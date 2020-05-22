package com.android.grafika.record.camerax

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.android.grafika.record.camera.R
import com.android.grafika.record.view.GLCameraView
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
        flip.setOnClickListener {
            cameraView.flipCameras(this)
        }
        record.setOnClickListener {
            cameraView.toggleRecording(outputFile)
        }
    }

}