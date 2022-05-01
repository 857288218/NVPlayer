package com.xiao.nicevideoplayer.utils;

import android.util.Log;

import com.xiao.nicevideoplayer.BuildConfig;

public class LogUtil {

    private static final String TAG = "NiceVideoPlayer";

    public static void d(String message) {
        if (BuildConfig.DEBUG)
            Log.d(TAG, message);
    }

    public static void i(String message) {
        if (BuildConfig.DEBUG)
            Log.i(TAG, message);
    }

    public static void e(String message, Throwable throwable) {
        if (BuildConfig.DEBUG)
            Log.e(TAG, message, throwable);
    }
}
