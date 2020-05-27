package com.android.grafika.record.camera.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.android.grafika.record.camera.GLCameraXModule
import com.android.grafika.videoencoder.VideoEncoderConfig
import java.io.File


class GLCameraView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), SurfaceTexture.OnFrameAvailableListener, LifecycleEventObserver {

    @CameraSelector.LensFacing
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    val isRecording
        get() = previewView.isRecording

    private val cameraXModule = GLCameraXModule(this)
    private val previewView = GLCameraSurfacePreviewView(context).apply {
        layoutParams = LayoutParams(1080, 1920)
    }

    var onRecordingStarted: () -> Unit
        get() = previewView.onRecordingStarted
        set(value) { previewView.onRecordingStarted = value }
    var onRecordingResumed: () -> Unit
        get() = previewView.onRecordingResumed
        set(value) { previewView.onRecordingResumed = value }
    var onRecordingPaused: () -> Unit
        get() = previewView.onRecordingPaused
        set(value) { previewView.onRecordingPaused = value }
    var onRecordingStopped: () -> Unit
        get() = previewView.onRecordingStopped
        set(value) { previewView.onRecordingStopped = value }
    var onRecordingFailed: (Throwable) -> Unit
        get() = previewView.onRecordingFailed
        set(value) { previewView.onRecordingFailed = value }

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
        when (previewView.isRecording) {
            false -> previewView.startRecording(outputFile)
            true -> previewView.stopRecording()
        }
    }

    fun startRecording(config: VideoEncoderConfig) = previewView.startRecording(config)
    fun startRecording(outputFile: File) = previewView.startRecording(outputFile)
    fun stopRecording() = previewView.stopRecording()

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
            Lifecycle.Event.ON_DESTROY -> previewView.onDestroy()
            else -> Unit
        }
    }
}