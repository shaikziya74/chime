package com.example.chime

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private var pickingHour = true

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {}
            if (pickingHour) prefs.hourUri = uri.toString() else prefs.halfUri = uri.toString()
            refresh()
        }
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)

        sw(R.id.swEnabled) { prefs.enabled = it; Scheduler.sync(this); refresh() }
        sw(R.id.swHour) { prefs.hourly = it }
        sw(R.id.swHalf) { prefs.half = it }
        sw(R.id.swAlarm) { prefs.alarmStream = it }
        sw(R.id.swQuiet) { prefs.quiet = it; refresh() }

        btn(R.id.btnExact) {
            if (Build.VERSION.SDK_INT >= 31)
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
        btn(R.id.btnHourPick) { pickingHour = true; picker.launch(arrayOf("audio/*")) }
        btn(R.id.btnHalfPick) { pickingHour = false; picker.launch(arrayOf("audio/*")) }
        btn(R.id.btnHourReset) { prefs.hourUri = null; refresh() }
        btn(R.id.btnHalfReset) { prefs.halfUri = null; refresh() }
        btn(R.id.btnHourTest) { ChimeService.start(this, true) }
        btn(R.id.btnHalfTest) { ChimeService.start(this, false) }

        btn(R.id.btnQStart) { pickTime(prefs.quietStart) { prefs.quietStart = it; refresh() } }
        btn(R.id.btnQEnd) { pickTime(prefs.quietEnd) { prefs.quietEnd = it; refresh() } }

        findViewById<SeekBar>(R.id.sbVolume).onChange { prefs.volume = it; refresh(false) }
        findViewById<SeekBar>(R.id.sbMax).onChange { prefs.maxSec = it + 3; refresh(false) }
    }

    override fun onResume() {
        super.onResume()
        Scheduler.sync(this)
        refresh()
    }

    private fun sw(id: Int, cb: (Boolean) -> Unit) =
        findViewById<MaterialSwitch>(id).setOnCheckedChangeListener { b, v -> if (b.isPressed) cb(v) }

    private fun btn(id: Int, cb: () -> Unit) = findViewById<Button>(id).setOnClickListener { cb() }

    private fun SeekBar.onChange(cb: (Int) -> Unit) {
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser) cb(p) }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
    }

    private fun pickTime(current: Int, done: (Int) -> Unit) {
        TimePickerDialog(this, { _, h, m -> done(h * 60 + m) },
            current / 60, current % 60, DateFormat.is24HourFormat(this)).show()
    }

    private fun fmt(min: Int): String {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, min / 60); set(Calendar.MINUTE, min % 60) }
        return DateFormat.getTimeFormat(this).format(c.time)
    }

    private fun nameOf(uriStr: String?): String {
        if (uriStr == null) return "Built-in default"
        return try {
            contentResolver.query(Uri.parse(uriStr), null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            } ?: "Custom file"
        } catch (e: Exception) { "Custom file (unavailable, default will play)" }
    }

    private fun refresh(full: Boolean = true) {
        findViewById<TextView>(R.id.tvVolume).text = "Volume: ${prefs.volume}%"
        findViewById<TextView>(R.id.tvMax).text = "Max chime length: ${prefs.maxSec} s"
        if (!full) return
        findViewById<MaterialSwitch>(R.id.swEnabled).isChecked = prefs.enabled
        findViewById<MaterialSwitch>(R.id.swHour).isChecked = prefs.hourly
        findViewById<MaterialSwitch>(R.id.swHalf).isChecked = prefs.half
        findViewById<MaterialSwitch>(R.id.swAlarm).isChecked = prefs.alarmStream
        findViewById<MaterialSwitch>(R.id.swQuiet).isChecked = prefs.quiet
        findViewById<TextView>(R.id.tvHour).text = nameOf(prefs.hourUri)
        findViewById<TextView>(R.id.tvHalf).text = nameOf(prefs.halfUri)
        findViewById<SeekBar>(R.id.sbVolume).progress = prefs.volume
        findViewById<SeekBar>(R.id.sbMax).progress = prefs.maxSec - 3
        findViewById<Button>(R.id.btnQStart).apply { text = "From ${fmt(prefs.quietStart)}"; isEnabled = prefs.quiet }
        findViewById<Button>(R.id.btnQEnd).apply { text = "Until ${fmt(prefs.quietEnd)}"; isEnabled = prefs.quiet }
        findViewById<Button>(R.id.btnExact).visibility =
            if (prefs.enabled && !Scheduler.canExact(this)) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvNext).text =
            if (prefs.enabled) "Next slot: " + SimpleDateFormat("EEE h:mm a", Locale.getDefault()).format(Date(Scheduler.nextBoundary()))
            else "Chimes are off"
    }
}
