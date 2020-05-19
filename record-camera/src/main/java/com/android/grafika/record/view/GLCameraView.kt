package com.android.grafika.record.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.android.synthetic.main.activity_camera_x_capture.view.*
import java.io.File


class GLCameraView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), SurfaceTexture.OnFrameAvailableListener, LifecycleEventObserver {

    @CameraSelector.LensFacing
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    private val cameraXModule = GLCameraXModule(this)
    private val previewView = GLSurfacePreviewView(context).apply {
        layoutParams = LayoutParams(1080, 1920)
    }

    init {
        this.addView(previewView, 0)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) = previewView.requestRender()

    fun bindLifecycleOwner(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
        previewView.onSurfaceTextureAvailable = {
            cameraXModule.bindToLifecycle(lifecycleOwner, previewView)
        }
        previewView.surfaceTexture?.let {
            previewView.onSurfaceTextureAvailable(it)
        }
    }

    fun toggleRecording(outputFile: File) {
        when (previewView.recordingEnabled) {
            false -> previewView.startRecording(outputFile)
            true -> previewView.stopRecording()
        }
    }

    fun flipCameras(lifecycleOwner: LifecycleOwner) {
        when (lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> lensFacing = CameraSelector.LENS_FACING_BACK
            CameraSelector.LENS_FACING_BACK -> lensFacing = CameraSelector.LENS_FACING_FRONT
        }
        cameraXModule.bindToLifecycle(lifecycleOwner, previewView, lensFacing)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_RESUME -> previewView.onResume()
            Lifecycle.Event.ON_PAUSE -> previewView.onPause()
        }
    }
}