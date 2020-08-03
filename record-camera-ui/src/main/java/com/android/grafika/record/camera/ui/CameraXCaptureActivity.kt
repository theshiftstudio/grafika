package com.android.grafika.record.camera.ui

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import com.android.grafika.core.PermissionHelper
import com.android.grafika.record.camera.view.GLCameraView
import java.io.File

class CameraXCaptureActivity : AppCompatActivity() {

    private val cameraView by lazy { findViewById<GLCameraView>(R.id.camera_view) }
    private val flip by lazy { findViewById<Button>(R.id.flip) }
    private val record by lazy { findViewById<Button>(R.id.record) }
    private val power by lazy { findViewById<ToggleButton>(R.id.power) }
    private val outputFile by lazy { File(filesDir, "camera-test.mp4") }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_x_capture)
        cameraView
                .onRecordingStarted {
                    record.text = "Stop"
                    Log.d(CameraXCaptureActivity::class.java.simpleName, "recording STARTED!")
                }
                .onRecordingPaused {
                    record.text = "Paused"
                    Log.d(CameraXCaptureActivity::class.java.simpleName, "recording PAUSED!Start")
                }
                .onRecordingResumed {
                    record.text = "Stop"
                    Log.d(CameraXCaptureActivity::class.java.simpleName, "recording RESUMED!")
                }
                .onRecordingStopped {
                    Log.d(CameraXCaptureActivity::class.java.simpleName, "recording STOPPED!")
                    record.text = "Rec"
                }
                .onRecordingFailed {
                    record.text = "Rec"
                    Log.e(CameraXCaptureActivity::class.java.simpleName, "recording FAILED!", it)
                }
        flip.setOnClickListener {
            cameraView.flipCameras(this)
        }
        record.setOnClickListener {
            cameraView.toggleRecording(outputFile)
        }
        power.setOnCheckedChangeListener { buttonView, isChecked ->
            powerOnCamera()
        }
    }

    private fun powerOnCamera() {
        if (power.isChecked) {
            flip.isEnabled = true
            cameraView.bindLifecycleOwner(this)
        } else {
            flip.isEnabled = false
            cameraView.unbindUseCases()
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionHelper.hasCameraPermission(this)
                && PermissionHelper.hasAudioPermission(this)) {
            powerOnCamera()
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
            powerOnCamera()
        }
    }
}