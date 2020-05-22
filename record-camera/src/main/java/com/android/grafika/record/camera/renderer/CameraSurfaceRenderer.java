package com.android.grafika.record.camera.renderer;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import androidx.camera.core.impl.utils.Threads;

import com.android.grafika.core.BuildConfig;
import com.android.grafika.core.Utils;
import com.android.grafika.core.gles.FullFrameRect;
import com.android.grafika.core.gles.Texture2dProgram;
import com.android.grafika.videoencoder.BaseVideoEncoder;
import com.android.grafika.videoencoder.VideoEncoderConfig;

import java.io.File;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Renderer object for our GLSurfaceView.
 * <p>
 * Do not call any methods here directly from another thread -- use the
 * GLSurfaceView#queueEvent() call.
 */
public class CameraSurfaceRenderer implements GLSurfaceView.Renderer {
    // Camera filters; must match up with cameraFilterNames in strings.xml
    public static final int FILTER_NONE = 0;
    public static final int FILTER_BLACK_WHITE = 1;
    public static final int FILTER_BLUR = 2;
    public static final int FILTER_SHARPEN = 3;
    public static final int FILTER_EDGE_DETECT = 4;
    public static final int FILTER_EMBOSS = 5;
    private static final String TAG = Utils.TAG;
    private static final boolean VERBOSE = false;
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final int RECORDING_NULL = -1;
    private static final int RECORDING_OFF = 0;
    private static final int RECORDING_ON = 1;
    private static final int RECORDING_RESUMED = 2;
    private static final int RECORDING_PAUSED = 3;

    private CameraHandler mCameraHandler;
    private BaseVideoEncoder mVideoEncoder;
    private VideoEncoderConfig mEncoderConfig;

    private FullFrameRect mFullScreen;

    private final float[] mSTMatrix = new float[16];
    private int mTextureId;

    private SurfaceTexture mSurfaceTexture;
    private int mRecordingStatus;
    private int mRequestRecordingStatus;
    private int mFrameCount;

    // width/height of the incoming camera preview frames
    private boolean mIncomingSizeUpdated;
    private int mIncomingWidth;
    private int mIncomingHeight;

    private int mCurrentFilter;
    private int mNewFilter;


    /**
     * Constructs CameraSurfaceRenderer.
     * <p>
     * @param cameraHandler Handler for communicating with UI thread
     * @param movieEncoder video encoder object
     */
    public CameraSurfaceRenderer(CameraHandler cameraHandler, BaseVideoEncoder movieEncoder) {
        mCameraHandler = cameraHandler;
        mVideoEncoder = movieEncoder;

        mTextureId = -1;

        mRecordingStatus = RECORDING_NULL;
        mRequestRecordingStatus = RECORDING_NULL;
        mFrameCount = -1;

        mIncomingSizeUpdated = false;
        mIncomingWidth = mIncomingHeight = -1;

        // We could preserve the old filter mode, but currently not bothering.
        mCurrentFilter = -1;
        mNewFilter = FILTER_NONE;
    }

    /**
     * Notifies the renderer thread that the activity is pausing.
     * <p>
     * For best results, call this *after* disabling Camera preview.
     */
    public void notifyPausing() {
        if (mSurfaceTexture != null) {
            Log.d(TAG, "renderer pausing -- releasing SurfaceTexture");
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mFullScreen != null) {
            mFullScreen.release(false);     // assume the GLSurfaceView EGL context is about
            mFullScreen = null;             //  to be destroyed
        }
        mIncomingWidth = mIncomingHeight = -1;
    }

    @SuppressLint("RestrictedApi")
    public void startRecording(VideoEncoderConfig config) {
        Threads.checkBackgroundThread();
        this.mEncoderConfig = config;
        this.mRequestRecordingStatus = RECORDING_ON;
    }

    @SuppressLint("RestrictedApi")
    public void startRecording(File outputFile) {
        Threads.checkBackgroundThread();
        this.mEncoderConfig = baseConfigBuilder(outputFile);
        this.mRequestRecordingStatus = RECORDING_ON;
    }

    @SuppressLint("RestrictedApi")
    public void startRecording(File outputFile, int bitRate) {
        Threads.checkBackgroundThread();
        this.mEncoderConfig = baseConfigBuilder(outputFile)
                .videoBitRate(() -> bitRate);
        this.mRequestRecordingStatus = RECORDING_ON;
    }

    @SuppressLint("RestrictedApi")
    public void resumeRecoding() {
        Threads.checkBackgroundThread();
        this.mRequestRecordingStatus = RECORDING_RESUMED;
    }

    @SuppressLint("RestrictedApi")
    public void pauseRecording() {
        Threads.checkBackgroundThread();
        this.mRequestRecordingStatus = RECORDING_PAUSED;
    }

    @SuppressLint("RestrictedApi")
    @WorkerThread
    public void stopRecording() {
        Threads.checkBackgroundThread();
        if (mEncoderConfig == null) {
            return;
        }
        this.mRequestRecordingStatus = RECORDING_OFF;
        mEncoderConfig = null;
    }

