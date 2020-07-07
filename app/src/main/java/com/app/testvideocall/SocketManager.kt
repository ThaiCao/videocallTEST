package com.app.testvideocall

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
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

    private var activity: Activity? = null

    companion object{
        fun getInstance(): SocketManager? {
            return Holder.socketManager
        }
    }

    private object Holder {
        val socketManager = SocketManager()
    }

    fun setActivity(activity_ : Activity){
        this.activity = activity_
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

    override fun onIceCandidate( sdp: String?,sdpMLineIndex: Int,sdpMid: String?, action: String?) {
        Log.e("TEST_DATA","SocketManager onIceCandidate activity: $activity")
        if(activity !=null && activity is TestActivity){
            ((activity as TestActivity).addRemoteIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, sdp)))
        }
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

    override fun onSetRemoteDescription(sdp: String?, type: String?) {
        Log.e("TEST_DATA","SocketManager onSetRemoteDescription activity: $activity - type: $type")
        if(activity !=null && activity is TestActivity){
            ((activity as TestActivity).setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER,  sdp)))
        }
    }

    fun sendMessage(text: String){
        if (webSocket != null) {
            Log.e("TEST_DATA","send message: $text")
            webSocket!!.send(text)
        }
    }

    // send answer
    fun sendAnswer(sdp: String) {
        val message = JSONObject()
        try {
            message.put("sdp", sdp)
            message.put("action", "sdp")
            message.put("type", "answer") //SessionDescription.Type.ANSWER
            sendMessage(message.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    // send offer
    fun sendOffer(sdp: String) {
        val message = JSONObject()
        try {
            message.put("sdp", sdp)
            message.put("action", "sdp")
            message.put("type", "offer") //SessionDescription.Type.OFFER
            sendMessage(message.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    // send ice-candidate
    fun sendIceCandidate(sdp: String,sdpMid: String,sdpMLineIndex: Int) {
//        {
//            "sdp":"candidate:3382678889 1 udp 2122260223 192.168.100.133 64571 typ host generation 0 ufrag sGXr network-id 1 network-cost 10",
//            "sdpMLineIndex":0,
//            "sdpMid":"0",
//            "action":"candidate"
//        }

        val message = JSONObject()
        try {
            message.put("sdp", sdp)
            message.put("action", "candidate")
            message.put("sdpMLineIndex", sdpMLineIndex)
            message.put("sdpMid", sdpMid)
            sendMessage(message.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
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