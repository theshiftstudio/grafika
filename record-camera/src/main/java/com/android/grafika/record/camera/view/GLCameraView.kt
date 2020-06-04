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
import com.android.grafika.record.camera.R
import com.android.grafika.videoencoder.VideoEncoderConfig.Companion.DEFAULT_AUDIO_BIT_RATE
import com.android.grafika.videoencoder.VideoEncoderConfig.Companion.DEFAULT_FRAME_RATE
import com.android.grafika.videoencoder.VideoEncoderConfig.Companion.DEFAULT_VIDEO_BIT_RATE
import com.android.grafika.videoencoder.VideoEncoderConfig.Companion.DEFAULT_VIDEO_HEIGHT
import com.android.grafika.videoencoder.VideoEncoderConfig.Companion.DEFAULT_VIDEO_WIDTH
import java.io.File


class GLCameraView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), SurfaceTexture.OnFrameAvailableListener, LifecycleEventObserver {

    @CameraSelector.LensFacing
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    val isRecording
        get() = previewView.isRecording

    val previewWidth
        get() = previewView.width

    val previewHeight
        get() = previewView.height

    private val cameraXModule = GLCameraXModule(this)
    private val previewView by lazy {
        GLCameraSurfacePreviewView(context).apply {
            layoutParams = LayoutParams(videoPreferredWidth, videoPreferredHeight)
        }
    }

    private var videoPreferredWidth: Int = DEFAULT_VIDEO_WIDTH
    private var videoPreferredHeight: Int = DEFAULT_VIDEO_HEIGHT
    private var videoBitRate: Int = DEFAULT_VIDEO_HEIGHT
    private var videoFrameRate: Int = DEFAULT_VIDEO_HEIGHT
    private var audioBitRate: Int = DEFAULT_VIDEO_HEIGHT

    init {
        attrs?.let {
            val array = context.obtainStyledAttributes(it, R.styleable.GLCameraView)
            videoPreferredWidth = array.getInteger(R.styleable.GLCameraView_videoPreferredWidth, DEFAULT_VIDEO_WIDTH)
            videoPreferredHeight = array.getInteger(R.styleable.GLCameraView_videoPreferredHeight, DEFAULT_VIDEO_HEIGHT)
            videoBitRate = array.getInteger(R.styleable.GLCameraView_videoBitRate, DEFAULT_VIDEO_BIT_RATE)
            videoFrameRate = array.getInteger(R.styleable.GLCameraView_videoFrameRate, DEFAULT_FRAME_RATE)
            audioBitRate = array.getInteger(R.styleable.GLCameraView_audioBitRate, DEFAULT_AUDIO_BIT_RATE)
            array.recycle()
        }
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

    fun startRecording(outputFile: File) = previewView.startRecording(outputFile)
    fun stopRecording() = previewView.stopRecording()

    fun flipCameras(lifecycleOwner: LifecycleOwner) {
        when (lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> lensFacing = CameraSelector.LENS_FACING_BACK
            CameraSelector.LENS_FACING_BACK -> lensFacing = CameraSelector.LENS_FACING_FRONT
        }
        cameraXModule.bindToLifecycle(lifecycleOwner, previewView, lensFacing)
    }

    fun onRecordingStarted(block: () -> Unit) = apply {
        previewView.onRecordingStarted = block
    }

    fun onRecordingResumed(block: () -> Unit) = apply {
        previewView.onRecordingResumed = block
    }

    fun onRecordingPaused(block: () -> Unit) = apply {
        previewView.onRecordingPaused = block
    }

    fun onRecordingStopped(block: () -> Unit) = apply {
        previewView.onRecordingStopped = block
    }

    fun onRecordingFailed(block: (Throwable) -> Unit) = apply {
        previewView.onRecordingFailed = block
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