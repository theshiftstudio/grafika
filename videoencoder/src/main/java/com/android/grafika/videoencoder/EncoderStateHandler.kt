package com.android.grafika.videoencoder

import android.os.Handler
import android.os.Message
import android.util.Log
import com.android.grafika.core.Utils
import java.lang.ref.WeakReference

class EncoderStateHandler @JvmOverloads constructor(
        handler: EncoderStateCallback? = null
) : Handler() {
    // Weak reference to the Activity; only access this from the UI thread.
    private val mWeakSurfaceHandler: WeakReference<EncoderStateCallback?> = WeakReference(handler)

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
        val encoderStateCallback = mWeakSurfaceHandler.get()
        if (encoderStateCallback == null) {
            Log.w(TAG, "CameraHandler.handleMessage: surfaceHandler is null")
            return
        }
        when (what) {
            MSG_RECORDING_STARTED -> encoderStateCallback.onRecordingStarted()
            MSG_RECORDING_RESUMED -> encoderStateCallback.onRecordingResumed()
            MSG_RECORDING_PAUSED -> encoderStateCallback.onRecordingPaused()
            MSG_RECORDING_STOPPED -> encoderStateCallback.onRecordingStopped()
            MSG_RECORDING_FAILED -> encoderStateCallback.onRecordingFailed((inputMessage.obj as Throwable))
            else -> throw RuntimeException("unknown msg $what")
        }
    }

    companion object {
        private const val TAG = Utils.TAG
        const val MSG_RECORDING_STARTED = 0
        const val MSG_RECORDING_RESUMED = 1
        const val MSG_RECORDING_PAUSED = 2
        const val MSG_RECORDING_STOPPED = 3
        const val MSG_RECORDING_FAILED = 4
    }

}