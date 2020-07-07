package com.app.testvideocall

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

class SocketManager : IEvent{

    private val TAG = "dds_SocketManager"
    private var webSocket: DWebSocket? = null
    private val userState = 0
    private val myId: String? = null

    private val handler = Handler(Looper.getMainLooper())

    companion object{
        fun getInstance(): SocketManager? {
            return Holder.socketManager
        }
    }

    private object Holder {
        val socketManager = SocketManager()
    }



    fun connect(url: String, userId: String, device: Int) {
        if (webSocket == null || !webSocket!!.isOpen()) {
            val uri: URI
            uri = try {
                val urls = "$url/$userId/$device"
                URI(urls)
            } catch (e: URISyntaxException) {
                e.printStackTrace()
                return
            }
            webSocket = DWebSocket(uri, this)
            // 设置wss
            if (url.startsWith("wss")) {
                try {
                    val sslContext =
                        SSLContext.getInstance("TLS")
                    sslContext?.init(
                        null,
                        arrayOf(TrustManagerTest()),
                        SecureRandom()
                    )
                    var factory: SSLSocketFactory? = null
                    if (sslContext != null) {
                        factory = sslContext.socketFactory
                    }
                    if (factory != null) {
                        webSocket!!.socket = factory.createSocket()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            // 开始connect
            webSocket!!.connect()
        }
    }

    fun unConnect() {
        if (webSocket != null) {
            webSocket!!.setConnectFlag(false)
            webSocket!!.close()
            webSocket = null
        }
    }

    override fun onOpen() {
//        sendMessageTest("User123")
    }

    override fun loginSuccess(userId: String?, avatar: String?) {
    }

    override fun onInvite(room: String?, audioOnly: Boolean, inviteId: String?, userList: String?) {
    }

    override fun onCancel(inviteId: String?) {
    }

    override fun onRing(userId: String?) {
    }

    override fun onPeers(myId: String?, userList: String?, roomSize: Int) {
    }

    override fun onNewPeer(myId: String?) {
    }

    override fun onReject(userId: String?, type: Int) {
    }

    override fun onOffer(userId: String?, sdp: String?) {
    }

    override fun onAnswer(userId: String?, sdp: String?) {
    }

    override fun onIceCandidate(userId: String?, id: String?, label: Int, candidate: String?) {
    }

    override fun onLeave(userId: String?) {
    }

    override fun logout(str: String?) {
    }

    override fun onTransAudio(userId: String?) {
    }

    override fun onDisConnect(userId: String?) {
    }

    override fun reConnect() {
    }

    fun sendMessage(text: String){
        if (webSocket != null) {
            Log.e("TEST_DATA","send message: $text")
            webSocket!!.send(text)
        }
    }

    // send answer
    fun sendAnswer(sdp: String) {
//        val map: MutableMap<String, Any> =
//            HashMap()
//        val childMap: MutableMap<String, Any> =
//            HashMap()
//        childMap["sdp"] = sdp
////        childMap["fromID"] = myId
////        childMap["userID"] = userId
////        map["data"] = childMap
//        map["type"] = "answer"
//        val `object` = JSONObject(map as Map<*, *>)
//        val jsonString: String = `object`.toString()
//        Log.e("TEST_DATA", "sendAnswer send-->$jsonString")
        val message = JSONObject()
        try {
            message.put("type", "answer") //SessionDescription.Type.ANSWER
            message.put("sdp", sdp)
            sendMessage(message.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
//        sendMessage(jsonString)
    }

    // send offer
    fun sendOffer(sdp: String) {
        val message = JSONObject()
        try {
            message.put("type", "offer") //SessionDescription.Type.OFFER
            message.put("sdp", sdp)
            sendMessage(message.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
//        sendMessage(jsonString)
    }


    class TrustManagerTest : X509TrustManager {
        @SuppressLint("TrustAllX509TrustManager")
        @Throws(CertificateException::class)
        override fun checkClientTrusted(
            chain: Array<X509Certificate>,
            authType: String
        ) {
        }

        @SuppressLint("TrustAllX509TrustManager")
        @Throws(CertificateException::class)
        override fun checkServerTrusted(
            chain: Array<X509Certificate>,
            authType: String
        ) {
        }

        override fun getAcceptedIssuers(): Array<X509Certificate?> {
            return arrayOfNulls(0)
        }
    }
}