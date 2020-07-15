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
package com.android.grafika.videoencoder.muxer

import android.util.Log
import com.android.grafika.videoencoder.BaseVideoEncoder
import com.android.grafika.videoencoder.EncoderCore
import com.android.grafika.videoencoder.EncoderStateHandler
import com.android.grafika.videoencoder.VideoEncoderConfig
import com.android.grafika.videoencoder.audio.AudioRecorder
import java.io.IOException
import java.util.*

class MuxerVideoEncoder(
        encoderStateHandler: EncoderStateHandler
) : BaseVideoEncoder(encoderStateHandler) {

    init {
        if (VERBOSE) Log.d(TAG, "Using MuxerVideoEncoder!!!")
    }

    override fun handleFrameAvailable(transform: FloatArray, timestampNanos: Long) {
        if (VERBOSE) Log.d(TAG, "handleFrameAvailable tr=" + transform.contentToString())
        videoEncoder?.drainEncoder(false)
        fullScreen?.drawFrame(textureId, transform)
        if (VERBOSE) {
            drawBox(frameNum++)
        }
        inputWindowSurface?.setPresentationTime(timestampNanos)
        inputWindowSurface?.swapBuffers()
    }

    @Throws(IllegalStateException::class, IOException::class)
    override fun createEncoder(config: VideoEncoderConfig): EncoderCore {
        return MuxerVideoEncoderCore(config, this)
    }

    override fun handleStopRecording() {
        super.handleStopRecording()
    }
}