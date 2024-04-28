package me.tatarka.webpush.relay

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.webpush.WebPush
import okio.ByteString
import okio.Source
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Constructs and instance of [WebPushRelayKeyManager] that stores the auth secret in shared preferences.
 */
fun WebPushRelayKeyManager(context: Context): WebPushRelayKeyManager =
    WebPushRelayKeyManager(secretStore = SharedPreferencesWebPushRelaySecretStore(context))

/**
 * Manages keys used for decrypting web push messages.
 *
 * @param secretStore A place to store the randomly generated auth secret
 * @param keyAlias The key alias to used for storing the public/private key. The default is
 * 'me.tatarka.webpush.relay.KeyPair'
 * @param keyStoreFactory Returns a loaded [KeyStore] instance for storing the public/private key. It is recommended you
 * keep the default which stores in the 'AndroidKeyStore'
 */
class WebPushRelayKeyManager(
    private val secretStore: WebPushRelaySecretStore,
    private val keyAlias: String = "me.tatarka.webpush.relay.KeyPair",
    private val keyStoreFactory: () -> KeyStore = { KeyStore.getInstance("AndroidKeyStore").apply { load(null) } },
) {
    private val keyStore by lazy { keyStoreFactory() }

    /**
     * Generates a new auth secret and stores it in the [secretStore]. This should be called before you register to your
     * web push service. Note: This will **always** create a new secret which means registrations with the previous
     * secret can no longer be decrypted. Use [requireAuthSecret] instead if you want to obtain a previously stored
     * secret.
     */
    suspend fun createAndStoreAuthSecret(): ByteString {
        return WebPush.generateAuthSecret().also { secretStore.save(it) }
    }

    /**
     * Returns the currently stored auth secret, generating and storing a new one if it doesn't exist.
     */
    suspend fun requireAuthSecret(): ByteString {
        return secretStore.load() ?: createAndStoreAuthSecret()
    }

    /**
     * Returns the public key of a public/private key pair that is stored in the [keyStore] under [keyAlias], creating
     * it if it doesn't exist.
     * @throws java.security.KeyStoreException if the [keyStore] was not initialized or the key fails to generate.
     */
    suspend fun getOrCreatePublicKey(): ByteString {
        return withContext(Dispatchers.IO) {
            val publicKey = keyStore.getCertificate(keyAlias)?.publicKey as? ECPublicKey
            if (publicKey != null) {
                return@withContext WebPush.encodePublicKey(publicKey)
            }
            WebPush.encodePublicKey(generateKeyPair().public as ECPublicKey)
        }
    }

    /**
     * Deletes the current public/private key pair in the [keyStore] stored under [keyAlias].
     * @throws java.security.KeyStoreException if the [keyStore] has not been initialized or the entry cannot be
     * removed.
     */
    suspend fun deleteKeyPair() {
        withContext(Dispatchers.IO) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    /**
     * Decrypts the given [WebPush] using the stored key and auth secret.
     *
     * @throws GeneralSecurityException or one of its subclasses if decryption fails.
     */
    suspend fun decrypt(webPush: WebPush): Source {
        val authSecret = secretStore.load() ?: throw GeneralSecurityException("Failed to decrypt: no auth secret saved")
        return withContext(Dispatchers.IO) {
            val publicKey = keyStore.getCertificate(keyAlias)?.publicKey
                ?: throw GeneralSecurityException("Failed to decrypt: no key pair saved")
            val privateKey = keyStore.getKey(keyAlias, null) as PrivateKey
            webPush.decrypt(
                authSecret = authSecret,
                keys = KeyPair(publicKey, privateKey)
            )
        }
    }

    private fun generateKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            .apply {
                initialize(
                    KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_AGREE_KEY)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .build()
                )
            }.generateKeyPair()
    }
}