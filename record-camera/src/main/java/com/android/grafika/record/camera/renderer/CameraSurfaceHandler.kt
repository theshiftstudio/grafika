package com.android.grafika.record.camera.renderer

import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Message
import android.util.Log
import com.android.grafika.core.Utils
import java.lang.ref.WeakReference

/**
 * Handles camera operation requests from other threads.  Necessary because the Camera
 * must only be accessed from one thread.
 *
 *
 * The object is created on the UI thread, and all handlers run there.  Messages are
 * sent from other threads, using sendMessage().
 */
class CameraSurfaceHandler(handler: CameraSurfaceCallback) : Handler() {
    // Weak reference to the Activity; only access this from the UI thread.
    private val mWeakSurfaceHandler: WeakReference<CameraSurfaceCallback> = WeakReference(handler)

    /**
     * Drop the reference to the activity.  Useful as a paranoid measure to ensure that
     * attempts to access a stale Activity through a handler are caught.
     */
    fun invalidateHandler() {
        mWeakSurfaceHandler.clear()
    }

    // runs on UI thread
    override fun handleMessage(inputMessage: Message) {
        val what = inputMessage.what
        Log.d(TAG, "CameraHandler [$this]: what=$what")
        val cameraSurfaceCallback = mWeakSurfaceHandler.get()
        if (cameraSurfaceCallback == null) {
            Log.w(TAG, "CameraHandler.handleMessage: surfaceHandler is null")
            return
        }
        when (what) {
            MSG_SET_SURFACE_TEXTURE -> cameraSurfaceCallback.handleSurfaceTextureAvailable(inputMessage.obj as SurfaceTexture)
            MSG_PAUSE_SURFACE -> cameraSurfaceCallback.pauseSurface()
            else -> throw RuntimeException("unknown msg $what")
        }
    }

    companion object {
        private const val TAG = Utils.TAG
        const val MSG_SET_SURFACE_TEXTURE = 0
        const val MSG_PAUSE_SURFACE = 1
    }

}