package com.android.grafika.record.camera.ui

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.android.grafika.record.camera.view.GLCameraView
import java.io.File

class CameraXCaptureActivity : AppCompatActivity() {

    private val cameraView by lazy { findViewById<GLCameraView>(R.id.camera_view) }
    private val flip by lazy { findViewById<Button>(R.id.flip) }
    private val record by lazy { findViewById<Button>(R.id.record) }
    private val outputFile by lazy { File(filesDir, "camera-test.mp4") }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_x_capture)
        cameraView.bindLifecycleOwner(this)
        cameraView.onRecordingStarted = {
            record.isEnabled = true
            Log.d(CameraXCaptureActivity::class.java.simpleName, "recording STARTED!")
        }
        cameraView.onRecordingPaused = {
            record.isEnabled = true
            Log.d(CameraXCaptureActivity::class.java.simpleName, "recording PAUSED!")
        }
        cameraView.onRecordingResumed = {
            record.isEnabled = true
            Log.d(CameraXCaptureActivity::class.java.simpleName, "recording RESUMED!")
        }
        cameraView.onRecordingStopped = {
            Log.d(CameraXCaptureActivity::class.java.simpleName, "recording STOPPED!")
            record.isEnabled = true
        }
        cameraView.onRecordingFailed = {
            Log.e(CameraXCaptureActivity::class.java.simpleName, "recording FAILED!", it)
            record.isEnabled = true
        }
        flip.setOnClickListener {
            cameraView.flipCameras(this)
        }
        record.setOnClickListener {
            cameraView.toggleRecording(outputFile)
            record.isEnabled = false
        }
    }

}