package de.eberhardt.unlockcapture.capture

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import de.eberhardt.unlockcapture.MainActivity
import de.eberhardt.unlockcapture.R
import de.eberhardt.unlockcapture.settings.CaptureMode
import de.eberhardt.unlockcapture.settings.CaptureReason
import de.eberhardt.unlockcapture.settings.SettingsRepository
import de.eberhardt.unlockcapture.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.Executors

class CaptureForegroundService : Service(), LifecycleOwner {
    companion object {
        const val CHANNEL_ID = "capture"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_REASON = "reason"
        const val ACTION_CAPTURE_STATUS = "de.eberhardt.unlockcapture.CAPTURE_STATUS"
        const val EXTRA_STATE = "state"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_ERROR = "error"
        const val STATE_STARTED = "STARTED"
        const val STATE_FINISHED = "FINISHED"
        const val ERROR_CAMERA_PERMISSION_MISSING = "camera_permission_missing"
        const val ERROR_VIDEO_FINALIZE_PREFIX = "video_finalize:"
        @Volatile private var running = false
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var statusBroadcaster: CaptureStatusBroadcaster
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        AppLog.i("Service", "onCreate()")
        statusBroadcaster = CaptureStatusBroadcaster(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        startForeground(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLog.i("Service", "onStartCommand(startId=$startId flags=$flags) action=${intent?.action} extras=${intent?.extras?.keySet()?.joinToString(",") ?: "-"}")
        if (running) {
            AppLog.w("Service", "Already running -> stopSelf(startId=$startId)")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        running = true
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        val reason = runCatching {
            CaptureReason.valueOf(intent?.getStringExtra(EXTRA_REASON) ?: CaptureReason.MANUAL_TEST.name)
        }.getOrDefault(CaptureReason.MANUAL_TEST)
        AppLog.i("Service", "Capture reason=$reason")

        scope.launch {
            try {
                if (ContextCompat.checkSelfPermission(this@CaptureForegroundService, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    AppLog.w("Service", "CAMERA permission missing -> abort")
                    statusBroadcaster.finished(reason, success = false, error = ERROR_CAMERA_PERMISSION_MISSING)
                    return@launch
                }
                val mode = SettingsRepository(this@CaptureForegroundService).captureMode.first()
                AppLog.i("Service", "Mode=$mode")
                statusBroadcaster.started(reason)
                when (mode) {
                    CaptureMode.PHOTO -> takePhoto(reason)
                    CaptureMode.VIDEO_4_SECONDS -> recordVideo(reason)
                }
            } finally {
                AppLog.i("Service", "Finished -> stopSelf(startId=$startId)")
                running = false
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle(getString(R.string.fg_notification_title))
            .setContentText(getString(R.string.fg_notification_text))
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private suspend fun takePhoto(reason: CaptureReason) {
        AppLog.i("Capture", "takePhoto(reason=$reason)")
        val provider = ProcessCameraProvider.getInstance(this).get()
        val imageCapture = ImageCapture.Builder().build()
        provider.unbindAll()
        provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture)
        delay(700)

        val name = createMediaName(reason)
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            // On Android 10+, specify the folder
            if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UnlockCapture")
            }
        }

        val options = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()
        AppLog.i("Capture", "Photo output name=$name")

        imageCapture.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                AppLog.e("Capture", "Photo error: ${exception.message}", exception)
                statusBroadcaster.finished(reason, success = false, error = exception.message ?: exception.javaClass.simpleName)
                stopSelf()
            }
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                AppLog.i("Capture", "Photo saved: uri=${outputFileResults.savedUri}")
                outputFileResults.savedUri?.let { uri ->
                    scope.launch(Dispatchers.IO) {
                        CaptureIntegrityRecorder.record(this@CaptureForegroundService, uri, "photo")
                    }
                }
                statusBroadcaster.finished(reason, success = true)
                stopSelf()
            }
        })
        delay(2500)
        provider.unbindAll()
    }

    private fun createMediaName(reason: CaptureReason): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH__mm__ss", java.util.Locale.US)
        val timeStamp = sdf.format(Date())
        val name = "unlock_${timeStamp}_${reason.name.lowercase()}"
        return name
    }

    private suspend fun recordVideo(reason: CaptureReason) {
        AppLog.i("Capture", "recordVideo(reason=$reason)")
        try {
            val provider = ProcessCameraProvider.getInstance(this).get()
            val recorder = Recorder.Builder().build()
            val videoCapture = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, videoCapture)
            delay(700)

            val durationSeconds = SettingsRepository(this).videoDurationSeconds.first()
            AppLog.i("Capture", "Video durationSeconds=$durationSeconds")

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, createMediaName(reason) + ".mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/UnlockCapture")
            }
            AppLog.i("Capture", "Video values name=${values.getAsString(MediaStore.Video.Media.DISPLAY_NAME)}")

            val options = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(values)
                .build()

            val recording: Recording = videoCapture.output
                .prepareRecording(this, options)
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        AppLog.i("Capture", "Video finalize error=${event.error} cause=${event.cause}")
                        if (event.error == VideoRecordEvent.Finalize.ERROR_NONE) {
                            val uri = event.outputResults.outputUri
                            scope.launch(Dispatchers.IO) {
                                CaptureIntegrityRecorder.record(this@CaptureForegroundService, uri, "video")
                            }
                            statusBroadcaster.finished(reason, success = true)
                        } else {
                            statusBroadcaster.finished(
                                reason,
                                success = false,
                                error = ERROR_VIDEO_FINALIZE_PREFIX + event.error
                            )
                        }
                        stopSelf()
                    }
                }

            delay(durationSeconds * 1000L)
            AppLog.i("Capture", "Stopping video recording")
            recording.stop()
            delay(1000)
            provider.unbindAll()
        } catch (t: Throwable) {
            AppLog.e("Capture", "Video recording failed; falling back to photo. ${t.message}", t)
            // If photo fallback succeeds, it will send FINISHED itself.
            takePhoto(reason)
        }
    }

    override fun onDestroy() {
        running = false
        AppLog.i("Service", "onDestroy()")
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraExecutor.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
