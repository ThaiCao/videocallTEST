//package com.app.testvideocall
//
//import com.google.gson.Gson
//import org.java_websocket.client.WebSocketClient
//import org.java_websocket.handshake.ServerHandshake
//import java.net.URI
//
//class DWebSocket(val serverUri: URI, val iEvent: IEvent) : WebSocketClient(serverUri) {
//
//    private var connectFlag = false
//    private val gson : Gson = Gson()
//    init {
//
//    }
//
//    override fun onOpen(handshakedata: ServerHandshake?) {
//        iEvent.onOpen()
//        connectFlag = true
//    }
//
//    override fun onClose(code: Int, reason: String?, remote: Boolean) {
//        if (connectFlag) {
//            try {
//                Thread.sleep(3000)
//            } catch (e: InterruptedException) {
//                e.printStackTrace()
//            }
//            iEvent.reConnect()
//        } else {
//            iEvent.logout("onClose")
//        }
//    }
//
//    override fun onMessage(message: String?) {
//        handleMessage(message)
//    }
//
//    override fun onError(ex: Exception?) {
//        iEvent.logout("onError")
//        connectFlag = false
//    }
//
//    fun setConnectFlag(flag: Boolean) {
//        connectFlag = flag
//    }
//
//
//    private fun handleMessage(message: String?) {
//        val map: Map<*, *> = gson.fromJson(message, MutableMap::class.java)
//        val eventName = map["eventName"] as String? ?: return
//        // 登录成功
//        if (eventName == "__login_success") {
//            handleLogin(map)
//            return
//        }
//        // 被邀请
//        if (eventName == "__invite") {
//            handleInvite(map)
//            return
//        }
//        // 取消拨出
//        if (eventName == "__cancel") {
//            handleCancel(map)
//            return
//        }
//        // 响铃
//        if (eventName == "__ring") {
//            handleRing(map)
//            return
//        }
//        // 进入房间
//        if (eventName == "__peers") {
//            handlePeers(map)
//            return
//        }
//        // 新人入房间
//        if (eventName == "__new_peer") {
//            handleNewPeer(map)
//            return
//        }
//        // 拒绝接听
//        if (eventName == "__reject") {
//            handleReject(map)
//            return
//        }
//        // offer
//        if (eventName == "__offer") {
//            handleOffer(map)
//            return
//        }
//        // answer
//        if (eventName == "__answer") {
//            handleAnswer(map)
//            return
//        }
//        // ice-candidate
//        if (eventName == "__ice_candidate") {
//            handleIceCandidate(map)
//        }
//        // 离开房间
//        if (eventName == "__leave") {
//            handleLeave(map)
//        }
//        // 切换到语音
//        if (eventName == "__audio") {
//            handleTransAudio(map)
//        }
//        // 意外断开
//        if (eventName == "__disconnect") {
//            handleDisConnect(map)
//        }
//    }
//
//    private fun handleDisConnect(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val fromId = data["fromID"] as String?
//            iEvent.onDisConnect(fromId)
//        }
//    }
//
//    private fun handleTransAudio(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val fromId = data["fromID"] as String?
//            iEvent.onTransAudio(fromId)
//        }
//    }
//
//    private fun handleLogin(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val userID = data["userID"] as String?
//            val avatar = data["avatar"] as String?
//            iEvent.loginSuccess(userID, avatar)
//        }
//    }
//
//    private fun handleIceCandidate(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val userID = data["fromID"] as String?
//            val id = data["id"] as String?
//            val label = data["label"] as Int
//            val candidate = data["candidate"] as String?
//            iEvent.onIceCandidate(userID, id, label, candidate)
//        }
//    }
//
//    private fun handleAnswer(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val sdp = data["sdp"] as String?
//            val userID = data["fromID"] as String?
//            iEvent.onAnswer(userID, sdp)
//        }
//    }
//
//    private fun handleOffer(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val sdp = data["sdp"] as String?
//            val userID = data["fromID"] as String?
//            iEvent.onOffer(userID, sdp)
//        }
//    }
//
//    private fun handleReject(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val fromID = data["fromID"] as String?
//            val rejectType = data["refuseType"].toString().toInt()
//            iEvent.onReject(fromID, rejectType)
//        }
//    }
//
//    private fun handlePeers(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val you = data["you"] as String?
//            val connections = data["connections"] as String?
//            val roomSize = data["roomSize"] as Int
//            iEvent.onPeers(you, connections, roomSize)
//        }
//    }
//
//    private fun handleNewPeer(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val userID = data["userID"] as String?
//            iEvent.onNewPeer(userID)
//        }
//    }
//
//    private fun handleRing(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val fromId = data["fromID"] as String?
//            iEvent.onRing(fromId)
//        }
//    }
//
//    private fun handleCancel(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val inviteID = data["inviteID"] as String?
//            val userList = data["userList"] as String?
//            iEvent.onCancel(inviteID)
//        }
//    }
//
//    private fun handleInvite(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val room = data["room"] as String?
//            val audioOnly = data["audioOnly"] as Boolean
//            val inviteID = data["inviteID"] as String?
//            val userList = data["userList"] as String?
//            iEvent.onInvite(room, audioOnly, inviteID, userList)
//        }
//    }
//
//    private fun handleLeave(map: Map<*, *>) {
//        val data = map["data"] as Map<*, *>?
//        if (data != null) {
//            val fromID = data["fromID"] as String?
//            iEvent.onLeave(fromID)
//        }
//    }
//}