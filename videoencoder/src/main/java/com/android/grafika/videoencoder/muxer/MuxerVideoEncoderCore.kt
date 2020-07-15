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

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
import android.os.Bundle
import android.util.Log
import android.view.Surface
import com.android.grafika.videoencoder.EncoderCore
import com.android.grafika.videoencoder.EncoderStateCallback
import com.android.grafika.videoencoder.EncoderStateCallback.Companion.EMPTY
import com.android.grafika.videoencoder.VideoEncoderConfig
import com.android.grafika.videoencoder.audio.AudioRecorder
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
) : EncoderCore {


    /**
     * Returns the encoder's input surface.
     */
    override val inputSurface: Surface
    private var mediaMuxer: MediaMuxer?
    private var encoder: MediaCodec?
    private var audioRecorder: AudioRecorder?
    private val bufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
    private var trackIndex: Int
    private var muxerStarted = AtomicBoolean()

    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    init {
        val format = MediaFormat.createVideoFormat(
                MIME_TYPE,
                config.width,
                config.height
        )

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        if (VERBOSE) Log.d(TAG, "format: $format")

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        encoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = encoder!!.createInputSurface()
        encoder!!.start()

        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        mediaMuxer = MediaMuxer(config.outputFile.toString(), MUXER_OUTPUT_MPEG_4)
        trackIndex = -1
        muxerStarted.set(false)

        audioRecorder = AudioRecorder(config)
    }

    override fun start() {
        mediaMuxer?.start()
        audioRecorder?.start()
        encoderStateCallback.onRecordingStarted()
    }

    /**
     * Releases encoder resources.
     */
    override fun release() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects")
        encoder?.apply {
            stop()
            release()
        }
        encoder = null
        mediaMuxer?.apply {
            // TODO: stop() throws an exception if you haven't fed it any data.  Keep track
            //       of frames submitted, and don't call stop() if we haven't written anything.
            stop()
            release()
        }
        mediaMuxer = null
        audioRecorder?.apply {
            stop()
            release()
        }
        audioRecorder = null
    }

    override fun pause() {
        encoder?.takeIf { pauseResumeSupported }
                ?.let {
                    val params = Bundle()
                    params.putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 1)
                    encoder!!.setParameters(params)
                    encoderStateCallback.onRecordingPaused()
                }
    }

    override fun resume() {
        if (encoder != null && pauseResumeSupported) {
            val params = Bundle()
            params.putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 0)
            encoder!!.setParameters(params)
            encoderStateCallback.onRecordingResumed()
        }
    }

    override fun stop() {
        drainEncoder(true)
    }

    /**
     * Extracts all pending data from the encoder and forwards it to the muxer.
     *
     *
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     *
     *
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
     * not recording audio.
     */
    @Suppress("DEPRECATION")
    override fun drainEncoder(endStream: Boolean) {
        drainVideoEncoder(endStream)
        drainAudioEncoder()
    }

    private fun drainAudioEncoder() {
        audioRecorder?.drainEncoder()
    }

    private fun drainVideoEncoder(endStream: Boolean) {
        if (VERBOSE) Log.d(TAG, "drainEncoder($endStream)")
        if (endStream) {
            if (VERBOSE) Log.d(TAG, "sending EOS to encoder")
            encoder!!.signalEndOfInputStream()
        }
        var encoderOutputBuffers = encoder!!.outputBuffers
        while (true) {
            val encoderStatus = encoder!!.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC.toLong())
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endStream) {
                    break // out of while
                } else {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS")
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = encoder!!.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (muxerStarted.get()) {
                    throw RuntimeException("format changed twice")
                }
                val newFormat = encoder!!.outputFormat
                Log.d(TAG, "encoder output format changed: $newFormat")

                // now that we have the Magic Goodies, start the muxer
                trackIndex = mediaMuxer!!.addTrack(newFormat)
                start()
                muxerStarted.set(true)
            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus)
                // let's ignore it
            } else {
                val encodedData = encoderOutputBuffers[encoderStatus]
                        ?: throw RuntimeException("encoderOutputBuffer " + encoderStatus +
                                " was null")
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
                    bufferInfo.size = 0
                }
                if (bufferInfo.size != 0) {
                    if (!muxerStarted.get()) {
                        throw RuntimeException("muxer hasn't started")
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    mediaMuxer!!.writeSampleData(trackIndex, encodedData, bufferInfo)
                    if (VERBOSE) {
                        Log.d(TAG, "sent " + bufferInfo.size + " bytes to muxer, ts=" +
                                bufferInfo.presentationTimeUs)
                    }
                }
                encoder!!.releaseOutputBuffer(encoderStatus, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (!endStream) {
                        Log.w(TAG, "reached end of stream unexpectedly")
                    } else {
                        if (VERBOSE) Log.d(TAG, "end of stream reached")
                    }
                    encoderStateCallback.onRecordingStopped()
                    break // out of while
                }
            }
        }
    }

    override val pauseResumeSupported: Boolean
        get() = false

    companion object {
        private val TAG = MuxerVideoEncoderCore::class.java.simpleName
        private const val  VERBOSE = false

        // TODO: these ought to be configurable as well
        private const val MIME_TYPE = "video/avc" // H.264 Advanced Video Coding
        private const val FRAME_RATE = 30 // 30fps
        private const val IFRAME_INTERVAL = 5 // 5 seconds between I-frames
        private val TIMEOUT_USEC = 10000
    }
}