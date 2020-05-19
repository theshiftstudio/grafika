package com.android.grafika.videoencoder

import android.annotation.SuppressLint
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.view.Surface
import androidx.core.util.Preconditions
import java.io.File
import java.io.IOException


class MediaRecorderVideoEncoderCore private constructor(
        width: Int,
        height: Int,
        videoBitRate: Int,
        audioBitRate: Int,
        frameRate: Int,
        outputFile: File,
        val inputSurface: Surface
) {

    private val mediaRecorder = MediaRecorder()
    private var isRecording: Boolean = false
    init {
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT)
//        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT)
//        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
//        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
//        mediaRecorder.setVideoSize(width, height)

        // Use the same size for recording profile.
        val profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P).apply {
            this.videoFrameWidth = width
            this.videoFrameHeight = height
        }
        mediaRecorder.setProfile(profile)

//        mediaRecorder.setVideoFrameRate(frameRate)
//        mediaRecorder.setVideoEncodingBitRate(videoBitRate)
//        mediaRecorder.setAudioEncodingBitRate(audioBitRate)
        mediaRecorder.setInputSurface(inputSurface)
        mediaRecorder.setOutputFile(outputFile.absolutePath)
    }

    @Throws(IOException::class, IllegalStateException::class)
    fun prepare(): MediaRecorderVideoEncoderCore = apply {
        mediaRecorder.prepare()
    }

    fun releaseMediaRecorder() {
        mediaRecorder.reset()
        mediaRecorder.release()
    }

    fun startRecording() = apply {
        mediaRecorder.start()
        isRecording = true
    }

    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder.resume()
        } else {

        }
    }

    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder.pause()
            isRecording = false
        } else {

        }
    }

    fun stopRecording() {
        mediaRecorder.stop()
        isRecording = false
    }

    class Builder {
        private var videoBitRate: Int = 1080 * 1000// 1080kbps
        private var audioBitRate: Int = 128 * 1000 // 128kbps
        private var frameRate: Int = 20
        private var width: Int = 0
        private var height: Int = 0
        private var outputFile: File? = null
        private var inputSurface: Surface? = null

        fun width(block: () -> Int): Builder = apply {
            this.width = block()
        }
        fun height(block: () -> Int): Builder = apply {
            this.height = block()
        }
        fun videoBitRate(block: () -> Int): Builder = apply {
            this.videoBitRate = block()
        }
        fun audioBitRate(block: () -> Int): Builder = apply {
            this.audioBitRate = block()
        }
        fun frameRate(block: () -> Int): Builder = apply {
            this.frameRate = block()
        }
        fun outputFile(block: () -> File): Builder = apply {
            this.outputFile = block()
        }
        fun inputSurface(block: () -> Surface): Builder = apply {
            this.inputSurface = block()
        }
        @SuppressLint("RestrictedApi")
        fun build(): MediaRecorderVideoEncoderCore {
            Preconditions.checkArgument(width != 0, "width == 0")
            Preconditions.checkArgument(height != 0, "height == 0")
            Preconditions.checkNotNull(outputFile, "outputFile == null")
            Preconditions.checkNotNull(inputSurface, "inputSurface == null")
            return MediaRecorderVideoEncoderCore(
                    width, height, videoBitRate, audioBitRate, frameRate, outputFile!!, inputSurface!!
            )
        }
    }


}