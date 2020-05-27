package com.android.grafika.record.camera.renderer;

import android.graphics.SurfaceTexture;

public interface CameraSurfaceCallback {
    void handleSurfaceTextureAvailable(SurfaceTexture surfaceTexture);
    void pauseSurface();
}
