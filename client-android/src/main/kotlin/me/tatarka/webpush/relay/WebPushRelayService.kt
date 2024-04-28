package me.tatarka.webpush.relay

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.tatarka.webpush.WebPushFormatException
import okio.ByteString
import okio.IOException
import okio.Source

/**
 * A [FirebaseMessagingService] that decodes webpush messages relayed by the relay server. This is a convenience for
 * handling webpush setup and receiving for you. Alternatively, you can implement [FirebaseMessagingService] and use
 * [WebPushRelay] and [WebPushRelayKeyManager] directly.
 */
abstract class WebPushRelayService : FirebaseMessagingService() {

    enum class ErrorReason {
        /**
         * The encryption key failed to generate. This means [register] will not be triggered.
         */
        KeyGenerationFailed,

        /**
         * The received web push failed to be decoded, either because it was malformed or unable to be decrypted.
         */
        WebPushDecodeFailed
    }

    private lateinit var scope: CoroutineScope
    private lateinit var keyManager: WebPushRelayKeyManager

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(Dispatchers.Main)
        keyManager = onCreateKeyManager()
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    final override fun onMessageReceived(message: RemoteMessage) {
        scope.launch {
            if (WebPushRelay.isWebPush(message)) {
                val body = try {
                    val webPush = WebPushRelay.decode(message)
                    keyManager.decrypt(webPush)
                } catch (e: IOException) {
                    onWebPushFailed(ErrorReason.WebPushDecodeFailed, e)
                    null
                } catch (e: WebPushFormatException) {
                    onWebPushFailed(ErrorReason.WebPushDecodeFailed, e)
                    null
                }
                if (body != null) {
                    onWebPushReceived(body)
                }
            } else {
                onOtherMessageReceived(message)
            }
        }
    }

    /**
     * Constructs the [WebPushRelayKeyManager] that's used for handling decryption. You can override
     * this to construct a custom instance.
     */
    @MainThread
    open fun onCreateKeyManager(): WebPushRelayKeyManager {
        return WebPushRelayKeyManager(this)
    }

    /**
     * Called when a new token is generated. This is called on initial launch and when the token changes. The default
     * implementation will generate the necessary encryption keys and then call [register] for you to register your app
     * with the server.
     */
    @WorkerThread
    @CallSuper
    override fun onNewToken(token: String) {
        scope.launch {
            val path = WebPushRelay.path(token)
            var publicKey: ByteString? = null
            var authSecret: ByteString? = null
            try {
                publicKey = keyManager.getOrCreatePublicKey()
                authSecret = keyManager.createAndStoreAuthSecret()
            } catch (e: IOException) {
                onWebPushFailed(ErrorReason.KeyGenerationFailed, e)
            }
            if (authSecret != null && publicKey != null) {
                register(path, publicKey, authSecret)
            }
        }
    }

    /**
     * Register with the application server to send push messages to the relay. This is called when Firebase generates
     * a new token.
     *
     * While this signals that firebase is ready, you _may_ wait until another time to register your application, for
     * example if the user hasn't accepted receiving push notifications. In that case you can reference the variables
     * passed here with [WebPushRelay.path], [WebPushRelayKeyManager.getOrCreatePublicKey], and
     * [WebPushRelayKeyManager.requireAuthSecret].
     *
     * @param path The path portion of the relay url, use this to build what endpoint the application
     * server should send pushes to
     * @param publicKey The client's public key
     * @param authSecret The client's auth secret
     */
    abstract suspend fun register(path: String, publicKey: ByteString, authSecret: ByteString)

    /**
     * Called when Firebase receives a push from the relay server. Because decryption has to happen before rendering,
     * this will be called regardless if your app is in the background or foreground and you are responsible for
     * decoding and rendering a notification yourself if you so choose.
     *
     * @param body The decrypted body of the push message.
     */
    abstract suspend fun onWebPushReceived(body: Source)

    /**
     * Called when Firebase receives a push that is not recognized as originating from the relay
     * server. This is equivalent to [FirebaseMessagingService.onMessageReceived].
     * @param message Remote message that has been received.
     *
     * @see [FirebaseMessagingService.onMessageReceived]
     */
    open suspend fun onOtherMessageReceived(message: RemoteMessage) {
    }

    /**
     * Called when there was an error setting up or receiving a web push. By default, logs error in debug builds.
     *
     * @param reason The reason the error was triggered
     * @param error The error that was thrown
     */
    open suspend fun onWebPushFailed(reason: ErrorReason, error: Throwable) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.e("WebPushRelayService", error.message, error)
        }
    }
}