package com.android.grafika.videoencoder;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.grafika.core.Utils;

import java.lang.ref.WeakReference;

public class EncoderStateHandler extends Handler {
    private static final String TAG = Utils.TAG;

    public static final int MSG_RECORDING_STARTED = 0;
    public static final int MSG_RECORDING_RESUMED = 1;
    public static final int MSG_RECORDING_PAUSED = 2;
    public static final int MSG_RECORDING_STOPPED = 3;
    public static final int MSG_RECORDING_FAILED = 4;

    // Weak reference to the Activity; only access this from the UI thread.
    private WeakReference<EncoderStateCallback> mWeakSurfaceHandler;
    public EncoderStateHandler() {
        this(null);
    }
    public EncoderStateHandler(EncoderStateCallback handler) {
        mWeakSurfaceHandler = new WeakReference<>(handler);
    }

    /**
     * Drop the reference to the activity.  Useful as a paranoid measure to ensure that
     * attempts to access a stale Activity through a handler are caught.
     */
    public void invalidateHandler() {
        mWeakSurfaceHandler.clear();
    }

    @Override  // runs on UI thread
    public void handleMessage(Message inputMessage) {
        int what = inputMessage.what;
        Log.d(TAG, "CameraHandler [" + this + "]: what=" + what);

        EncoderStateCallback encoderStateCallback = mWeakSurfaceHandler.get();
        if (encoderStateCallback == null) {
            Log.w(TAG, "CameraHandler.handleMessage: surfaceHandler is null");
            return;
        }

        switch (what) {
            case MSG_RECORDING_STARTED:
                encoderStateCallback.onRecordingStarted();
                break;
            case MSG_RECORDING_RESUMED:
                encoderStateCallback.onRecordingResumed();
                break;
            case MSG_RECORDING_PAUSED:
                encoderStateCallback.onRecordingPaused();
                break;
            case MSG_RECORDING_STOPPED:
                encoderStateCallback.onRecordingStopped();
                break;
            case MSG_RECORDING_FAILED:
                encoderStateCallback.onRecordingFailed(((Throwable) inputMessage.obj));
                break;
            default:
                throw new RuntimeException("unknown msg " + what);
        }
    }

}
