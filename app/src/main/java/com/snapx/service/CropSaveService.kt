package com.snapx.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.snapx.R
import kotlinx.coroutines.*

class CropSaveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("DEPRECATION")
    private inline fun <reified T : android.os.Parcelable> Intent.getParcelableCompat(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            getParcelableExtra(key, T::class.java)
        else
            getParcelableExtra<T>(key)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri  = intent?.getParcelableCompat<Uri>(EXTRA_URI)   ?: return START_NOT_STICKY
        val rect = intent?.getParcelableCompat<Rect>(EXTRA_RECT) ?: return START_NOT_STICKY

        startAsForeground()

        scope.launch {
            cropAndSave(uri, rect)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.notif_watcher_title))
            .setContentText("Saving screenshot…")
            .build()

        startForeground(NOTIF_CROP_ID, notification)
    }

    private suspend fun cropAndSave(originalUri: Uri, rect: Rect) {
        val original = contentResolver.openInputStream(originalUri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return

        val safeRect = Rect(
            rect.left.coerceIn(0, original.width - 1),
            rect.top.coerceIn(0, original.height - 1),
            rect.right.coerceIn(1, original.width),
            rect.bottom.coerceIn(1, original.height)
        )

        val cropped = Bitmap.createBitmap(
            original, safeRect.left, safeRect.top,
            safeRect.width(), safeRect.height()
        )
        original.recycle()

        val displayName = "Screenshot_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
        }

        val newUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return

        val saved = contentResolver.openOutputStream(newUri)?.use { out ->
            cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: false
        cropped.recycle()

        if (!saved) {
            contentResolver.delete(newUri, null, null)
            return
        }
        contentResolver.delete(originalUri, null, null)

        postSavedNotification(newUri, displayName)
    }

    private fun postSavedNotification(uri: Uri, displayName: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)

        val viewIntent = PendingIntent.getActivity(
            this, 0,
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val shareIntent = PendingIntent.getActivity(
            this, 1,
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }, getString(R.string.action_share)
            ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.notif_saved_title))
            .setContentText(displayName)
            .setAutoCancel(true)
            .setContentIntent(viewIntent)
            .addAction(0, getString(R.string.action_view), viewIntent)
            .addAction(0, getString(R.string.action_share), shareIntent)
            .build()

        nm.notify(NOTIF_SAVED_ID, notif)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URI = "uri"
        const val EXTRA_RECT = "rect"
        const val CHANNEL_ID = "snapx_main"
        const val NOTIF_SAVED_ID = 1002
        const val NOTIF_CROP_ID = 1003

        fun start(context: Context, uri: Uri, rect: Rect) {
            context.startService(
                Intent(context, CropSaveService::class.java).apply {
                    putExtra(EXTRA_URI, uri)
                    putExtra(EXTRA_RECT, rect)
                }
            )
        }
    }
}
