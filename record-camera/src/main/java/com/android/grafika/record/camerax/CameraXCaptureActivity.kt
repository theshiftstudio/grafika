package com.android.grafika.record.camerax

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.android.grafika.record.camera.R
import com.android.grafika.record.view.CameraView
import com.android.grafika.record.view.GLSurfaceCameraView

class CameraXCaptureActivity : AppCompatActivity() {

    private val cameraView by lazy {
        findViewById<GLSurfaceCameraView>(R.id.camera_view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_x_capture)
        cameraView.onSurfaceTextureAvailable = {
            it.bindLifecycleOwner(this)
            cameraView.toggleRecording()
            cameraView.postDelayed({
                cameraView.toggleRecording()
            }, 10_000)
        }
    }

    override fun onResume() {
        super.onResume()
        cameraView.previewView.onResume()
    }

    override fun onPause() {
        super.onPause()
        cameraView.onPause()
    }
}