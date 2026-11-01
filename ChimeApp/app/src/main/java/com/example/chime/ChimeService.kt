package com.example.chime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class ChimeService : Service() {
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val finishRunnable = Runnable { finish() }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNow()
        stopPlayback()
        play(intent?.getBooleanExtra(EXTRA_HOUR, true) ?: true)
        return START_NOT_STICKY
    }

    private fun startForegroundNow() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("chime", "Chime playback", NotificationManager.IMPORTANCE_LOW)
        )
        val n: Notification = NotificationCompat.Builder(this, "chime")
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("Chime")
            .setContentText("Playing chime…")
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }

    private fun play(isHour: Boolean) {
        val p = Prefs(this)
        val attrs = AudioAttributes.Builder()
            .setUsage(if (p.alarmStream) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(attrs)
            var ok = false
            val custom = if (isHour) p.hourUri else p.halfUri
            if (custom != null) {
                try { mp.setDataSource(this, Uri.parse(custom)); mp.prepare(); ok = true }
                catch (e: Exception) { mp.reset(); mp.setAudioAttributes(attrs) }
            }
            if (!ok) {
                val afd = resources.openRawResourceFd(if (isHour) R.raw.chime_hour else R.raw.chime_half)
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close(); mp.prepare()
            }
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs).build()
            am.requestAudioFocus(focus!!)
            val v = p.volume / 100f
            mp.setVolume(v, v)
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ -> finish(); true }
            mp.start()
            player = mp
            handler.postDelayed(finishRunnable, p.maxSec * 1000L)
        } catch (e: Exception) {
            finish()
        }
    }

    private fun stopPlayback() {
        handler.removeCallbacks(finishRunnable)
        player?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player = null
    }

    private fun finish() {
        stopPlayback()
        focus?.let { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) }
        focus = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { stopPlayback(); super.onDestroy() }

    companion object {
        const val EXTRA_HOUR = "isHour"
        fun start(ctx: Context, isHour: Boolean) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, ChimeService::class.java).putExtra(EXTRA_HOUR, isHour)
            )
        }
    }
}
