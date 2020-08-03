package com.android.grafika.core

import android.os.Build


object Utils {

    const val TAG = "Grafika"

    fun isKitKat() = Build.VERSION.SDK_INT >= 19

}