package com.potato.liftinsight.common.logging

import android.util.Log

interface AppLogger {
    fun trace(tag: String, message: String)

    fun debug(tag: String, message: String)

    fun info(tag: String, message: String)

    fun warn(tag: String, message: String)

    fun error(tag: String, message: String, throwable: Throwable? = null)
}

object AndroidAppLogger : AppLogger {
    override fun trace(tag: String, message: String) {
        Log.v(tag, message)
    }

    override fun debug(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun info(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun warn(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun error(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
