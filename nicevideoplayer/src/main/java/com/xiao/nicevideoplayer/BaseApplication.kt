package com.xiao.nicevideoplayer

import android.app.Application

class BaseApplication : Application() {

    companion object {
        private lateinit var application: Application

        fun getApplication() = application
    }

    override fun onCreate() {
        super.onCreate()
        application = this
    }

}