package com.android.grafika.record.camera;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.android.grafika.core.Utils;

import java.lang.ref.WeakReference;

/**
 * Handles camera operation requests from other threads.  Necessary because the Camera
 * must only be accessed from one thread.
 * <p>
 * The object is created on the UI thread, and all handlers run there.  Messages are
 * sent from other threads, using sendMessage().
 */
public class CameraHandler extends Handler {
    private static final String TAG = Utils.TAG;

    public static final int MSG_SET_SURFACE_TEXTURE = 0;

    // Weak reference to the Activity; only access this from the UI thread.
    private WeakReference<SurfaceTextureHandler> mWeakSurfaceHandler;

    public CameraHandler(SurfaceTextureHandler handler) {
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

        SurfaceTextureHandler surfaceTextureHandler = mWeakSurfaceHandler.get();
        if (surfaceTextureHandler == null) {
            Log.w(TAG, "CameraHandler.handleMessage: surfaceHandler is null");
            return;
        }

        switch (what) {
            case MSG_SET_SURFACE_TEXTURE:
                surfaceTextureHandler.handleSetSurfaceTexture((SurfaceTexture) inputMessage.obj);
                break;
            default:
                throw new RuntimeException("unknown msg " + what);
        }
    }

    public interface SurfaceTextureHandler {
        void handleSetSurfaceTexture(SurfaceTexture surfaceTexture);
    }
}
