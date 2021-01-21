package com.android.grafika.record.camera

import android.annotation.SuppressLint
import android.util.Size
import android.view.Surface
import android.view.View
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.view.preview.transform.PreviewTransform
import androidx.concurrent.futures.CallbackToFutureAdapter
import com.android.grafika.record.camera.view.GLPreviewView
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors

/*
    TODO(@andrei): androidx.camera.view.preview.* is removed in camera-view:1.0.0-alpha20
        (https://android.googlesource.com/platform/frameworks/support/+/a6e72fe54b6e6805131c5730e4e2d97e56f90673%5E%21/)
     so we're stuck with camera-view:1.0.0-alpha18
 */
class RecordSurfaceProvider<T>(
    private val previewView: T,
    private val previewTransform: PreviewTransform
) : Preview.SurfaceProvider where T : GLPreviewView, T : View {

    private var surfaceReleaseFuture: ListenableFuture<SurfaceRequest.Result?>? = null

    var resolution: Size? = null
    var surface: Surface? = null
        private set

    @SuppressLint("RestrictedApi", "Recycle")
    override fun onSurfaceRequested(request: SurfaceRequest) {
        resolution = request.resolution
        previewView.setTextureBufferSize(request.resolution)
        previewTransform.applyCurrentScaleType(
            previewView.parent as View,
            previewView,
            request.resolution
        )
        surface = Surface(previewView.surfaceTexture)
        val surfaceReleaseFuture =
            CallbackToFutureAdapter.getFuture { completer: CallbackToFutureAdapter.Completer<SurfaceRequest.Result?> ->
                request.provideSurface(surface!!,
                    Executors.newSingleThreadExecutor(),
                    { value: SurfaceRequest.Result? -> completer.set(value) }
                )
                ("provideSurface[request=$request surface=$surface]")
            }
        this.surfaceReleaseFuture = surfaceReleaseFuture
    }
}