package com.app.testvideocall

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext

class SignallingClient(
    private val listener: SignallingClientListener
) : CoroutineScope {

    private val job = Job()

    private val gson = Gson()

    private val sendChannel = ConflatedBroadcastChannel<String>()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job


    fun send(dataObject: Any?) = runBlocking {
        sendChannel.send(gson.toJson(dataObject))
    }


}