package com.app.testvideocall

import android.app.Application

class AppApplication : Application() {

    val WS = "ws://47.93.186.97:5000/ws"
    override fun onCreate() {
        super.onCreate()
//        SocketManager.getInstance()?.connect(WS, "userId", 0)
    }
}