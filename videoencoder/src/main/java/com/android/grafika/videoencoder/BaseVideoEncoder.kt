/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.grafika.videoencoder

import android.graphics.SurfaceTexture
import android.opengl.EGLContext
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.android.grafika.core.Utils
import com.android.grafika.core.gles.EglCore
import com.android.grafika.core.gles.FullFrameRect
import com.android.grafika.core.gles.Texture2dProgram
import com.android.grafika.core.gles.WindowSurface
import java.io.IOException
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Encode a movie from frames rendered from an external texture image.
 *
 *
 * The object wraps an encoder running on a dedicated thread.  The various control messages
 * may be sent from arbitrary threads (typically the app UI thread).  The encoder thread
 * manages both sides of the encoder (feeding and draining); the only external input is
 * the GL texture.
 *
 *
 * The design is complicated slightly by the need to create an EGL context that shares state
 * with a androidx.camera.view that gets restarted if (say) the device orientation changes.  When the androidx.camera.view
 * in question is a GLSurfaceView, we don't have full control over the EGL context creation
 * on that side, so we have to bend a bit backwards here.
 *
 *
 * To use:
 *
 *  * create TextureMovieEncoder object
 *  * create an EncoderConfig
 *  * call TextureMovieEncoder#startRecording() with the config
 *  * call TextureMovieEncoder#setTextureId() with the texture object that receives frames
 *  * for each frame, after latching it with SurfaceTexture#updateTexImage(),
 * call TextureMovieEncoder#frameAvailable().
 *
 *
 * TODO: tweak the API (esp. textureId) so it's less awkward for simple use cases.
 */
