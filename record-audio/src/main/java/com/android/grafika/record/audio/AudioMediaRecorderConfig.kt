package com.android.grafika.record.audio

import android.annotation.SuppressLint
import android.media.MediaRecorder
import androidx.core.util.Preconditions
import java.io.File

class AudioMediaRecorderConfig(
        config: AudioMediaRecorderConfig? = DEFAULT
) {

    var audioSamplingRate: Int = config?.audioSamplingRate ?: 0
        private set
    var audioBitRate: Int = config?.audioBitRate ?: 0
        private set
    var outputFile: File? = config?.outputFile
        private set
    var audioSource: Int = config?.audioSource ?: -1
        private set
    var audioEncoder: Int = config?.audioEncoder ?: -1

    fun audioSamplingRate(block: () -> Int) = apply {
        this.audioSamplingRate = block()
    }

    /**
     * Be careful with setting the audio bit rate. it depends on the audio encoder codec
     * and a wrong value may produce audio stuttering on different devices.
     *
     * If you don't know what you're doing, like me,
     * let MediaRecorder pick an audioBitRate.
     */
    fun audioBitRate(block: () -> Int) = apply {
        this.audioBitRate = block()
    }

    fun outputFile(block: () -> File) = apply {
        this.outputFile = block()
    }

    fun audioSource(block: () -> Int) = apply {
        this.audioSource = block()
    }

    fun audioEncoder(block: () -> Int) = apply {
        this.audioEncoder = block()
    }

    @SuppressLint("RestrictedApi")
    internal fun preconditions() = apply {
        Preconditions.checkArgument(audioSamplingRate != 0, "audioSamplingRate == 0")
        Preconditions.checkArgument(audioBitRate != 0, "audioBitRate == 0")
        Preconditions.checkArgument(audioSource != -1, "audioSource invalid:" +
                "must be a value from `MediaRecorder.AudioSource`")
        Preconditions.checkArgument(audioEncoder != -1, "audioEncoder invalid:" +
                "must be a value from `MediaRecorder.AudioEncoder`")
        Preconditions.checkNotNull(outputFile, "outputFile == null")
    }

    companion object {
        private const val DEFAULT_AUDIO_BIT_RATE = 24 * 1000 //48kbps
        private const val DEFAULT_AUDIO_SAMPLING_RATE = 44100
        private const val DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.CAMCORDER
        private const val DEFAULT_AUDIO_ENCODER = MediaRecorder.AudioEncoder.HE_AAC
        private val DEFAULT = AudioMediaRecorderConfig()
                .audioBitRate { DEFAULT_AUDIO_BIT_RATE }
                .audioSamplingRate { DEFAULT_AUDIO_SAMPLING_RATE }
                .audioSource { DEFAULT_AUDIO_SOURCE }
                .audioEncoder { DEFAULT_AUDIO_ENCODER }
    }
}
