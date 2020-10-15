package com.android.grafika.record.camera.ui

import android.media.AudioRecord
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import com.android.grafika.core.PermissionHelper
import com.android.grafika.record.audio.AudioMediaRecorderConfig
import com.android.grafika.record.audio.AudioRecorder
import java.io.File


class AudioCaptureActivity : AppCompatActivity() {

    private val audioRecorder = AudioRecorder()
    private val record by lazy { findViewById<ToggleButton>(R.id.record) }
    private val outputFile by lazy { File(filesDir, "audio-recording.mp4") }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.activity_capture_audio)
        audioRecorder
                .config {
                    AudioMediaRecorderConfig()
                            .outputFile { outputFile }
                }
                .onRecordingStarted { Log.i("grafika-audio", "onCreate: recordingStarted") }
                .onRecordingStopped { Log.i("grafika-audio", "onCreate: recordingStopped") }
        record.setOnCheckedChangeListener { _, isChecked ->
            if (audioRecorder.isRecording.not()) {
                audioRecorder.startRecording(this)
            } else {
                audioRecorder.stopRecording()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasAudioPermission(this).not()) {
            PermissionHelper.requestAudioPermission(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (PermissionHelper.hasAudioPermission(this).not()) {
            Toast.makeText(this,
                    "Audio permission is needed to run this application", Toast.LENGTH_LONG).show()
            PermissionHelper.launchPermissionSettings(this)
            finish()
        }
    }
}