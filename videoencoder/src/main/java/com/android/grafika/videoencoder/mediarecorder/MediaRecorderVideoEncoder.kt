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
package com.android.grafika.videoencoder.mediarecorder

import android.media.MediaCodec
import android.opengl.EGLContext
import android.util.Log
import com.android.grafika.videoencoder.BaseVideoEncoder
import com.android.grafika.videoencoder.EncoderCore
import com.android.grafika.videoencoder.VideoEncoderConfig
import java.io.File
import java.io.IOException

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
class MediaRecorderVideoEncoder : BaseVideoEncoder(), Runnable {

    override fun handleFrameAvailable(transform: FloatArray, timestampNanos: Long) {
        if (VERBOSE) Log.d(TAG, "handleFrameAvailable tr=$transform")
        mFullScreen.drawFrame(mTextureId, transform)
        if (VERBOSE) drawBox(mFrameNum++)
        mInputWindowSurface.swapBuffers()
    }

    @Throws(IllegalStateException::class, IOException::class)
    override fun createEncoder(videoEncoderConfig: VideoEncoderConfig): EncoderCore {
        return videoEncoderConfig
                .inputSurface { MediaCodec.createPersistentInputSurface() }
                .buildInputSurface()
                .buildMediaRecorderEncoderCore()
                .prepare()
                .also {
                    it.start()
                    mVideoEncoder = it
                }
    }
}