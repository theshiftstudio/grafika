package com.android.grafika.videoencoder

import android.annotation.SuppressLint
import android.media.MediaRecorder
import android.opengl.EGL14
import android.opengl.EGLContext
import android.view.Surface
import androidx.core.util.Preconditions
import java.io.File

class VideoEncoderConfig(config: VideoEncoderConfig? = null) {

    var videoBitRate: Int = config?.videoBitRate ?: 0
        private set
    var audioBitRate: Int = config?.audioBitRate ?: 0
        private set
    var frameRate: Int = config?.frameRate ?: 0
        private set
    var width: Int = config?.width ?: 0
        private set
    var height: Int = config?.height ?: 0
        private set
    var outputFile: File? = config?.outputFile
        private set
    internal var inputSurface: Surface? = config?.inputSurface
        private set
    internal var eglContext: EGLContext? = EGL14.eglGetCurrentContext()
        private set
    var audioSource: Int = config?.audioSource ?: -1
        private set
    var audioEncoder: Int = config?.audioEncoder ?: -1

    fun width(block: () -> Int) = apply {
        this.width = block()
    }

    fun height(block: () -> Int) = apply {
        this.height = block()
    }

    fun videoBitRate(block: () -> Int) = apply {
        this.videoBitRate = block()
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

    fun frameRate(block: () -> Int) = apply {
        this.frameRate = block()
    }

    fun outputFile(block: () -> File) = apply {
        this.outputFile = block()
    }

    fun inputSurface(block: () -> Surface) = apply {
        this.inputSurface = block()
    }

    fun audioSource(block: () -> Int) = apply {
        this.audioSource = block()
    }

    fun audioEncoder(block: () -> Int) = apply {
        this.audioEncoder = block()
    }

    @SuppressLint("RestrictedApi")
    internal fun preconditions() = apply {
        Preconditions.checkArgument(width != 0, "width == 0")
        Preconditions.checkArgument(height != 0, "height == 0")
        Preconditions.checkArgument(frameRate > 0, "frameRate <= 0")
        Preconditions.checkArgument(videoBitRate != 0, "height == 0")
        Preconditions.checkArgument(audioBitRate != 0, "height == 0")
        Preconditions.checkArgument(audioSource != -1, "audioSource invalid:" +
                "must be a value from `MediaRecorder.AudioSource`")
        Preconditions.checkArgument(audioEncoder != -1, "audioEncoder invalid:" +
                "must be a value from `MediaRecorder.AudioEncoder`")
        Preconditions.checkNotNull(outputFile, "outputFile == null")
    }

    companion object {
        const val NULL_VALUE = -1
        const val DEFAULT_VIDEO_BIT_RATE = 1080 * 1000 //1080kbps
        const val DEFAULT_AUDIO_BIT_RATE = 24 * 1000 //48kbps
        const val DEFAULT_FRAME_RATE = 30 //30FPS
        const val DEFAULT_VIDEO_WIDTH = 1080
        const val DEFAULT_VIDEO_HEIGHT = 1920
        const val DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.CAMCORDER
        const val DEFAULT_AUDIO_ENCODER = MediaRecorder.AudioEncoder.HE_AAC
        val DEFAULT = VideoEncoderConfig()
                .width { DEFAULT_VIDEO_WIDTH }
                .height { DEFAULT_VIDEO_HEIGHT }
                .videoBitRate { DEFAULT_VIDEO_BIT_RATE }
                .audioBitRate { NULL_VALUE }
                .frameRate { DEFAULT_FRAME_RATE }
                .audioSource { DEFAULT_AUDIO_SOURCE }
                .audioEncoder { DEFAULT_AUDIO_ENCODER }
    }

}