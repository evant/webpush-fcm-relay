package me.tatarka.webpush.relay

import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.guava.await
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.inputStream
import kotlin.io.path.notExists

private const val ScopeMessagesCreate = "cloudmessaging.messages.create"

data class FirebaseConfig(val auth: FirebaseAuth) {
    constructor(config: ApplicationConfig) : this(
        auth = FirebaseAuth(config.config("auth"))
    )
}

data class FirebaseAuth(val credentialsDir: Path? = null) {
    constructor(config: ApplicationConfig) : this(
        credentialsDir = config.propertyOrNull("credentialsDir")?.getString()?.let { Paths.get(it) }
    )
}

interface MessagingProvider {
    fun messaging(projectId: String): FirebaseMessaging
}

fun MessagingProvider(config: FirebaseConfig): MessagingProvider {
    val logger = LoggerFactory.getLogger(MessagingProvider::class.java)
    val credentialsDir = config.auth.credentialsDir
    return if (credentialsDir != null) {
        val credentials = mutableListOf<ServiceAccountCredentials>()
        if (credentialsDir.notExists()) {
            throw IllegalArgumentException("$credentialsDir does not exist")
        }
        credentialsDir.forEachDirectoryEntry(glob = "*.json") { credentialFile ->
            credentials.add(credentialFile.inputStream().buffered().use {
                ServiceAccountCredentials.fromStream(it)
            })
        }
        val provider = ServiceAccountsMessagingProvider(credentials)
        logger.debug("Registered projectIds: ${credentials.joinToString(", ") { it.projectId }}")
        provider
    } else {
        val provider = ApplicationDefaultMessagingProvider()
        logger.debug("Using application default firebase credentials")
        provider
    }
}

private class ApplicationDefaultMessagingProvider : MessagingProvider {

    private val app = FirebaseApp.initializeApp(
        FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.getApplicationDefault().createScoped(ScopeMessagesCreate))
            .build()
    )

    override fun messaging(projectId: String): FirebaseMessaging {
        return FirebaseMessaging.getInstance(app)
    }
}

private class ServiceAccountsMessagingProvider(
    credentials: List<ServiceAccountCredentials>
) : MessagingProvider {

    private val apps = credentials.associate { credential ->
        credential.projectId to FirebaseApp.initializeApp(
            FirebaseOptions.builder()
                .setCredentials(credential.createScoped(ScopeMessagesCreate))
                .build(),
            credential.projectId,
        )
    }

    override fun messaging(projectId: String): FirebaseMessaging {
        val app = requireNotNull(apps[projectId]) {
            "projectId: $projectId not registered"
        }
        return FirebaseMessaging.getInstance(app)
    }
}

interface FirebaseMessagingService {
    suspend fun sendDataNotification(
        projectId: String,
        token: String,
        ttl: Long,
        data: Map<String, String>
    ): String

    companion object {
        lateinit var Instance: FirebaseMessagingService
    }
}

fun FirebaseMessagingService(messagingProvider: MessagingProvider): FirebaseMessagingService {
    return object : FirebaseMessagingService {
        @Suppress("UNCHECKED_CAST")
        override suspend fun sendDataNotification(
            projectId: String,
            token: String,
            ttl: Long,
            data: Map<String, String>
        ): String {
            val messaging = messagingProvider.messaging(projectId)
            val message = Message.builder()
                .setToken(token)
                .putAllData(data)
                .setAndroidConfig(
                    AndroidConfig.builder()
                        .setTtl(ttl)
                        .build()
                )
                .build()
            return (messaging.sendAsync(message) as ListenableFuture<String>).await()
        }
    }
}

fun Application.firebaseMessagingModule() {
    val firebaseConfig = FirebaseConfig(environment.config.config("firebase"))
    val provider = MessagingProvider(firebaseConfig)
    FirebaseMessagingService.Instance = FirebaseMessagingService(provider)
}
