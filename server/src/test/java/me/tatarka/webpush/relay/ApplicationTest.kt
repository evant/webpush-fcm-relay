package me.tatarka.webpush.relay

import assertk.Assert
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.util.toMap
import me.tatarka.webpush.WebPush
import okio.Buffer
import okio.buffer
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test

class ApplicationTest {

    private val messagingService: TestFirebaseMessagingService by lazy {
        FirebaseMessagingService.Instance as TestFirebaseMessagingService
    }

    @Test
    fun `returns a 400 when TTL is missing`() = testApplication {
        val response = client.post("/wpush/projectId/token")

        assertThat(response).apply {
            hasStatus(HttpStatusCode.BadRequest)
            hasBodyText("Missing or malformed 'TTL' header")
        }
    }

    @Test
    fun `returns a 400 when Content-Encoding is missing`() = testApplication {
        val response = client.post("/wpush/projectId/token") {
            headers.append("TTL", "0")
        }

        assertThat(response).apply {
            hasStatus(HttpStatusCode.BadRequest)
            hasBodyText("Missing 'Content-Encoding' header")
        }
    }

    @Test
    fun `returns a 400 when a body is missing`() = testApplication {
        val response = client.post("/wpush/projectId/token") {
            headers.append("TTL", "0")
            headers.append(HttpHeaders.ContentEncoding, WebPush.ContentEncoding.aes128gcm.toString())
        }

        assertThat(response).apply {
            hasStatus(HttpStatusCode.BadRequest)
            hasBodyText("Missing body")
        }
    }

    @Test
    fun `returns a 400 when Content-Encoding is aesgcm and extra headers are missing`() = testApplication {
        val response = client.post("/wpush/projectId/token") {
            headers.append("TTL", "0")
            headers.append(HttpHeaders.ContentEncoding, WebPush.ContentEncoding.aesgcm.toString())
        }

        assertThat(response).apply {
            hasStatus(HttpStatusCode.BadRequest)
            hasBodyText("Missing 'Encryption' header")
        }
    }

    @Test
    fun `sends encrypted message to push service aes128gcm`() = testApplication {
        val serverKeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val clientKeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val clientPublicKey = WebPush.encodePublicKey(clientKeyPair.public as ECPublicKey)

        val webPush = WebPush.encrypt(
            authSecret = WebPush.generateAuthSecret(),
            keys = serverKeyPair,
            clientPublicKey = clientPublicKey,
            body = Buffer().writeUtf8("secret body"),
            encoding = WebPush.ContentEncoding.aes128gcm,
        )
        val encryptedBody = webPush.encryptedBody.buffer().readByteString()

        val response = client.post("/wpush/projectId/token") {
            headers.append("TTL", "0")
            for ((name, value) in webPush.headers) {
                headers.append(name, value)
            }
            setBody(encryptedBody.toByteArray())
        }

        assertThat(response).apply {
            hasStatus(HttpStatusCode.Created)
            havingHeaders().contains(HttpHeaders.Location to listOf("/m/messageId"))
        }
        assertThat(messagingService.notification()).isNotNull().apply {
            hasProjectId("projectId")
            hasToken("token")
            havingData().containsOnly(
                "con" to WebPush.ContentEncoding.aes128gcm.toString(),
                "body" to encryptedBody.base64(),
            )
        }
    }

    @Test
    fun `sends encrypted message to push service aesgcm`() = testApplication {
        val serverKeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val clientKeyPair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val clientPublicKey = WebPush.encodePublicKey(clientKeyPair.public as ECPublicKey)

        val webPush = WebPush.encrypt(
            authSecret = WebPush.generateAuthSecret(),
            keys = serverKeyPair,
            clientPublicKey = clientPublicKey,
            body = Buffer().writeUtf8("secret body"),
            encoding = WebPush.ContentEncoding.aesgcm,
        )
        val webPushHeaders = webPush.headers.toMap()
        val encryptedBody = webPush.encryptedBody.buffer().readByteString()

        val response = client.post("/wpush/projectId/token") {
            headers.append("TTL", "0")
            for ((name, value) in webPush.headers) {
                headers.append(name, value)
            }
            setBody(encryptedBody.toByteArray())
        }

        assertThat(response).apply {
            hasStatus(HttpStatusCode.Created)
            havingHeaders().contains(HttpHeaders.Location to listOf("/m/messageId"))
        }

        assertThat(messagingService.notification()).isNotNull().apply {
            hasProjectId("projectId")
            hasToken("token")
            havingData().containsOnly(
                "con" to WebPush.ContentEncoding.aesgcm.toString(),
                "body" to encryptedBody.base64(),
                "cryptokey" to webPushHeaders.getValue(WebPush.HeaderCryptoKey),
                "enc" to webPushHeaders.getValue(WebPush.HeaderEncryption),
            )
        }
    }
}

fun Assert<HttpResponse>.hasStatus(status: HttpStatusCode) = prop(HttpResponse::status).isEqualTo(status)
suspend fun Assert<HttpResponse>.hasBodyText(text: String) = transform(name = "body") { actual ->
    actual.bodyAsText()
}.isEqualTo(text)

fun Assert<HttpResponse>.havingHeaders() = prop(name = "headers") { it.headers.toMap() }
