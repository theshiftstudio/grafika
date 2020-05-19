package com.android.grafika.record.view

import android.graphics.SurfaceTexture
import android.util.Size
import androidx.camera.core.Preview


interface PreviewView {

    val surfaceTexture: SurfaceTexture?
    val surfaceProvider: Preview.SurfaceProvider

    fun setTextureBufferSize(resolution: Size)

}