package com.android.grafika.videoencoder.muxer


interface MuxerCallback {

    fun onEncoderReleased(trackIndex: Int)

}