package androidx.camera.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import androidx.camera.core.CameraInfo
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView


class RecordPreviewView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : PreviewView(context, attrs, defStyleAttr) {

    val surfaceTexture: SurfaceTexture?
        get() = if (mImplementation is TextureViewImplementation) {
            (mImplementation as TextureViewImplementation).mSurfaceTexture
        } else {
            null
        }
}