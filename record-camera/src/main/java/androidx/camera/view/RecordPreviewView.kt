package androidx.camera.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Size
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.impl.utils.Threads
import androidx.camera.view.preview.transform.PreviewTransform
import androidx.core.util.Preconditions


@SuppressLint("VisibleForTests")
class RecordPreviewView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : PreviewView(context, attrs, defStyleAttr), com.android.grafika.record.view.PreviewView {

    @SuppressLint("RestrictedApi")
    private val previewTransform = PreviewTransform()

    init {
        preferredImplementationMode = ImplementationMode.TEXTURE_VIEW
    }

    @SuppressLint("RestrictedApi")
    override fun createSurfaceProvider(cameraInfo: CameraInfo?): Preview.SurfaceProvider {
        Threads.checkMainThread()
        removeAllViews()
        mImplementation = TextureViewImplementation2()
        mImplementation!!.init(this, previewTransform)
        return mImplementation!!.surfaceProvider
    }

    @SuppressLint("RestrictedApi")
    override fun setScaleType(scaleType: ScaleType) {
        previewTransform.scaleType = scaleType
        if (mImplementation != null) {
            mImplementation!!.redrawPreview()
        }
    }

    @SuppressLint("RestrictedApi")
    override fun getScaleType(): ScaleType {
        return previewTransform.scaleType
    }

    @SuppressLint("RestrictedApi")
    override fun createMeteringPointFactory(cameraSelector: CameraSelector): MeteringPointFactory {
        Preconditions.checkNotNull(mImplementation)
        return PreviewViewMeteringPointFactory(display, cameraSelector,
                mImplementation!!.resolution, previewTransform.scaleType, width,
                height)
    }

    override val surfaceTexture: SurfaceTexture?
        get() = (mImplementation as TextureViewImplementation2).mSurfaceTexture
    override val surfaceProvider: Preview.SurfaceProvider
        get() = createSurfaceProvider(null)

    override fun setTextureBufferSize(resolution: Size) {
        //not yet
    }
}