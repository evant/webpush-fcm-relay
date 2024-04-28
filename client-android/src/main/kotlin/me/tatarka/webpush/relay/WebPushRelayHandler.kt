package me.tatarka.webpush.relay

import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.messaging.RemoteMessage
import me.tatarka.webpush.WebPush
import okio.Buffer
import okio.ByteString.Companion.decodeBase64

/**
 * Helpers for handling web pushes, used by [WebPushRelayService].
 */
object WebPushRelay {

    /**
     * Determines if the given [RemoteMessage] is a web push from the relay server and can be decoded.
     */
    fun isWebPush(message: RemoteMessage): Boolean {
        return isWebPush(message.data["con"])
    }

    private fun isWebPush(e: String?): Boolean {
        return when (e) {
            WebPush.ContentEncoding.aesgcm.toString() -> true
            WebPush.ContentEncoding.aes128gcm.toString() -> true
            else -> false
        }
    }

    /**
     * Construct a valid path (without a leading /) for the web relay server from the given firebase token. This should
     * be appended to the domain the relay is hosted at and passed to your webpush registration service.
     *
     * @param token The firebase token, can be obtained with
     * [com.google.firebase.messaging.FirebaseMessaging.getToken()]
     */
    fun path(token: String): String {
        val projectId = requireNotNull(Firebase.app.options.projectId) { "projectId not set" }
        return "wpush/${projectId}/${token}"
    }

    /**
     * Decodes the given message into a [WebPush] when can then be decrypted. You should use [isWebPush] to ensure the
     * message is a valid webpush message before calling this.
     *
     * @throws WebPushDecodeFailedException if the message can't be decoded, likely because it has missing or malformed
     * fields.
     */
    fun decode(message: RemoteMessage): WebPush {
        val data = message.data
        val encoding = WebPush.ContentEncoding.of(
            data["con"] ?: throw WebPushDecodeFailedException("missing header: 'Content-Encoding'")
        )
        val headers = buildList {
            add(WebPush.HeaderContentEncoding to encoding.toString())
            if (encoding == WebPush.ContentEncoding.aesgcm) {
                add(
                    WebPush.HeaderEncryption to (data["enc"]
                        ?: throw WebPushDecodeFailedException("missing header: 'Encryption'"))
                )
                add(
                    WebPush.HeaderCryptoKey to (data["cryptokey"]
                        ?: throw WebPushDecodeFailedException("missing header: 'Crypto-Key'"))
                )
            }
        }
        val encryptedBody = data["body"]?.decodeBase64() ?: throw WebPushDecodeFailedException("missing body")
        return WebPush(
            headers = headers,
            encryptedBody = Buffer().write(encryptedBody),
        )
    }
}

class WebPushDecodeFailedException(message: String) : Exception(message)