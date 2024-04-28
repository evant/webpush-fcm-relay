package me.tatarka.webpush.relay

import assertk.Assert
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.ktor.server.application.Application

class TestFirebaseMessagingService : FirebaseMessagingService {
    private val sentNotifications = mutableListOf<SentNotification>()

    fun notification(): SentNotification? = sentNotifications.removeFirstOrNull()

    override suspend fun sendDataNotification(
        projectId: String,
        token: String,
        ttl: Long,
        data: Map<String, String>
    ): String {
        sentNotifications.add(SentNotification(projectId = projectId, token = token, ttl = ttl, data = data))
        return "messageId"
    }

}

class SentNotification(val projectId: String, val token: String, val ttl: Long, val data: Map<String, String>) {
    override fun toString(): String {
        return buildString {
            append(
                """
                projectId: $projectId
                token: $token
                ttl: $ttl
                
                """.trimIndent()
            )
            for ((key, value) in data) {
                append("$key=$value\n")
            }
        }
    }
}

fun Application.testFirebaseMessagingModule() {
    FirebaseMessagingService.Instance = TestFirebaseMessagingService()
}

fun Assert<SentNotification>.hasProjectId(projectId: String) = prop(SentNotification::projectId).isEqualTo(projectId)
fun Assert<SentNotification>.hasToken(token: String) = prop(SentNotification::token).isEqualTo(token)
fun Assert<SentNotification>.havingData() = prop(SentNotification::data)