package com.pedro.streamer.utils

import android.util.Log
import com.pedro.streamer.BuildConfig


object Logger {
    private const val TAG = "HUANG"
    private const val ERROR = "ANDROID_ERROR:"
    private const val WARN = "ANDROID_WARN:"
    private const val RUNTIME_EXCEPTION = "ANDROID_RUNTIME_EXCEPTION"
//    private val DEBUG = BuildConfig.DEBUG
    private val DEBUG = true

    @JvmStatic
    fun v(subTag: String, message: String){
        if (DEBUG) {
            Log.v(TAG, "[$subTag][Thread: ${Thread.currentThread().name}] $message")
        }
    }
    @JvmStatic
    fun d(subTag: String, message: String){
        if (DEBUG) {
            Log.d(TAG, "[$subTag][Thread: ${Thread.currentThread().name}] $message")
        }
    }
    @JvmStatic
    fun i(subTag: String, message: String){
        if (DEBUG) {
            Log.i(TAG, "[$subTag][Thread: ${Thread.currentThread().name}] $message")
        }
    }
    @JvmStatic
    fun w(subTag: String, message: String){
        if (DEBUG) {
            Log.w(TAG, "[$subTag][Thread: ${Thread.currentThread().name}] $WARN $message")
        }
    }
    @JvmStatic
    fun e(subTag: String, message: String){
        if (DEBUG) {
            Log.e(TAG, "[$subTag][Thread: ${Thread.currentThread().name}] $ERROR $message")
        }
    }

    @JvmStatic
    fun throwRuntimeException(){
        RuntimeException(TAG).printStackTrace()
    }

    @JvmStatic
    fun printStackTrace(tag: String){
        //打印堆栈而不退出
        d(tag, Log.getStackTraceString(Throwable()))

        Exception("debug log").printStackTrace()

        for (i in Thread.currentThread().stackTrace){
            i(tag, i.toString())
        }
        val runtimeException = RuntimeException()
        runtimeException.fillInStackTrace()

        try {
            i(tag, "----------------------throw NullPointerException----------------------")
            throw NullPointerException()
        } catch (nullPointer: NullPointerException) {
            i(tag, "----------------------catch NullPointerException----------------------")
            e(tag, Log.getStackTraceString(nullPointer))
        }
        i(tag, "----------------------end NullPointerException ----------------------")
    }
}