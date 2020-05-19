package androidx.camera.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.android.grafika.record.view.GLCameraXModule
import kotlinx.android.synthetic.main.activity_camera_x_capture.view.*
import java.io.File


class RecordCameraView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs), LifecycleEventObserver {

    @CameraSelector.LensFacing
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    private val cameraXModule = GLCameraXModule(this)
    private val previewView = RecordPreviewView(context).apply {
        layoutParams = LayoutParams(1080, 1920)
    }

    init {
        this.addView(previewView, 0)
    }

    fun bindLifecycleOwner(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
        cameraXModule.bindToLifecycle(lifecycleOwner, previewView)
    }

    fun toggleRecording(outputFile: File) {
//        when (previewView.recordingEnabled) {
//            false -> previewView.startRecording(outputFile)
//            true -> previewView.stopRecording()
//        }
    }

    fun flipCameras(lifecycleOwner: LifecycleOwner) {
        when (lensFacing) {
            CameraSelector.LENS_FACING_FRONT -> lensFacing = CameraSelector.LENS_FACING_BACK
            CameraSelector.LENS_FACING_BACK -> lensFacing = CameraSelector.LENS_FACING_FRONT
        }
        cameraXModule.bindToLifecycle(lifecycleOwner, previewView, lensFacing)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
//            Lifecycle.Event.ON_RESUME -> previewView.onResume()
//            Lifecycle.Event.ON_PAUSE -> previewView.onPause()
        }
    }
}