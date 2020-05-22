package com.android.grafika.record.view

import android.annotation.SuppressLint
import android.util.Size
import android.view.Surface
import android.view.View
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.view.preview.transform.PreviewTransform
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import com.android.grafika.record.view.GLSurfacePreviewView
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors


class RecordSurfaceProvider<T>(
        private val previewView: T,
        private val previewTransform: PreviewTransform
) : Preview.SurfaceProvider  where T: PreviewView, T: View {

    private var surfaceReleaseFuture: ListenableFuture<SurfaceRequest.Result?>? = null

    var resolution: Size? = null
    var surface: Surface? = null
        private set

    @SuppressLint("RestrictedApi", "Recycle")
    override fun onSurfaceRequested(request: SurfaceRequest) {
        resolution = request.resolution
        previewView.setTextureBufferSize(request.resolution)
        previewTransform.applyCurrentScaleType(previewView.parent as View, previewView, request.resolution)
        surface = Surface(previewView.surfaceTexture)
        val surfaceReleaseFuture = CallbackToFutureAdapter.getFuture {
            completer: CallbackToFutureAdapter.Completer<SurfaceRequest.Result?> ->
            request.provideSurface(surface!!,
                    Executors.newSingleThreadExecutor(),
                    Consumer { value: SurfaceRequest.Result? -> completer.set(value) }
            )
            ("provideSurface[request=$request surface=$surface]")
        }
        this.surfaceReleaseFuture = surfaceReleaseFuture
        this.surfaceReleaseFuture?.addListener(Runnable {
            surface?.release()
            surface = null
            if (this.surfaceReleaseFuture === surfaceReleaseFuture) {
                this.surfaceReleaseFuture = null
            }
        }, ContextCompat.getMainExecutor(previewView.context))
    }

}