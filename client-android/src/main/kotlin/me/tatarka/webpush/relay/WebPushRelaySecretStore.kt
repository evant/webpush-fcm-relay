package me.tatarka.webpush.relay

import android.content.Context
import android.content.SharedPreferences
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

/**
 * An interface for saving and storing the auth secret used for webpush encryption.
 */
interface WebPushRelaySecretStore {
    suspend fun save(secret: ByteString)

    suspend fun load(): ByteString?
}

/**
 * An implementation of [WebPushRelaySecretStore] that saves the auth secret in shared preferences.
 * @param key The key to store the entry under. Defaults to 'me.tatarka.webpush.relay.AuthSecret'
 * @param preferencesFactory Returns a [SharedPreferences] instance to save and load the key from.
 */
class SharedPreferencesWebPushRelaySecretStore(
    private val key: String = "me.tatarka.webpush.relay.AuthSecret",
    private val preferencesFactory: () -> SharedPreferences
) : WebPushRelaySecretStore {

    /**
     * An implementation of [WebPushRelaySecretStore] that stores the auth secret under shared preferences with the
     * given name.
     *
     * @param context The app context
     * @param preferencesName The name of the shared preferences to store the key under. Defaults to
     * 'me.tatarka.webpush.relay.Preferences'
     */
    constructor(
        context: Context,
        preferencesName: String = "me.tatarka.webpush.relay.Preferences"
    ) : this(
        preferencesFactory = {
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        }
    )

    private val preferences by lazy { preferencesFactory() }

    override suspend fun save(secret: ByteString) {
        preferences.edit()
            .putString(key, secret.base64())
            .apply()
    }

    override suspend fun load(): ByteString? {
        return preferences.getString(key, null)?.decodeBase64()
    }
}