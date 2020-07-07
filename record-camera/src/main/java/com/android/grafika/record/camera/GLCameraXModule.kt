package com.android.grafika.record.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.impl.LensFacingCameraFilter
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.android.grafika.record.camera.view.GLCameraView
import com.android.grafika.record.camera.view.GLPreviewView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class GLCameraXModule(private val cameraView: View) {

    private val cameraManager by lazy {
        cameraView.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val screenAspectRatio by lazy {
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { cameraView.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")
    }

    @SuppressLint("RestrictedApi")
    fun <T> bindToLifecycle(
            lifecycleOwner: LifecycleOwner,
            previewView: T,
            @CameraSelector.LensFacing lensFacing: Int = CameraSelector.LENS_FACING_FRONT
    ) where T: GLPreviewView, T: View = cameraView.post {
        val cameraSelector = CameraSelector.Builder()
                .appendFilter { cameras ->
                    getFirstCameraIdFacing(cameraManager, lensFacing)?.let { cameraId ->
                        cameras.filter {
                            it.cameraInfoInternal.cameraId == cameraId
                        }.toSet()
                    } ?: LensFacingCameraFilter(lensFacing).filterCameras(cameras)
                }
                .build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(cameraView.context)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Preview
            val preview = Preview.Builder()
                    // We request aspect ratio but no resolution
                    .setTargetAspectRatio(screenAspectRatio)
                    // Set initial target rotation
                    .setTargetRotation(cameraView.display.rotation)
                    .build()

            // Must unbind the use-cases before rebinding them
            cameraProvider.unbindAll()

            try {
                // A variable number of use-cases can be passed here -
                // camera provides access to CameraControl & CameraInfo
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)
                // Attach the viewfinder's surface provider to preview use case
                preview.setSurfaceProvider(previewView.surfaceProvider)
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(cameraView.context))
    }

    private fun getFirstCameraIdFacing(
            cameraManager: CameraManager,
            facing: Int = CameraSelector.LENS_FACING_FRONT
    ): String? {
        // Get list of all compatible cameras
        val cameraIds = cameraManager.cameraIdList.filter {
            cameraManager.getCameraCharacteristics(it).supportsRgb()
        }

        val metadataFacing = when (facing) {
            CameraSelector.LENS_FACING_FRONT -> CameraMetadata.LENS_FACING_FRONT
            CameraSelector.LENS_FACING_BACK -> CameraMetadata.LENS_FACING_BACK
            else -> CameraMetadata.LENS_FACING_FRONT
        }
        // Iterate over the list of cameras and return the first one matching desired
        // lens-facing configuration
        return cameraIds.firstOrNull {
            cameraManager.getCameraCharacteristics(it)[CameraCharacteristics.LENS_FACING] == metadataFacing
        }
        // If no camera matched desired orientation, return the first one from the list
    }

    fun unbindUseCases(block: () -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(cameraView.context)
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Must unbind the use-cases before rebinding them
            cameraProvider.unbindAll()
            block()
        }, ContextCompat.getMainExecutor(cameraView.context))
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }


    companion object {
        private val TAG by lazy { GLCameraView::class.java.simpleName }
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }
}

fun CameraCharacteristics.supportsRgb() : Boolean =
        this[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]
                ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)
                ?: false