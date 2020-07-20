/*
 * Copyright 2014 Google Inc. All rights reserved.
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

import android.media.*
import android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
import android.os.Bundle
import android.util.Log
import android.view.Surface
import com.android.grafika.videoencoder.EncoderCore
import com.android.grafika.videoencoder.EncoderStateCallback
import com.android.grafika.videoencoder.EncoderStateCallback.Companion.EMPTY
import com.android.grafika.videoencoder.VideoEncoderConfig
import com.android.grafika.videoencoder.muxer.audio.MediaAudioEncoder
import com.android.grafika.videoencoder.muxer.audio.MediaEncoder
import com.android.grafika.videoencoder.muxer.video.MediaVideoEncoder
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class wraps up the core components used for surface-input video encoding.
 *
 *
 * Once created, frames are fed to the input surface.  Remember to provide the presentation
 * time stamp, and always call drainEncoder() before swapBuffers() to ensure that the
 * producer side doesn't get backed up.
 *
 *
 * This class is not thread-safe, with one exception: it is valid to use the input surface
 * on one thread, and drain the output on a different thread.
 */
class MuxerVideoEncoderCore(
        config: VideoEncoderConfig,
        private val encoderStateCallback: EncoderStateCallback = EMPTY
) : EncoderCore, MediaEncoder.MediaMuxerCallback, MediaEncoder.MediaEncoderListener {

    override val inputSurface: Surface?
        get() = videoEncoder?.inputSurface

    private var muxerStarted = AtomicBoolean()

    private var mediaMuxer: MediaMuxer?
    private val audioEncoder : MediaAudioEncoder?
    private val videoEncoder : MediaVideoEncoder?

    private val countDownLatch = CountDownLatch(2)

    init {
        mediaMuxer = MediaMuxer(config.outputFile.toString(), MUXER_OUTPUT_MPEG_4)
        audioEncoder = MediaAudioEncoder(this@MuxerVideoEncoderCore, this@MuxerVideoEncoderCore).apply {
            prepare()
            startRecording()
        }
        videoEncoder = MediaVideoEncoder(config, pauseResumeSupported, encoderStateCallback, this@MuxerVideoEncoderCore)
    }

    override fun start() = synchronized(this) {
        if (muxerStarted.get().not()) {
            muxerStarted.set(true)
            mediaMuxer?.start()
            encoderStateCallback.onRecordingStarted()
        }
    }

    /**
     * Releases encoder resources.
     */
    override fun release() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects")
        videoEncoder?.release()
        mediaMuxer?.apply {
            // TODO: stop() throws an exception if you haven't fed it any data.  Keep track
            //       of frames submitted, and don't call stop() if we haven't written anything.
            stop()
            release()
        }
        mediaMuxer = null
    }

    override fun pause() {
       videoEncoder?.pause()
    }

    override fun resume() {
        videoEncoder?.resume()
    }

    override fun stop() {
        audioEncoder?.stopRecording()
        drainEncoder(true)
    }

    override fun drainEncoder(endStream: Boolean) {
        videoEncoder?.drainEncoder(endStream)
    }

    override val pauseResumeSupported: Boolean
        get() = false

    override fun onPrepared(encoder: MediaEncoder?) {
        synchronized(this) {
            Log.i(TAG, "${encoder?.javaClass?.simpleName} prepared")
        }
    }

    override fun onStopped(encoder: MediaEncoder?) {
        synchronized(this) {
            Log.i(TAG, "${encoder?.javaClass?.simpleName} stopped")
        }
    }

    override fun isMuxerStarted(): Boolean = muxerStarted.get()

    override fun addEncoder(encoder: MediaEncoder?) {
        synchronized(this) {
            Log.i(TAG, "${encoder?.javaClass?.simpleName} addEncoder")
        }
    }

    override fun writeSampleData(trackIndex: Int, byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        synchronized(this) {
            mediaMuxer?.writeSampleData(trackIndex, byteBuffer, bufferInfo)
        }
    }

    override fun addTrack(format: MediaFormat): Int = synchronized(this) {
        countDownLatch.countDown()
        mediaMuxer?.addTrack(format) ?: -1
    }

    override fun startMuxer(): Boolean {
        countDownLatch.await()
        start()
        return true
    }

    override fun stopMuxer() {
        synchronized(this) {
            stop()
        }
    }

    companion object {
        private val TAG = MuxerVideoEncoderCore::class.java.simpleName
        private const val  VERBOSE = false
    }
}