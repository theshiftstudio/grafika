package com.android.grafika.videoencoder

import android.graphics.SurfaceTexture
import android.opengl.EGLContext
import com.android.grafika.videoencoder.muxer.video.VideoEncoderConfig


interface Recorder {

    var isRecording: Boolean

    fun startRecording(config: VideoEncoderConfig?)

    fun resumeRecording()

    fun pauseRecording()

    fun stopRecording()

    fun updateTextureId(textureId: Int)

    fun frameAvailable(surfaceTexture: SurfaceTexture)

    fun updateSharedContext(eglContext: EGLContext)

}