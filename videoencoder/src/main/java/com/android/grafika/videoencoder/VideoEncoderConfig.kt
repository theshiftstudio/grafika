package com.android.grafika.videoencoder

import android.annotation.SuppressLint
import android.opengl.EGL14
import android.opengl.EGLContext
import android.view.Surface
import androidx.core.util.Preconditions
import java.io.File

class VideoEncoderConfig {

    internal var videoBitRate: Int = 0
        private set
    internal var audioBitRate: Int = 0
        private set
    internal var frameRate: Int = 0
        private set
    internal var width: Int = 0
        private set
    internal var height: Int = 0
        private set
    internal var outputFile: File? = null
        private set
    internal var inputSurface: Surface? = null
        private set
    internal var eglContext: EGLContext? = EGL14.eglGetCurrentContext()
        private set

    fun width(block: () -> Int) = apply {
        this.width = block()
    }

    fun height(block: () -> Int) = apply {
        this.height = block()
    }

    fun videoBitRate(block: () -> Int) = apply {
        this.videoBitRate = block()
    }

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

    @SuppressLint("RestrictedApi")
    internal fun preconditions() = apply {
        Preconditions.checkArgument(width != 0, "width == 0")
        Preconditions.checkArgument(height != 0, "height == 0")
        Preconditions.checkArgument(frameRate > 0, "frameRate <= 0")
        Preconditions.checkArgument(videoBitRate != 0, "height == 0")
        Preconditions.checkArgument(audioBitRate != 0, "height == 0")
        Preconditions.checkNotNull(outputFile, "outputFile == null")
    }

    companion object {
        const val DEFAULT_VIDEO_BIT_RATE = 1080 * 1000 //1080kbps
        const val DEFAULT_AUDIO_BIT_RATE = 128 * 1000 //128kbps
        const val DEFAULT_FRAME_RATE = 30 //30FPS
        const val DEFAULT_VIDEO_WIDTH = 1080
        const val DEFAULT_VIDEO_HEIGHT = 1920
    }

}