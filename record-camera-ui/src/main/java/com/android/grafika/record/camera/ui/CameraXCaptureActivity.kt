package com.android.grafika.record.camera.ui

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.grafika.core.PermissionHelper
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
        cameraView.onRecordingStarted {
            record.isEnabled = true
            record.text = "Stop"
            Log.d(CameraXCaptureActivity::class.java.simpleName, "recording STARTED!")
        }
        cameraView.onRecordingPaused {
            record.isEnabled = true
            record.text = "Paused"
            Log.d(CameraXCaptureActivity::class.java.simpleName, "recording PAUSED!Start")
        }
        cameraView.onRecordingResumed {
            record.isEnabled = true
            record.text = "Stop"
            Log.d(CameraXCaptureActivity::class.java.simpleName, "recording RESUMED!")
        }
        cameraView.onRecordingStopped {
            Log.d(CameraXCaptureActivity::class.java.simpleName, "recording STOPPED!")
            record.text = "Rec"
            record.isEnabled = true
        }
        cameraView.onRecordingFailed {
            Log.e(CameraXCaptureActivity::class.java.simpleName, "recording FAILED!", it)
            record.text = "Rec"
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

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasCameraPermission(this)
                && PermissionHelper.hasAudioPermission(this)) {
            cameraView.bindLifecycleOwner(this)
        } else {
            PermissionHelper.requestCameraAndAudioPermission(this)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (PermissionHelper.hasCameraPermission(this).not()
                || PermissionHelper.hasAudioPermission(this).not()) {
            Toast.makeText(this,
                    "Camera permission is needed to run this application", Toast.LENGTH_LONG).show()
            PermissionHelper.launchPermissionSettings(this)
            finish()
        } else {
            cameraView.bindLifecycleOwner(this)
        }
    }
}