package com.android.grafika.record.camera.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Size
import android.view.View
import androidx.camera.view.PreviewView
import androidx.camera.view.preview.transform.PreviewTransform
import com.android.grafika.record.camera.RecordSurfaceProvider
import com.android.grafika.record.camera.renderer.CameraSurfaceCallback
import com.android.grafika.record.camera.renderer.CameraSurfaceHandler
import com.android.grafika.record.camera.renderer.CameraSurfaceRenderer
import com.android.grafika.videoencoder.EncoderStateCallback
import com.android.grafika.videoencoder.EncoderStateHandler
import com.android.grafika.videoencoder.VideoEncoderConfig
import com.android.grafika.videoencoder.mediarecorder.MediaRecorderEncoder
import java.io.File


class GLCameraSurfacePreviewView  @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs),
        GLPreviewView,
        CameraSurfaceCallback,
        EncoderStateCallback,
        SurfaceTexture.OnFrameAvailableListener {

    internal var onRecordingStarted: () -> Unit = { }
    internal var onRecordingResumed: () -> Unit = { }
    internal var onRecordingPaused: () -> Unit = { }
    internal var onRecordingStopped: () -> Unit = { }
    internal var onRecordingFailed: (Throwable) -> Unit = { }

    var onSurfaceTextureAvailable: (SurfaceTexture) -> Unit = { }

    @SuppressLint("RestrictedApi")
    private val previewTransform = PreviewTransform().apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    private var scaleType: PreviewView.ScaleType
        @SuppressLint("RestrictedApi")
        get() = previewTransform.scaleType
        @SuppressLint("RestrictedApi")
        set(value) {
            previewTransform.scaleType = value
            if (parent != null && surfaceProvider.resolution != null) {
                previewTransform.applyCurrentScaleType(parent as View, this, surfaceProvider.resolution!!)
            }
        }

    override val surfaceProvider = RecordSurfaceProvider(this, previewTransform)

    private val encoderStateHandler = EncoderStateHandler(this)
    private val videoEncoder = MediaRecorderEncoder(encoderStateHandler)
    private val cameraHandler = CameraSurfaceHandler(this)
    private val renderer = CameraSurfaceRenderer(cameraHandler, videoEncoder)

    val isRecording
        get() = videoEncoder.isRecording

    override val surfaceTexture: SurfaceTexture?
        get() = renderer.surfaceTexture

    init {
        this.preserveEGLContextOnPause = true
        this.setEGLContextClientVersion(2)
        this.setRenderer(renderer)
        this.renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun handleSurfaceTextureAvailable(surfaceTexture: SurfaceTexture?) {
        surfaceTexture?.let {
            it.setOnFrameAvailableListener(this)
            onSurfaceTextureAvailable(it)
        }
    }

    override fun onRecordingPaused() = this.onRecordingPaused.invoke()

    override fun onRecordingStopped() = this.onRecordingStopped.invoke()

    override fun onRecordingStarted() = this.onRecordingStarted.invoke()

    override fun onRecordingResumed() = this.onRecordingResumed.invoke()

    override fun onRecordingFailed(t: Throwable) = this.onRecordingFailed.invoke(t)

    override fun pauseSurface() = Unit

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) = requestRender()

    override fun onResume() {
        super.onResume()
        if (isRecording) {
            resumeRecording()
        }
    }

    override fun onPause() {
        pauseRecording()
        super.onPause()
    }

    fun onDestroy() {
        cameraHandler.invalidateHandler()
        encoderStateHandler.invalidateHandler()
    }

    fun startRecording(outputFile: File) = queueEvent {
        renderer.startRecording(outputFile)
    }

    private fun resumeRecording() = queueEvent {
        renderer.resumeRecoding()
    }

    private fun pauseRecording() {
        queueEvent {
            renderer.pauseRecording()
            renderer.handleRequestRecordingPause()
        }
    }


    fun stopRecording() = queueEvent {
        renderer.stopRecording()
    }

    override fun setTextureBufferSize(resolution: Size) {
        renderer.surfaceTexture?.setDefaultBufferSize(resolution.width, resolution.height)
        queueEvent {
            renderer.setCameraPreviewSize(resolution.height, resolution.width)
        }
    }

}