package me.tatarka.webpush.relay.fcm.app

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.tatarka.webpush.WebPush
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import okio.buffer
import okio.sink
import okio.source
import okio.use
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

class FakeServer(private val sharedPreferences: SharedPreferences) {
    private val scope = CoroutineScope(Dispatchers.IO)

    // pushes are normally sent by the server, but we can fake it here by calling the relay service directly.
    fun sendTestPush() {
        val registration = getPushRegistration()
        if (registration == null) {
            Log.w("FakeServer", "no push registration registered, was firebase correctly set up?")
            return
        }
        scope.launch {
            try {
                val webPush = WebPush.encrypt(
                    authSecret = registration.authSecret.decodeBase64()!!,
                    keys = getOrCreateServerKeyPair(),
                    clientPublicKey = registration.publicKey.decodeBase64()!!,
                    body = Buffer().writeUtf8("Test Push!")
                )

                val connection = URL(registration.endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.addRequestProperty("TTL", "0")
                for ((header, value) in webPush.headers) {
                    connection.addRequestProperty(header, value)
                }
                connection.connect()
                connection.outputStream.sink().buffer().use { out ->
                    out.writeAll(webPush.encryptedBody)
                }
                val response = connection.inputStream.source().buffer().readUtf8()
                Log.d("FakeServer", "${connection.responseCode}: ${connection.responseMessage}")
                Log.d("FakeServer", response)
                connection.disconnect()
            } catch (e: IOException) {
                Log.e("FakeServer", e.message, e)
            }
        }
    }

    private fun getPushRegistration(): PushRegistration? {
        val endpoint = sharedPreferences.getString("endpoint", null) ?: return null
        val publicKey = sharedPreferences.getString("publicKey", null) ?: return null
        val authSecret = sharedPreferences.getString("authSecret", null) ?: return null
        return PushRegistration(
            endpoint = endpoint,
            publicKey = publicKey,
            authSecret = authSecret,
        )
    }

    private fun getOrCreateServerKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            .apply {
                initialize(
                    KeyGenParameterSpec.Builder(ServerKeyAlias, KeyProperties.PURPOSE_AGREE_KEY)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .build()
                )
            }.generateKeyPair()
    }

    companion object {
        private const val ServerKeyAlias = "ServerKeyAlias"

        private var instance: FakeServer? = null

        @MainThread
        fun getInstance(context: Context): FakeServer {
            return instance ?: FakeServer(
                context.getSharedPreferences("pref", Context.MODE_PRIVATE)
            ).also {
                instance = it
            }
        }
    }

    private class PushRegistration(
        val endpoint: String,
        val publicKey: String,
        val authSecret: String
    )
}