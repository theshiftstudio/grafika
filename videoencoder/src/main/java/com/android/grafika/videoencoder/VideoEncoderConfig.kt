package com.android.grafika.videoencoder

import android.annotation.SuppressLint
import android.opengl.EGL14
import android.opengl.EGLContext
import android.view.Surface
import androidx.core.util.Preconditions
import com.android.grafika.videoencoder.mediarecorder.MediaRecorderEncoderCore
import com.android.grafika.videoencoder.muxer.MuxerVideoEncoderCore
import java.io.File

class VideoEncoderConfig(config: VideoEncoderConfig? = null) {

    var videoBitRate: Int = config?.videoBitRate ?: 1080 * 1000// 1080kbps
        private set
    var audioBitRate: Int = config?.audioBitRate ?: 128 * 1000 // 128kbps
        private set
    var frameRate: Int = config?.frameRate ?: 30
        private set
    var width: Int = config?.width ?: 0
        private set
    var height: Int = config?.height ?: 0
        private set
    var outputFile: File? = config?.outputFile
        private set
    var inputSurface: Surface? = config?.inputSurface
        private set
    var eglContext: EGLContext? = config?.eglContext ?: EGL14.eglGetCurrentContext()
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
    fun preconditions() = apply {
        Preconditions.checkArgument(width != 0, "width == 0")
        Preconditions.checkArgument(height != 0, "height == 0")
        Preconditions.checkNotNull(outputFile, "outputFile == null")
        Preconditions.checkArgument(frameRate > 0, "frameRate <= 0")
    }

}