    private VideoEncoderConfig baseConfigBuilder(File outputFile) {
        return new VideoEncoderConfig()
                .width(() -> mIncomingWidth)
                .height(() -> mIncomingHeight)
                .outputFile(() -> outputFile);
    }

    /**
     * Changes the filter that we're applying to the camera preview.
     */
    public void changeFilterMode(int filter) {
        mNewFilter = filter;
    }

    /**
     * Updates the filter program.
     */
    public void updateFilter() {
        Texture2dProgram.ProgramType programType;
        float[] kernel = null;
        float colorAdj = 0.0f;

        Log.d(TAG, "Updating filter to " + mNewFilter);
        switch (mNewFilter) {
            case FILTER_NONE:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT;
                break;
            case FILTER_BLACK_WHITE:
                // (In a previous version the TEXTURE_EXT_BW variant was enabled by a flag called
                // ROSE_COLORED_GLASSES, because the shader set the red channel to the B&W color
                // and green/blue to zero.)
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_BW;
                break;
            case FILTER_BLUR:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[] {
                        1f/16f, 2f/16f, 1f/16f,
                        2f/16f, 4f/16f, 2f/16f,
                        1f/16f, 2f/16f, 1f/16f };
                break;
            case FILTER_SHARPEN:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[] {
                        0f, -1f, 0f,
                        -1f, 5f, -1f,
                        0f, -1f, 0f };
                break;
            case FILTER_EDGE_DETECT:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[] {
                        -1f, -1f, -1f,
                        -1f, 8f, -1f,
                        -1f, -1f, -1f };
                break;
            case FILTER_EMBOSS:
                programType = Texture2dProgram.ProgramType.TEXTURE_EXT_FILT;
                kernel = new float[] {
                        2f, 0f, 0f,
                        0f, -1f, 0f,
                        0f, 0f, -1f };
                colorAdj = 0.5f;
                break;
            default:
                throw new RuntimeException("Unknown filter mode " + mNewFilter);
        }

        // Do we need a whole new program?  (We want to avoid doing this if we don't have
        // too -- compiling a program could be expensive.)
        if (programType != mFullScreen.getProgram().getProgramType()) {
            mFullScreen.changeProgram(new Texture2dProgram(programType));
            // If we created a new program, we need to initialize the texture width/height.
            mIncomingSizeUpdated = true;
        }

        // Update the filter kernel (if any).
        if (kernel != null) {
            mFullScreen.getProgram().setKernel(kernel, colorAdj);
        }

        mCurrentFilter = mNewFilter;
    }

