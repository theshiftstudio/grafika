package com.android.grafika.videoencoder.muxer.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.util.Log
import android.view.Surface
import com.android.grafika.videoencoder.EncoderStateCallback
import com.android.grafika.videoencoder.VideoEncoderConfig
import com.android.grafika.videoencoder.muxer.audio.MediaEncoder
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean


open class MediaVideoEncoder(
        config: VideoEncoderConfig,
        private val pauseResumeSupported: Boolean,
        private val encoderStateCallback: EncoderStateCallback,
        muxerCallback: MediaEncoder.MediaMuxerCallback
) {

    /**
    * Returns the encoder's input surface.
    */
    val inputSurface: Surface
    private var encoder: MediaCodec?
    private val bufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
    private var trackIndex: Int
    private var muxerStarted = AtomicBoolean()

    private val muxerCallback = WeakReference(muxerCallback)

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

        trackIndex = -1
        muxerStarted.set(false)

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
    fun drainEncoder(endStream: Boolean) {
        if (VERBOSE) Log.d(TAG, "drainEncoder($endStream)")
        if (endStream) {
            if (VERBOSE) Log.d(TAG, "sending EOS to encoder")
            encoder!!.signalEndOfInputStream()
        }
        if (muxerCallback.get() == null) {
            if (VERBOSE) Log.d(TAG, "MuxerCallback == null")
            return;
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
                muxerCallback.get()?.addTrack(newFormat)
                        ?.let { trackIndex = it }
                        ?: return
                muxerCallback.get()?.startMuxer()
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
                    // write encoded data to muxer(need to adjust presentationTimeUs.
                    bufferInfo.presentationTimeUs = getPTSUs()
                    muxerCallback.get()?.writeSampleData(trackIndex, encodedData, bufferInfo)
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

    fun release() {
        encoder?.apply {
            stop()
            release()
        }
        encoder = null
    }

    fun pause() {
        encoder?.takeIf { pauseResumeSupported }
                ?.let {
                    val params = Bundle()
                    params.putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 1)
                    encoder!!.setParameters(params)
                    encoderStateCallback.onRecordingPaused()
                }
    }

    fun resume() {
        if (encoder != null && pauseResumeSupported) {
            val params = Bundle()
            params.putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 0)
            encoder!!.setParameters(params)
            encoderStateCallback.onRecordingResumed()
        }
    }

    /**
     * previous presentationTimeUs for writing
     */
    private val prevOutputPTSUs: Long = 0

    /**
     * get next encoding presentationTimeUs
     * @return
     */
    private fun getPTSUs(): Long {
        var result = System.nanoTime() / 1000L
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < prevOutputPTSUs) result += prevOutputPTSUs - result
        return result
    }
    
    companion object {
        private val TAG = MediaVideoEncoder::class.java.simpleName
        private const val  VERBOSE = false

        // TODO: these ought to be configurable as well
        private const val MIME_TYPE = "video/avc" // H.264 Advanced Video Coding
        private const val FRAME_RATE = 30 // 30fps
        private const val IFRAME_INTERVAL = 5 // 5 seconds between I-frames
        private val TIMEOUT_USEC = 10000
    }
}