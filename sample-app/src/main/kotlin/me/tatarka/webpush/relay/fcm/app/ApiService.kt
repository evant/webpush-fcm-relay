package me.tatarka.webpush.relay.fcm.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.MainThread

class ApiService(private val sharedPreferences: SharedPreferences) {

    suspend fun register(
        endpoint: String,
        publicKey: String,
        authSecret: String,
    ) {
        // in a real application this would call your web application to register receiving pushes at the given endpoint.
        Log.d("ApiService", "registered: $endpoint")
        sharedPreferences.edit()
            .putString("endpoint", endpoint)
            .putString("publicKey", publicKey)
            .putString("authSecret", authSecret)
            .apply()
    }

    companion object {
        private var instance: ApiService? = null

        @MainThread
        fun getInstance(context: Context): ApiService {
            return instance ?: ApiService(
                context.getSharedPreferences("pref", Context.MODE_PRIVATE)
            ).also {
                instance = it
            }
        }
    }
}