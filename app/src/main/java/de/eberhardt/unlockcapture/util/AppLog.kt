package de.eberhardt.unlockcapture.util

import android.util.Log

object AppLog {
    const val TAG = "UnlockCapture"

    fun d(component: String, message: String) {
        Log.d(TAG, "[$component] $message")
    }

    fun i(component: String, message: String) {
        Log.i(TAG, "[$component] $message")
    }

    fun w(component: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(TAG, "[$component] $message", throwable)
        } else {
            Log.w(TAG, "[$component] $message")
        }
    }

    fun e(component: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, "[$component] $message", throwable)
        } else {
            Log.e(TAG, "[$component] $message")
        }
    }
}

