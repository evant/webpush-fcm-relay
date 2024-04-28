package me.tatarka.webpush.relay.fcm.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.RemoteMessage
import me.tatarka.webpush.relay.WebPushRelayService
import okio.ByteString
import okio.Source
import okio.buffer

class DemoWebPushService : WebPushRelayService() {

    private lateinit var apiService: ApiService
    private lateinit var nm: NotificationManagerCompat

    override fun onCreate() {
        super.onCreate()
        apiService = ApiService.getInstance(this)
        nm = NotificationManagerCompat.from(this)
        nm.createNotificationChannel(
            NotificationChannel(
                "push",
                "Server Pushes",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    override fun onNewToken(token: String) {
        Log.d("DemoWebPushService", "firebase token: $token")
        super.onNewToken(token)
    }

    override suspend fun register(path: String, publicKey: ByteString, authSecret: ByteString) {
        apiService.register(
            // this would be the endpoint you are hosing your push relay service at
            endpoint = Uri.parse("http://10.0.2.2:8080").buildUpon()
                .appendEncodedPath(path)
                .build().toString(),
            publicKey = publicKey.base64Url(),
            authSecret = authSecret.base64Url(),
        )
    }

    override suspend fun onWebPushReceived(body: Source) {
        val contents = body.buffer().readUtf8()

        val notification = NotificationCompat.Builder(this, "push")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentText(contents)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            nm.notify(0, notification)
        }
    }

    override suspend fun onOtherMessageReceived(message: RemoteMessage) {
        Log.d("DemoWebPushService", "other message: ${message.messageId}")
    }
}