    /**
     * Records the size of the incoming camera preview frames.
     * <p>
     * It's not clear whether this is guaranteed to execute before or after onSurfaceCreated(),
     * so we assume it could go either way.  (Fortunately they both run on the same thread,
     * so we at least know that they won't execute concurrently.)
     */
    public void setCameraPreviewSize(int width, int height) {
        Log.d(TAG, "setCameraPreviewSize");
        mIncomingWidth = width;
        mIncomingHeight = height;
        mIncomingSizeUpdated = true;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated");

        // We're starting up or coming back.  Either way we've got a new EGLContext that will
        // need to be shared with the video encoder, so figure out if a recording is already
        // in progress.
        switch (mRecordingStatus) {
            case RECORDING_NULL:
                mRequestRecordingStatus = mVideoEncoder.isRecording()
                        ? RECORDING_ON
                        : RECORDING_NULL;
                mRecordingStatus = RECORDING_OFF;
                break;
            case RECORDING_PAUSED:
                mRequestRecordingStatus = RECORDING_RESUMED;
                break;
        }

        // Set up the texture blitter that will be used for on-screen display.  This
        // is *not* applied to the recording, because that uses a separate shader.
        mFullScreen = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));

        mTextureId = mFullScreen.createTextureObject();

        // Create a SurfaceTexture, with an external texture, in this EGL context.  We don't
        // have a Looper in this thread -- GLSurfaceView doesn't create one -- so the frame
        // available messages will arrive on the main thread.
        mSurfaceTexture = new SurfaceTexture(mTextureId);

        // Tell the UI thread to enable the camera preview.
        mCameraHandler.sendMessage(mCameraHandler.obtainMessage(
                CameraHandler.MSG_SET_SURFACE_TEXTURE, mSurfaceTexture));
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        Log.d(TAG, "onSurfaceChanged " + width + "x" + height);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onDrawFrame(GL10 unused) {
        if (VERBOSE) Log.d(TAG, "onDrawFrame tex=" + mTextureId);

        // Latch the latest frame.  If there isn't anything new, we'll just re-use whatever
        // was there before.
        mSurfaceTexture.updateTexImage();

        // If the recording state is changing, take care of it here.  Ideally we wouldn't
        // be doing all this in onDrawFrame(), but the EGLContext sharing with GLSurfaceView
        // makes it hard to do elsewhere.
        switch (mRequestRecordingStatus) {
            case RECORDING_ON:
                handleRequestRecordingOn();
                break;
            case RECORDING_RESUMED:
                handleRequestRecordingResume();
                break;
            case RECORDING_PAUSED:
                handleRequestRecordingPause();
                break;
            case RECORDING_OFF:
                handleRequestRecordingOff();
                break;
        }
        mRequestRecordingStatus = RECORDING_NULL;

        // Set the video encoder's texture name.  We only need to do this once, but in the
        // current implementation it has to happen after the video encoder is started, so
        // we just do it here.
        //
        // TODO: be less lame.
        mVideoEncoder.setTextureId(mTextureId);

        // Tell the video encoder thread that a new frame is available.
        // This will be ignored if we're not actually recording.
        mVideoEncoder.frameAvailable(mSurfaceTexture);

        if (mIncomingWidth <= 0 || mIncomingHeight <= 0) {
            // Texture size isn't set yet.  This is only used for the filters, but to be
            // safe we can just skip drawing while we wait for the various races to resolve.
            // (This seems to happen if you toggle the screen off/on with power button.)
            Log.i(TAG, "Drawing before incoming texture size set; skipping");
            return;
        }
        // Update the filter, if necessary.
        if (mCurrentFilter != mNewFilter) {
            updateFilter();
        }
        if (mIncomingSizeUpdated) {
            mFullScreen.getProgram().setTexSize(mIncomingWidth, mIncomingHeight);
            mIncomingSizeUpdated = false;
        }

        // Draw the video frame.
        mSurfaceTexture.getTransformMatrix(mSTMatrix);
        mFullScreen.drawFrame(mTextureId, mSTMatrix);

        // Draw a flashing box if we're recording.  This only appears on screen.
        if (DEBUG && mVideoEncoder.isRecording() && (++mFrameCount & 0x04) == 0) {
            drawBox();
        }
    }

    private void handleRequestRecordingOn() {
        switch (mRecordingStatus) {
            case RECORDING_OFF:
                Log.d(TAG, "START recording");
                // start recording
                mVideoEncoder.startRecording(mEncoderConfig);
                mRecordingStatus = RECORDING_ON;
                mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_RECORDING_STARTED));
                break;
            case RECORDING_PAUSED:
                Log.d(TAG, "RESUME recording");
                mVideoEncoder.resumeRecording();
                mRecordingStatus = RECORDING_ON;
                mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_RECORDING_RESUMED));
                break;
            case RECORDING_RESUMED:
                Log.d(TAG, "RESUME recording");
                mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext());
                mRecordingStatus = RECORDING_ON;
                mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_RECORDING_RESUMED));
                break;
            case RECORDING_ON:
                // yay
                break;
            default:
                throw new RuntimeException("unknown status " + mRecordingStatus);
        }
    }

    private void handleRequestRecordingResume() {
        switch (mRecordingStatus) {
            case RECORDING_OFF:
                throw new IllegalStateException("Requesting to resume after stop! Start first");
            case RECORDING_PAUSED:
                Log.d(TAG, "RESUME recording");
                mVideoEncoder.resumeRecording();
                mRecordingStatus = RECORDING_ON;
                mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_RECORDING_RESUMED));
                break;
            case RECORDING_RESUMED:
                //do nothing
                break;
            case RECORDING_ON:
//                throw new IllegalStateException("Requesting to resume after start! Pause first");
                break;
            default:
                throw new RuntimeException("unknown status " + mRecordingStatus);
        }
    }

    public void handleRequestRecordingPause() {
        switch (mRecordingStatus) {
            case RECORDING_OFF:
//                throw new IllegalStateException("Requesting to pause after stop! Start first");
            case RECORDING_PAUSED:
                //do nothing
                break;
            case RECORDING_RESUMED:
            case RECORDING_ON:
                mVideoEncoder.pauseRecording();
                mRecordingStatus = RECORDING_PAUSED;
                mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_PAUSE_SURFACE));
                mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_RECORDING_PAUSED));
                break;
            default:
                throw new RuntimeException("unknown status " + mRecordingStatus);
        }
    }

    private void handleRequestRecordingOff() {
        switch (mRecordingStatus) {
            case RECORDING_ON:
            case RECORDING_RESUMED:
                // stop recording
                Log.d(TAG, "STOP recording");
                mVideoEncoder.stopRecording();
                mRecordingStatus = RECORDING_OFF;
                mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_RECORDING_STOPPED));
                break;
            case RECORDING_PAUSED:
            case RECORDING_OFF:
                // yay
                break;
            default:
                throw new RuntimeException("unknown status " + mRecordingStatus);
        }
    }

    public SurfaceTexture getSurfaceTexture() {
        return mSurfaceTexture;
    }

    /**
     * Draws a red box in the corner.
     */
    private void drawBox() {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(0, 0, 100, 100);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
}
