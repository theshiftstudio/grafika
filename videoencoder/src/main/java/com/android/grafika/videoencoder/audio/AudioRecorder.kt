package com.android.grafika.videoencoder.audio

import android.media.AudioFormat
import android.media.AudioRecord
import com.android.grafika.videoencoder.VideoEncoderConfig
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean


class AudioRecorder(
        private val config: VideoEncoderConfig
) {

    private val outputFile: File = File(config.outputFile?.absolutePath + ".mp3")
    private val outputStream = FileOutputStream(outputFile)
    private val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)
    private var audioRecorder: AudioRecord


    init {
        audioRecorder = AudioRecord(VideoEncoderConfig.DEFAULT_AUDIO_SOURCE,
                SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE)
    }

    fun start() {
        audioRecorder.startRecording()
    }

    fun stop() {
        audioRecorder.stop()
    }

    fun release() {
        audioRecorder.release()
    }

    fun drainEncoder() {
        audioRecorder.read(buffer, BUFFER_SIZE)
                .takeIf { it < 0 }
                ?.let {
                    val reason = getBufferReadFailureReason(it)
                    throw RuntimeException("Audio recording failed: $reason")
                }
        outputStream.write(buffer.array(), 0, BUFFER_SIZE)
        buffer.clear()
    }

    private fun getBufferReadFailureReason(errorCode: Int): String = when (errorCode) {
        AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
        AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
        AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
        AudioRecord.ERROR -> "ERROR"
        else -> "Unknow ($errorCode)"
    }

    companion object {
        private const val SAMPLING_RATE_IN_HZ = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        private const val RECORDER_BPP = 16
        private const val AUDIO_RECORDER_FILE_EXT_WAV = "AudioRecorder.wav"
        private const val AUDIO_RECORDER_FOLDER = "AudioRecorder"
        private const val AUDIO_RECORDER_TEMP_FILE = "record_temp.raw"
        private const val RECORDER_SAMPLERATE = 8000
        private const val RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO
        private const val RECORDER_CHANNELS_INT = 1

        private const val RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /**
         * Factor by that the minimum buffer size is multiplied. The bigger the factor is the less
         * likely it is that samples will be dropped, but more memory will be used. The minimum buffer
         * size is determined by [AudioRecord.getMinBufferSize] and depends on the
         * recording settings.
         */
        private const val BUFFER_SIZE_FACTOR = 2

        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
                SAMPLING_RATE_IN_HZ, CHANNEL_CONFIG, AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR
    }

}