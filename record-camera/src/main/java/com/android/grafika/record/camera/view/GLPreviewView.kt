package com.android.grafika.record.camera.view

import android.graphics.SurfaceTexture
import android.util.Size
import androidx.camera.core.Preview


interface GLPreviewView {

    val surfaceTexture: SurfaceTexture?
    val surfaceProvider: Preview.SurfaceProvider

    fun setTextureBufferSize(resolution: Size)

}