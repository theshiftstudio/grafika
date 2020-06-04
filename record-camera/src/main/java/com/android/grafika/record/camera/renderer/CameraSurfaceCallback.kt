package com.android.grafika.record.camera.renderer

import android.graphics.SurfaceTexture

interface CameraSurfaceCallback {
    fun handleSurfaceTextureAvailable(surfaceTexture: SurfaceTexture?)
    fun pauseSurface()
}