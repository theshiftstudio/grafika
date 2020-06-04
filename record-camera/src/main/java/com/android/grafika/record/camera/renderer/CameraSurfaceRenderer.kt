package com.android.grafika.record.camera.renderer

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.camera.core.impl.utils.Threads
import com.android.grafika.core.BuildConfig
import com.android.grafika.core.Utils
import com.android.grafika.core.gles.FullFrameRect
import com.android.grafika.core.gles.Texture2dProgram
import com.android.grafika.core.gles.Texture2dProgram.ProgramType
import com.android.grafika.videoencoder.BaseVideoEncoder
import com.android.grafika.videoencoder.VideoEncoderConfig
import java.io.File
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Renderer object for our GLSurfaceView.
 *
 *
 * Do not call any methods here directly from another thread -- use the
 * GLSurfaceView#queueEvent() call.
 */
class CameraSurfaceRenderer(
        private val mCameraSurfaceHandler: CameraSurfaceHandler,
        private val mVideoEncoder: BaseVideoEncoder
) : GLSurfaceView.Renderer {

    private var mEncoderConfig: VideoEncoderConfig? = null
    private var mFullScreen: FullFrameRect? = null
    private val mSTMatrix = FloatArray(16)
    private var mTextureId: Int
    var surfaceTexture: SurfaceTexture? = null
        private set
    private var mRecordingStatus: Int
    private var mRequestRecordingStatus: Int
    private var mFrameCount: Int

    // width/height of the incoming camera preview frames
    private var mIncomingSizeUpdated: Boolean
    private var mIncomingWidth: Int
    private var mIncomingHeight: Int
    private var mCurrentFilter: Int
    private var mNewFilter: Int

    /**
     * Notifies the renderer thread that the activity is pausing.
     *
     *
     * For best results, call this *after* disabling Camera preview.
     */
    fun notifyPausing() {
        if (surfaceTexture != null) {
            Log.d(TAG, "renderer pausing -- releasing SurfaceTexture")
            surfaceTexture!!.release()
            surfaceTexture = null
        }
        if (mFullScreen != null) {
            mFullScreen!!.release(false) // assume the GLSurfaceView EGL context is about
            mFullScreen = null //  to be destroyed
        }
        mIncomingHeight = -1
        mIncomingWidth = mIncomingHeight
    }

    @SuppressLint("RestrictedApi")
    fun startRecording(outputFile: File) {
        startRecording(outputFile, VideoEncoderConfig.DEFAULT_VIDEO_BIT_RATE)
    }

    @SuppressLint("RestrictedApi")
    fun startRecording(outputFile: File, videoBitRate: Int) {
        startRecording(outputFile,
                videoBitRate,
                VideoEncoderConfig.DEFAULT_AUDIO_BIT_RATE,
                VideoEncoderConfig.DEFAULT_FRAME_RATE
        )
    }

    @SuppressLint("RestrictedApi")
    fun startRecording(outputFile: File, videoBitRate: Int, audioBitRate: Int) {
        startRecording(outputFile, videoBitRate, audioBitRate,
                VideoEncoderConfig.DEFAULT_FRAME_RATE
        )
    }

    @SuppressLint("RestrictedApi")
    fun startRecording(outputFile: File, videoBitRate: Int, audioBitRate: Int, frameRate: Int) {
        Threads.checkBackgroundThread()
        mEncoderConfig = VideoEncoderConfig()
                .width { mIncomingWidth }
                .height { mIncomingHeight }
                .videoBitRate { videoBitRate }
                .audioBitRate { audioBitRate }
                .frameRate { frameRate }
                .outputFile { outputFile }
        mRequestRecordingStatus = RECORDING_ON
    }

    @SuppressLint("RestrictedApi")
    fun resumeRecoding() {
        Threads.checkBackgroundThread()
        mRequestRecordingStatus = RECORDING_RESUMED
    }

    @SuppressLint("RestrictedApi")
    fun pauseRecording() {
        Threads.checkBackgroundThread()
        mRequestRecordingStatus = RECORDING_PAUSED
    }

    @SuppressLint("RestrictedApi")
    @WorkerThread
    fun stopRecording() {
        Threads.checkBackgroundThread()
        if (mEncoderConfig == null) {
            return
        }
        mRequestRecordingStatus = RECORDING_OFF
        mEncoderConfig = null
    }

    private fun baseConfigBuilder(outputFile: File): VideoEncoderConfig {
        return VideoEncoderConfig()
                .width { mIncomingWidth }
                .height { mIncomingHeight }
                .audioBitRate { VideoEncoderConfig.DEFAULT_AUDIO_BIT_RATE }
                .videoBitRate { VideoEncoderConfig.DEFAULT_VIDEO_BIT_RATE }
                .frameRate { VideoEncoderConfig.DEFAULT_FRAME_RATE }
                .outputFile { outputFile }
    }

    /**
     * Changes the filter that we're applying to the camera preview.
     */
    fun changeFilterMode(filter: Int) {
        mNewFilter = filter
    }

    /**
     * Updates the filter program.
     */
    fun updateFilter() {
        val programType: ProgramType
        var kernel: FloatArray? = null
        var colorAdj = 0.0f
        Log.d(TAG, "Updating filter to $mNewFilter")
        when (mNewFilter) {
            FILTER_NONE -> programType = ProgramType.TEXTURE_EXT
            FILTER_BLACK_WHITE ->                 // (In a previous version the TEXTURE_EXT_BW variant was enabled by a flag called
                // ROSE_COLORED_GLASSES, because the shader set the red channel to the B&W color
                // and green/blue to zero.)
                programType = ProgramType.TEXTURE_EXT_BW
            FILTER_BLUR -> {
                programType = ProgramType.TEXTURE_EXT_FILT
                kernel = floatArrayOf(
                        1f / 16f, 2f / 16f, 1f / 16f,
                        2f / 16f, 4f / 16f, 2f / 16f,
                        1f / 16f, 2f / 16f, 1f / 16f)
            }
            FILTER_SHARPEN -> {
                programType = ProgramType.TEXTURE_EXT_FILT
                kernel = floatArrayOf(
                        0f, -1f, 0f,
                        -1f, 5f, -1f,
                        0f, -1f, 0f)
            }
            FILTER_EDGE_DETECT -> {
                programType = ProgramType.TEXTURE_EXT_FILT
                kernel = floatArrayOf(
                        -1f, -1f, -1f,
                        -1f, 8f, -1f,
                        -1f, -1f, -1f)
            }
            FILTER_EMBOSS -> {
                programType = ProgramType.TEXTURE_EXT_FILT
                kernel = floatArrayOf(
                        2f, 0f, 0f,
                        0f, -1f, 0f,
                        0f, 0f, -1f)
                colorAdj = 0.5f
            }
            else -> throw RuntimeException("Unknown filter mode $mNewFilter")
        }

        // Do we need a whole new program?  (We want to avoid doing this if we don't have
        // too -- compiling a program could be expensive.)
        if (programType != mFullScreen!!.program.programType) {
            mFullScreen!!.changeProgram(Texture2dProgram(programType))
            // If we created a new program, we need to initialize the texture width/height.
            mIncomingSizeUpdated = true
        }

        // Update the filter kernel (if any).
        if (kernel != null) {
            mFullScreen!!.program.setKernel(kernel, colorAdj)
        }
        mCurrentFilter = mNewFilter
    }

    /**
     * Records the size of the incoming camera preview frames.
     *
     *
     * It's not clear whether this is guaranteed to execute before or after onSurfaceCreated(),
     * so we assume it could go either way.  (Fortunately they both run on the same thread,
     * so we at least know that they won't execute concurrently.)
     */
    fun setCameraPreviewSize(width: Int, height: Int) {
        Log.d(TAG, "setCameraPreviewSize")
        mIncomingWidth = width
        mIncomingHeight = height
        mIncomingSizeUpdated = true
    }

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        Log.d(TAG, "onSurfaceCreated")
        when (mRecordingStatus) {
            RECORDING_NULL -> {
                mRequestRecordingStatus = if (mVideoEncoder.isRecording) RECORDING_ON else RECORDING_NULL
                mRecordingStatus = RECORDING_OFF
            }
            RECORDING_PAUSED -> mRequestRecordingStatus = RECORDING_RESUMED
        }

        // Set up the texture blitter that will be used for on-screen display.  This
        // is *not* applied to the recording, because that uses a separate shader.
        mFullScreen = FullFrameRect(
                Texture2dProgram(ProgramType.TEXTURE_EXT))
        mTextureId = mFullScreen!!.createTextureObject()

        // Create a SurfaceTexture, with an external texture, in this EGL context.  We don't
        // have a Looper in this thread -- GLSurfaceView doesn't create one -- so the frame
        // available messages will arrive on the main thread.
        surfaceTexture = SurfaceTexture(mTextureId)

        // Tell the UI thread to enable the camera preview.
        mCameraSurfaceHandler.sendMessage(mCameraSurfaceHandler.obtainMessage(
                CameraSurfaceHandler.MSG_SET_SURFACE_TEXTURE, surfaceTexture))
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged " + width + "x" + height)
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    override fun onDrawFrame(unused: GL10) {
        if (VERBOSE) Log.d(TAG, "onDrawFrame tex=$mTextureId")

        // Latch the latest frame.  If there isn't anything new, we'll just re-use whatever
        // was there before.
        surfaceTexture!!.updateTexImage()
        when (mRequestRecordingStatus) {
            RECORDING_ON -> handleRequestRecordingOn()
            RECORDING_RESUMED -> handleRequestRecordingResume()
            RECORDING_PAUSED -> handleRequestRecordingPause()
            RECORDING_OFF -> handleRequestRecordingOff()
        }
        mRequestRecordingStatus = RECORDING_NULL

        // Set the video encoder's texture name.  We only need to do this once, but in the
        // current implementation it has to happen after the video encoder is started, so
        // we just do it here.
        //
        // TODO: be less lame.
        mVideoEncoder.updateTextureId(mTextureId)

        // Tell the video encoder thread that a new frame is available.
        // This will be ignored if we're not actually recording.
        mVideoEncoder.frameAvailable(surfaceTexture!!)
        if (mIncomingWidth <= 0 || mIncomingHeight <= 0) {
            // Texture size isn't set yet.  This is only used for the filters, but to be
            // safe we can just skip drawing while we wait for the various races to resolve.
            // (This seems to happen if you toggle the screen off/on with power button.)
            Log.i(TAG, "Drawing before incoming texture size set; skipping")
            return
        }
        // Update the filter, if necessary.
        if (mCurrentFilter != mNewFilter) {
            updateFilter()
        }
        if (mIncomingSizeUpdated) {
            mFullScreen!!.program.setTexSize(mIncomingWidth, mIncomingHeight)
            mIncomingSizeUpdated = false
        }

        // Draw the video frame.
        surfaceTexture!!.getTransformMatrix(mSTMatrix)
        mFullScreen!!.drawFrame(mTextureId, mSTMatrix)

        // Draw a flashing box if we're recording.  This only appears on screen.
        if (DEBUG && mVideoEncoder.isRecording && ++mFrameCount and 0x04 == 0) {
            drawBox()
        }
    }

    private fun handleRequestRecordingOn() {
        when (mRecordingStatus) {
            RECORDING_OFF -> {
                Log.d(TAG, "START recording")
                // start recording
                mVideoEncoder.startRecording(mEncoderConfig)
                mRecordingStatus = RECORDING_ON
            }
            RECORDING_PAUSED -> {
                Log.d(TAG, "RESUME recording")
                mVideoEncoder.resumeRecording()
                mRecordingStatus = RECORDING_ON
            }
            RECORDING_RESUMED -> {
                Log.d(TAG, "RESUME recording")
                mVideoEncoder.updateSharedContext(EGL14.eglGetCurrentContext())
                mRecordingStatus = RECORDING_ON
            }
            RECORDING_ON -> {
            }
            else -> throw RuntimeException("unknown status $mRecordingStatus")
        }
    }

    private fun handleRequestRecordingResume() {
        when (mRecordingStatus) {
            RECORDING_OFF -> throw IllegalStateException("Requesting to resume after stop! Start first")
            RECORDING_PAUSED -> {
                Log.d(TAG, "RESUME recording")
                mVideoEncoder.resumeRecording()
                mRecordingStatus = RECORDING_ON
            }
            RECORDING_RESUMED -> {
            }
            RECORDING_ON -> {
            }
            else -> throw RuntimeException("unknown status $mRecordingStatus")
        }
    }

    fun handleRequestRecordingPause() {
        when (mRecordingStatus) {
            RECORDING_OFF, RECORDING_PAUSED -> {
            }
            RECORDING_RESUMED, RECORDING_ON -> {
                mVideoEncoder.pauseRecording()
                mRecordingStatus = RECORDING_PAUSED
                mCameraSurfaceHandler.sendMessage(mCameraSurfaceHandler.obtainMessage(CameraSurfaceHandler.MSG_PAUSE_SURFACE))
            }
            else -> throw RuntimeException("unknown status $mRecordingStatus")
        }
    }

    private fun handleRequestRecordingOff() {
        when (mRecordingStatus) {
            RECORDING_ON, RECORDING_RESUMED -> {
                // stop recording
                Log.d(TAG, "STOP recording")
                mVideoEncoder.stopRecording()
                mRecordingStatus = RECORDING_OFF
            }
            RECORDING_PAUSED, RECORDING_OFF -> {
            }
            else -> throw RuntimeException("unknown status $mRecordingStatus")
        }
    }

    /**
     * Draws a red box in the corner.
     */
    private fun drawBox() {
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(0, 0, 100, 100)
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    companion object {
        // Camera filters; must match up with cameraFilterNames in strings.xml
        const val FILTER_NONE = 0
        const val FILTER_BLACK_WHITE = 1
        const val FILTER_BLUR = 2
        const val FILTER_SHARPEN = 3
        const val FILTER_EDGE_DETECT = 4
        const val FILTER_EMBOSS = 5
        private const val TAG = Utils.TAG
        private const val VERBOSE = false
        private val DEBUG = BuildConfig.DEBUG
        private const val RECORDING_NULL = -1
        private const val RECORDING_OFF = 0
        private const val RECORDING_ON = 1
        private const val RECORDING_RESUMED = 2
        private const val RECORDING_PAUSED = 3
    }

    /**
     * Constructs CameraSurfaceRenderer.
     *
     *
     * @param cameraSurfaceHandler Handler for communicating with UI thread
     * @param movieEncoder video encoder object
     */
    init {
        mTextureId = -1
        mRecordingStatus = RECORDING_NULL
        mRequestRecordingStatus = RECORDING_NULL
        mFrameCount = -1
        mIncomingSizeUpdated = false
        mIncomingHeight = -1
        mIncomingWidth = mIncomingHeight

        // We could preserve the old filter mode, but currently not bothering.
        mCurrentFilter = -1
        mNewFilter = FILTER_NONE
    }
}