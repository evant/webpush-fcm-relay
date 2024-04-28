package me.tatarka.webpush.relay

import com.google.api.client.http.HttpResponseException
import com.google.firebase.messaging.FirebaseMessagingException
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.util.getOrFail
import me.tatarka.webpush.WebPush
import me.tatarka.webpush.WebPushFormatException
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.slf4j.event.Level

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    install(CallLogging) {
        level = Level.DEBUG
    }
    val messaging = FirebaseMessagingService.Instance
    routing {
        post("/wpush/{projectId}/{token}") { relay(messaging) }
    }
}

suspend fun RoutingContext.relay(pushService: FirebaseMessagingService) {
    val projectId = call.pathParameters.getOrFail("projectId")
    val token = call.pathParameters.getOrFail("token")
    try {
        val ttl = call.request.headers["TTL"]?.toLongOrNull()
            ?: throw BadRequestException("Missing or malformed 'TTL' header")
        val contentEncoding = WebPush.ContentEncoding.of(
            call.request.headers[HttpHeaders.ContentEncoding]
                ?: throw BadRequestException("Missing 'Content-Encoding' header")
        )
        val body = call.receive<ByteArray>().toByteString()
        val data = buildPushData(contentEncoding, body, call.request.headers)
        if (body == ByteString.EMPTY) {
            throw BadRequestException("Missing body")
        }
        val response = pushService.sendDataNotification(projectId = projectId, token = token, ttl = ttl, data = data)
        val messageId = response.substringAfterLast("/")
        call.response.apply {
            status(HttpStatusCode.Created)
            header(HttpHeaders.Location, "/m/${messageId}")
        }
    } catch (e: WebPushFormatException) {
        call.respondText(
            status = HttpStatusCode.BadRequest,
            text = e.message!!,
        )
    } catch (e: BadRequestException) {
        call.respondText(
            status = HttpStatusCode.BadRequest,
            text = e.message!!,
        )
    } catch (e: FirebaseMessagingException) {
        val cause = e.cause
        if (cause is HttpResponseException) {
            // forward the response
            call.respondText(
                status = HttpStatusCode.fromValue(cause.statusCode),
                text = cause.message!!,
                contentType = ContentType.parse("application/json")
            )
        } else {
            throw e
        }
    }
}

fun buildPushData(
    encoding: WebPush.ContentEncoding,
    body: ByteString,
    headers: Headers
): Map<String, String> = buildMap {
    // the encrypted body
    put("body", body.base64())
    // which encoding we are using
    put("con", encoding.toString())
    // header values for aesgcm
    if (encoding == WebPush.ContentEncoding.aesgcm) {
        val encyption = headers[WebPush.HeaderEncryption]
            ?: throw BadRequestException("Missing 'Encryption' header")
        val cyptoKey = headers[WebPush.HeaderCryptoKey]
            ?: throw BadRequestException("Missing 'Crypto-Key' header")
        put("enc", encyption)
        put("cryptokey", cyptoKey)
    }
}