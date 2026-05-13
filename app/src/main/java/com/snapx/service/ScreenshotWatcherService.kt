package com.snapx.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.snapx.R
import com.snapx.overlay.OverlayManager
import com.snapx.snap.SnapEngine
import kotlinx.coroutines.*

class ScreenshotWatcherService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var overlayManager: OverlayManager
    private var contentObserver: ContentObserver? = null

    override fun onCreate() {
        super.onCreate()
        overlayManager = OverlayManager(this)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildForegroundNotification())
        registerScreenshotObserver()
    }

    private fun registerScreenshotObserver() {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                uri ?: return
                scope.launch { handleNewMedia(uri) }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
        )
        contentObserver = observer
    }

    private suspend fun handleNewMedia(uri: Uri) {
        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )
        val id = try { ContentUris.parseId(uri) } catch (e: Exception) { return }
        val cursor = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media._ID} = ?",
            arrayOf(id.toString()),
            null
        ) ?: return

        val path: String
        val dateAdded: Long
        cursor.use {
            if (!it.moveToFirst()) return
            path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
            dateAdded = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
        }

        val ageSeconds = System.currentTimeMillis() / 1000 - dateAdded
        if (ageSeconds > 3) return
        if (!path.contains("screenshot", ignoreCase = true)) return

        val displayBitmap = loadDisplayBitmap(uri) ?: return
        val windowBounds = SnapWindowService.instance?.getVisibleWindowBounds() ?: emptyList()
        val engine = SnapEngine(displayBitmap.width, displayBitmap.height)
        val staticZones = engine.computeStaticZones(windowBounds)

        withContext(Dispatchers.Main) {
            if (!overlayManager.isShowing()) {
                overlayManager.show(displayBitmap, uri, engine, staticZones)
            }
        }

        val edgeZones = engine.computeEdgeZones(displayBitmap)
        withContext(Dispatchers.Main) {
            overlayManager.updateEdgeZones(edgeZones)
        }
    }

    private fun loadDisplayBitmap(uri: Uri): Bitmap? {
        val metrics = resources.displayMetrics
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        opts.inSampleSize = calculateSampleSize(opts, metrics.widthPixels, metrics.heightPixels)
        opts.inJustDecodeBounds = false
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun calculateSampleSize(opts: BitmapFactory.Options, maxW: Int, maxH: Int): Int {
        var size = 1
        while (opts.outWidth / size > maxW * 2 || opts.outHeight / size > maxH * 2) size *= 2
        return size
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CropSaveService.CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun buildForegroundNotification() =
        NotificationCompat.Builder(this, CropSaveService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.notif_watcher_title))
            .setContentText(getString(R.string.notif_watcher_text))
            .setOngoing(true)
            .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        contentObserver?.let { contentResolver.unregisterContentObserver(it) }
        overlayManager.dismiss()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, ScreenshotWatcherService::class.java)
            )
        }
    }
}