abstract class BaseVideoEncoder(
        private val encoderStateHandler: EncoderStateHandler
) : Runnable, EncoderStateCallback {
    // ----- accessed exclusively by encoder thread -----
    protected var inputWindowSurface: WindowSurface? = null
    protected var fullScreen: FullFrameRect? = null
    protected var textureId = 0
    protected var frameNum = 0
    protected var videoEncoder: EncoderCore? = null

    private var eglCore: EglCore? = null

    // ----- accessed by multiple threads -----
    @Volatile
    protected var handler: EncoderHandler? = null
    protected var ready = false
    protected var running = false

    private val lock = ReentrantLock() // guards ready/running
    private val condition = lock.newCondition()

    /**
     * Tells the video recorder to start recording.  (Call from non-encoder thread.)
     *
     *
     * Creates a new thread, which will create an encoder using the provided configuration.
     *
     *
     * Returns after the recorder thread has started and is ready to accept Messages.  The
     * encoder may not yet be fully configured.
     */
    fun startRecording(config: VideoEncoderConfig?) {
        Log.d(TAG, "Encoder: startRecording()")
        lock.withLock {
            if (running) {
                Log.w(TAG, "Encoder thread already running")
                return
            }
            running = true
            Thread(this, "BaseVideoEncoder").start()
            while (!ready) {
                try {
                    condition.await()
                } catch (ie: InterruptedException) {
                    // ignore
                }
            }
        }
        handler!!.sendMessage(handler!!.obtainMessage(MSG_START_RECORDING, config))
    }

    /**
     * Tells the video recorder to stop recording.  (Call from non-encoder thread.)
     *
     *
     * Returns immediately; the encoder/muxer may not yet be finished creating the movie.
     *
     *
     * TODO: have the encoder thread invoke a callback on the UI thread just before it shuts down
     * so we can provide reasonable status UI (and let the caller know that movie encoding
     * has completed).
     */
    fun stopRecording() {
        handler!!.sendMessage(handler!!.obtainMessage(MSG_STOP_RECORDING))
        handler!!.sendMessage(handler!!.obtainMessage(MSG_QUIT))
        // We don't know when these will actually finish (or even start).  We don't want to
        // delay the UI thread though, so we return immediately.
    }

    fun pauseRecording() {
        handler!!.sendMessage(handler!!.obtainMessage(MSG_PAUSE_RECORDING))
    }

    fun resumeRecording() {
        handler!!.sendMessage(handler!!.obtainMessage(MSG_RESUME_RECORDING))
    }

    /**
     * Returns true if recording has been started.
     */
    val isRecording: Boolean
        get() {
            lock.withLock {
                return running
            }
        }

    /**
     * Tells the video recorder to refresh its EGL surface.  (Call from non-encoder thread.)
     */
    fun updateSharedContext(sharedContext: EGLContext?) {
        handler!!.sendMessage(handler!!.obtainMessage(MSG_UPDATE_SHARED_CONTEXT, sharedContext))
    }

    /**
     * Tells the video recorder that a new frame is available.  (Call from non-encoder thread.)
     *
     *
     * This function sends a message and returns immediately.  This isn't sufficient -- we
     * don't want the caller to latch a new frame until we're done with this one -- but we
     * can get away with it so long as the input frame rate is reasonable and the encoder
     * thread doesn't stall.
     *
     *
     * TODO: either block here until the texture has been rendered onto the encoder surface,
     * or have a separate "block if still busy" method that the caller can execute immediately
     * before it calls updateTexImage().  The latter is preferred because we don't want to
     * stall the caller while this thread does work.
     */
    fun frameAvailable(st: SurfaceTexture) {
        lock.withLock {
            if (!ready) {
                return
            }
        }
        val transform = FloatArray(16) // TODO - avoid alloc every frame
        st.getTransformMatrix(transform)
        val timestamp = st.timestamp
        if (timestamp == 0L) {
            // Seeing this after device is toggled off/on with power button.  The
            // first frame back has a zero timestamp.
            //
            // MPEG4Writer thinks this is cause to abort() in native code, so it's very
            // important that we just ignore the frame.
            Log.w(TAG, "HEY: got SurfaceTexture with timestamp of zero")
            return
        }
        handler!!.sendMessage(handler!!.obtainMessage(MSG_FRAME_AVAILABLE,
                (timestamp shr 32).toInt(), timestamp.toInt(), transform))
    }

    /**
     * Tells the video recorder what texture name to use.  This is the external texture that
     * we're receiving camera previews in.  (Call from non-encoder thread.)
     * TODO: do something less clumsy
     */
    fun updateTextureId(id: Int) {
        lock.withLock {
            if (!ready) {
                return
            }
        }
        handler!!.sendMessage(handler!!.obtainMessage(MSG_SET_TEXTURE_ID, id, 0, null))
    }

    /**
     * Encoder thread entry point.  Establishes Looper/Handler and waits for messages.
     *
     *
     * @see Thread.run
     */
    override fun run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare()
        lock.withLock {
            handler = EncoderHandler(this)
            ready = true
            condition.signal()
        }
        Looper.loop()
        Log.d(TAG, "Encoder thread exiting")
        lock.withLock {
            running = false
            ready = running
            handler = null
        }
    }

    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    protected class EncoderHandler(encoder: BaseVideoEncoder) : Handler() {
        private var mWeakEncoder: WeakReference<BaseVideoEncoder> = WeakReference(encoder)

        // runs on encoder thread
        override fun handleMessage(inputMessage: Message) {
            val what = inputMessage.what
            val obj = inputMessage.obj
            val encoder = mWeakEncoder.get()
            if (encoder == null) {
                Log.w(TAG, "EncoderHandler.handleMessage: encoder is null")
                return
            }
            when (what) {
                MSG_START_RECORDING -> encoder.handleStartRecording(obj as VideoEncoderConfig)
                MSG_STOP_RECORDING -> encoder.handleStopRecording()
                MSG_FRAME_AVAILABLE -> {
                    val timestamp = inputMessage.arg1.toLong() shl 32 or
                            (inputMessage.arg2.toLong() and 0xffffffffL)
                    encoder.handleFrameAvailable(obj as FloatArray, timestamp)
                }
                MSG_SET_TEXTURE_ID -> encoder.handleSetTexture(inputMessage.arg1)
                MSG_UPDATE_SHARED_CONTEXT -> encoder.handleUpdateSharedContext(inputMessage.obj as EGLContext)
                MSG_QUIT -> Looper.myLooper()!!.quit()
                MSG_PAUSE_RECORDING -> encoder.handlePauseRecording()
                MSG_RESUME_RECORDING -> encoder.handleResumeRecording()
                else -> throw RuntimeException("Unhandled msg what=$what")
            }
        }

    }

    /**
     * Starts recording.
     */
    protected fun handleStartRecording(config: VideoEncoderConfig) {
        Log.d(TAG, "handleStartRecording $config")
        frameNum = 0
        prepareEncoder(config)
    }

    protected fun handleResumeRecording() {
        if (videoEncoder!!.pauseResumeSupported) {
            videoEncoder!!.resume()
        } else {
            stopRecording()
        }
    }

    /**
     * Handles notification of an available frame.
     *
     *
     * The texture is rendered onto the encoder's input surface, along with a moving
     * box (just because we can).
     *
     *
     * @param transform The texture transform, from SurfaceTexture.
     * @param timestampNanos The frame's timestamp, from SurfaceTexture.
     */
    protected abstract fun handleFrameAvailable(transform: FloatArray, timestampNanos: Long)

    /**
     * Handles a request to stop encoding.
     */
    protected open fun handleStopRecording() {
        Log.d(TAG, "handleStopRecording")
        videoEncoder!!.stop()
        releaseEncoder()
    }

    protected fun handlePauseRecording() {
        if (videoEncoder!!.pauseResumeSupported) {
            videoEncoder!!.pause()
        } else {
            stopRecording()
        }
    }

    /**
     * Sets the texture name that SurfaceTexture will use when frames are received.
     */
    protected fun handleSetTexture(id: Int) {
        //Log.d(TAG, "handleSetTexture " + id);
        textureId = id
    }

    /**
     * Tears down the EGL surface and context we've been using to feed the MediaCodec input
     * surface, and replaces it with a new one that shares with the new context.
     *
     *
     * This is useful if the old context we were sharing with went away (maybe a GLSurfaceView
     * that got torn down) and we need to hook up with the new one.
     */
    protected fun handleUpdateSharedContext(newSharedContext: EGLContext) {
        Log.d(TAG, "handleUpdatedSharedContext $newSharedContext")

        // Release the EGLSurface and EGLContext.
        inputWindowSurface?.releaseEglSurface()
        fullScreen?.release(false)
        eglCore?.release()

        // Create a new EGLContext and recreate the window surface.
        eglCore = EglCore(newSharedContext, EglCore.FLAG_RECORDABLE)
        inputWindowSurface?.recreate(eglCore)
        inputWindowSurface?.makeCurrent()

        // Create new programs and such for the new context.
        fullScreen = FullFrameRect(
                Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT))
    }

    @Throws(IllegalStateException::class, IOException::class)
    protected abstract fun createEncoder(config: VideoEncoderConfig): EncoderCore
    private fun prepareEncoder(config: VideoEncoderConfig) {
        videoEncoder = try {
            createEncoder(config)
        } catch (e: IllegalStateException) {
            onRecordingFailed(e)
            throw RuntimeException(e)
        } catch (e: IOException) {
            onRecordingFailed(e)
            throw RuntimeException(e)
        }
        eglCore = EglCore(config.eglContext, EglCore.FLAG_RECORDABLE)
        inputWindowSurface = WindowSurface(eglCore, videoEncoder!!.inputSurface, true)
        inputWindowSurface!!.makeCurrent()
        fullScreen = FullFrameRect(Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT))
    }

    protected fun releaseEncoder() {
        videoEncoder!!.release()
        if (inputWindowSurface != null) {
            inputWindowSurface!!.release()
            inputWindowSurface = null
        }
        if (fullScreen != null) {
            fullScreen!!.release(false)
            fullScreen = null
        }
        if (eglCore != null) {
            eglCore!!.release()
            eglCore = null
        }
    }

    /**
     * Draws a box, with position offset.
     */
    protected fun drawBox(posn: Int) {
        val width = inputWindowSurface!!.width
        val xpos = posn * 4 % (width - 50)
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST)
        GLES20.glScissor(xpos, 0, 100, 100)
        GLES20.glClearColor(1.0f, 0.0f, 1.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
    }

    override fun onRecordingStarted() {
        val msg = encoderStateHandler.obtainMessage(EncoderStateHandler.MSG_RECORDING_STARTED)
        encoderStateHandler.sendMessage(msg)
    }

    override fun onRecordingResumed() {
        val msg = encoderStateHandler.obtainMessage(EncoderStateHandler.MSG_RECORDING_RESUMED)
        encoderStateHandler.sendMessage(msg)
    }

    override fun onRecordingPaused() {
        val msg = encoderStateHandler.obtainMessage(EncoderStateHandler.MSG_RECORDING_PAUSED)
        encoderStateHandler.sendMessage(msg)
    }

    override fun onRecordingStopped() {
        val msg = encoderStateHandler.obtainMessage(EncoderStateHandler.MSG_RECORDING_STOPPED)
        encoderStateHandler.sendMessage(msg)
    }

    override fun onRecordingFailed(t: Throwable) {
        val msg = encoderStateHandler.obtainMessage(EncoderStateHandler.MSG_RECORDING_FAILED, t)
        encoderStateHandler.sendMessage(msg)
    }

    companion object {
        const val TAG = Utils.TAG
        val VERBOSE = BuildConfig.DEBUG
        protected const val MSG_START_RECORDING = 0
        protected const val MSG_STOP_RECORDING = 1
        protected const val MSG_FRAME_AVAILABLE = 2
        protected const val MSG_SET_TEXTURE_ID = 3
        protected const val MSG_UPDATE_SHARED_CONTEXT = 4
        protected const val MSG_QUIT = 5
        protected const val MSG_PAUSE_RECORDING = 6
        protected const val MSG_RESUME_RECORDING = 7
    }

}