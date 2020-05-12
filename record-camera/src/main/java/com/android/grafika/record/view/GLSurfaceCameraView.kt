package com.android.grafika.record.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import com.android.grafika.record.camera.CameraHandler
import com.android.grafika.record.camera.CameraSurfaceRenderer
import com.android.grafika.videoencoder.TextureMovieEncoder
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class GLSurfaceCameraView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), CameraHandler.SurfaceTextureHandler, SurfaceTexture.OnFrameAvailableListener {

    var onSurfaceTextureAvailable: (GLSurfaceCameraView) -> Unit = { }

    private var surfaceReleaseFuture: ListenableFuture<SurfaceRequest.Result?>? = null
    var outputFile: File = File(context.filesDir, "camera-test.mp4")

    private var recordingEnabled = videoEncoder.isRecording
    private val cameraHandler = CameraHandler(this)
    private val renderer = CameraSurfaceRenderer(cameraHandler, videoEncoder, outputFile)

    @CameraSelector.LensFacing
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    private val screenAspectRatio by lazy {
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun bindLifecycleOwner(lifecycleOwner: LifecycleOwner) = post {
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    // We request aspect ratio but no resolution
                    .setTargetAspectRatio(screenAspectRatio)
                    // Set initial target rotation
                    .setTargetRotation(display.rotation)
                    .build()

            // Must unbind the use-cases before rebinding them
            cameraProvider.unbindAll()

            try {
                // A variable number of use-cases can be passed here -
                // camera provides access to CameraControl & CameraInfo
                val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview)
                // Attach the viewfinder's surface provider to preview use case
                preview.setSurfaceProvider {
                    queueEvent {
                        renderer.setCameraPreviewSize(it.resolution.width, it.resolution.height)
                    }
                    val surface = Surface(renderer.surfaceTexture)
                    val surfaceReleaseFuture = CallbackToFutureAdapter.getFuture { completer: CallbackToFutureAdapter.Completer<SurfaceRequest.Result?> ->
                        it.provideSurface(surface,
                                Executors.newSingleThreadExecutor(),
                                Consumer { value: SurfaceRequest.Result? -> completer.set(value) }
                        )
                        ("provideSurface[request=$it surface=$surface]")
                    }
                    this.surfaceReleaseFuture = surfaceReleaseFuture
                    this.surfaceReleaseFuture?.addListener(Runnable {
                        surface.release()
                        if (this.surfaceReleaseFuture === surfaceReleaseFuture) {
                            this.surfaceReleaseFuture = null
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) = requestRender()

    override fun handleSetSurfaceTexture(surfaceTexture: SurfaceTexture?) {
        surfaceTexture?.let {
            it.setOnFrameAvailableListener(this)
            onSurfaceTextureAvailable(this)
        }
    }

    fun toggleRecording() {
        recordingEnabled = recordingEnabled.not()
        queueEvent {
            renderer.changeRecordingState(recordingEnabled)
        }
    }

    fun flipCameras() {
        TODO("not implemented")
    }

    override fun onPause() {
        queueEvent {
            renderer.notifyPausing()
        }
        super.onPause()
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }
    companion object {
        private val TAG by lazy { GLSurfaceCameraView::class.java.simpleName }
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        private val videoEncoder = TextureMovieEncoder()

    }
}