package com.app.testvideocall

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.android.synthetic.main.activity_test.*
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.*
import org.webrtc.PeerConnection.Observer
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.net.URISyntaxException
import java.util.*

class TestActivity : AppCompatActivity() {

    private val TAG = "TestActivity"

    companion object {
        private const val RC_CALL: Int = 111
        private const val UserId: String = "User11211"
    }

    val VIDEO_RESOLUTION_WIDTH = 1280
    val VIDEO_RESOLUTION_HEIGHT = 720
    val FPS = 30
    val VIDEO_TRACK_ID = "ARDAMSv0"

    private var socket: Socket? = null
    private var isInitiator = false
    private var isChannelReady = false
    private var isStarted = false

    private var peerConnection: PeerConnection? = null
    private var rootEglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var videoTrackFromCamera: VideoTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        start()
    }

    @AfterPermissionGranted(RC_CALL)
    fun start() {
        val perms = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (EasyPermissions.hasPermissions(this, *perms)) {
            connectToSignallingServer()
            initializeSurfaceViews()
            initializePeerConnectionFactory()
            createVideoTrackFromCameraAndShowIt()
            initializePeerConnections()
            startStreamingVideo()
        } else {
            EasyPermissions.requestPermissions(
                this,
                "Need some permissions",
                RC_CALL,
                *perms
            )
        }
    }


    private fun connectToSignallingServer() {
        try {
            socket = IO.socket("https://salty-sea-26559.herokuapp.com/")
//            socket = IO.socket("https://wertcsocket.herokuapp.com/")
            socket!!.on(
                Socket.EVENT_CONNECT,
                Emitter.Listener {
//                    tvSocketStatus.text = " connect"
                    Log.e(TAG,"connectToSignallingServer: connect")
                    socket!!.emit("create or join", "foo")
                }
            ).on("ipaddr") {
//                tvSocketStatus.text = " ipaddr"
                Log.e(TAG,"connectToSignallingServer: ipaddr")
            }.on("created") {
//                tvSocketStatus.text = " created"
                Log.e(TAG,"connectToSignallingServer: created")
                isInitiator = true
            }.on("full") {
//                tvSocketStatus.text = " full"
                Log.e(TAG,"connectToSignallingServer: full")
            }.on("join") {
//                tvSocketStatus.text = " join room"
                Log.e(TAG,"connectToSignallingServer: join")
                Log.e(TAG,"connectToSignallingServer: Another peer made a request to join room $it")
                Log.e(TAG,"connectToSignallingServer: This peer is the initiator of room")
                isChannelReady = true
            }.on("joined") {Log.e(TAG,"connectToSignallingServer: joined")
//                tvSocketStatus.text = " joined room"
                isChannelReady = true
            }.on("log") { args: Array<Any> ->
                for (arg in args) {
                    Log.e(TAG,"connectToSignallingServer -- log --: $arg")
                }
            }.on("message") {
                Log.e(TAG,"connectToSignallingServer: got a message")
            }.on("message") { args: Array<Any> ->
                try {
                    if (args[0] is String) {
                        val message = args[0] as String
                        Log.e(TAG,"connectToSignallingServer: -- got user media-- got message $message")
//                        if (message == "got user media") {
                        if (message == UserId) {
                            maybeStart()
                        }
                    } else {
                        val message = args[0] as JSONObject
                        Log.e(TAG,"connectToSignallingServer: got message $message")
//                        tvSocketStatus.text = message.getString("type")
                        if (message.getString("type") == "offer") {
                            Log.e(TAG,"connectToSignallingServer: received an offer $isInitiator $isStarted")
                            if (!isInitiator && !isStarted) {
                                maybeStart()
                            }
                            peerConnection!!.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(
                                    SessionDescription.Type.OFFER,
                                    message.getString("sdp")
                                )
                            )
                            doAnswer()
                        } else if (message.getString("type") == "answer" && isStarted) {
                            peerConnection!!.setRemoteDescription(
                                SimpleSdpObserver(),
                                SessionDescription(
                                    SessionDescription.Type.ANSWER,
                                    message.getString("sdp")
                                )
                            )
                        } else if (message.getString("type") == "candidate" && isStarted) {
                            Log.e(TAG,"connectToSignallingServer: receiving candidates")
                            val candidate = IceCandidate(
                                message.getString("id"),
                                message.getInt("label"),
                                message.getString("candidate")
                            )
                            peerConnection!!.addIceCandidate(candidate)
                        }
                        /*else if (message === 'bye' && isStarted) {
                                            handleRemoteHangup()
                                        }*/
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }.on(Socket.EVENT_DISCONNECT) {
                Log.e(TAG,"connectToSignallingServer: disconnect")
            }
            socket!!.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
    }

    private fun maybeStart() {
        Log.e(TAG,"maybeStart isStarted: $isStarted  - isChannelReady: $isChannelReady - isInitiator: $isInitiator")
        if (!isStarted && isChannelReady) {
            isStarted = true
            if (isInitiator) {
                doCall()
            }
        }
    }

    private fun doCall() {
//        val sdpMediaConstraints = offerOrAnswerConstraint()
        val sdpMediaConstraints = MediaConstraints()
        peerConnection!!.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                Log.e(TAG,"doCall createOffer onCreateSuccess: ${sessionDescription.toString()}")
                peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "offer") //SessionDescription.Type.OFFER
                    message.put("sdp", sessionDescription!!.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, sdpMediaConstraints)
    }

    private fun doAnswer() {
        peerConnection!!.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                Log.e(TAG,"doAnswer createAnswer onCreateSuccess: ${sessionDescription.toString()}")
                peerConnection!!.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "answer") // SessionDescription.Type.ANSWER
                    message.put("sdp", sessionDescription!!.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, MediaConstraints())
    }

    private fun offerOrAnswerConstraint(): MediaConstraints? {
        val mediaConstraints = MediaConstraints()
        val keyValuePairs =
            ArrayList<MediaConstraints.KeyValuePair>()
        keyValuePairs.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        keyValuePairs.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        mediaConstraints.mandatory.addAll(keyValuePairs)
        return mediaConstraints
    }

    private fun sendMessage(message: Any) {
        Log.e(TAG,"sendMessage: $message")
        socket!!.emit("message", message)
    }


    private fun initializeSurfaceViews() {
        rootEglBase = EglBase.create()
        surfaceView.init(rootEglBase!!.eglBaseContext, null)
        surfaceView.setEnableHardwareScaler(true)
        surfaceView.setMirror(true)
        surfaceView2.init(rootEglBase!!.eglBaseContext, null)
        surfaceView2.setEnableHardwareScaler(true)
        surfaceView2.setMirror(true)
    }

    private fun initializePeerConnectionFactory() {
        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)
        factory = PeerConnectionFactory(null)
        factory!!.setVideoHwAccelerationOptions(
            rootEglBase!!.eglBaseContext,
            rootEglBase!!.eglBaseContext
        )
    }

    private fun createVideoTrackFromCameraAndShowIt() {
        val videoCapturer = createVideoCapturer()
        val videoSource = factory!!.createVideoSource(videoCapturer)
        videoCapturer!!.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)
        videoTrackFromCamera = factory!!.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        videoTrackFromCamera!!.setEnabled(true)
        videoTrackFromCamera!!.addRenderer(VideoRenderer(surfaceView))
    }

    private fun initializePeerConnections() {
        peerConnection = createPeerConnection(factory!!)
    }

    private fun startStreamingVideo() {
        val mediaStream = factory!!.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(videoTrackFromCamera)
        peerConnection!!.addStream(mediaStream)
//        sendMessage("got user media")
        sendMessage(UserId)
    }

    private fun createPeerConnection(factory: PeerConnectionFactory): PeerConnection {
        val iceServers = ArrayList<IceServer>()
//        iceServers.add(IceServer("stun:stun.l.google.com:19302"))
        iceServers.add(IceServer("stun:ss-turn2.xirsys.com"))
        iceServers.add(IceServer("turn:ss-turn2.xirsys.com:80?transport=udp","yEMjNNWgsYpAE1yBfxi9aEcdyVR7h7Oqh1kQmucfFgre3wn-rvQmUCeBgiJ-OL-sAAAAAF77JAJraG9haW5ib3g=","1989b4ea-bac6-11ea-a6ed-0242ac140004"))
        iceServers.add(IceServer("turn:ss-turn2.xirsys.com:3478?transport=udp","yEMjNNWgsYpAE1yBfxi9aEcdyVR7h7Oqh1kQmucfFgre3wn-rvQmUCeBgiJ-OL-sAAAAAF77JAJraG9haW5ib3g=","1989b4ea-bac6-11ea-a6ed-0242ac140004"))
        iceServers.add(IceServer("turn:ss-turn2.xirsys.com:80?transport=tcp","yEMjNNWgsYpAE1yBfxi9aEcdyVR7h7Oqh1kQmucfFgre3wn-rvQmUCeBgiJ-OL-sAAAAAF77JAJraG9haW5ib3g=","1989b4ea-bac6-11ea-a6ed-0242ac140004"))
        iceServers.add(IceServer("turn:ss-turn2.xirsys.com:3478?transport=tcp","yEMjNNWgsYpAE1yBfxi9aEcdyVR7h7Oqh1kQmucfFgre3wn-rvQmUCeBgiJ-OL-sAAAAAF77JAJraG9haW5ib3g=","1989b4ea-bac6-11ea-a6ed-0242ac140004"))
        iceServers.add(IceServer("turns:ss-turn2.xirsys.com:443?transport=tcp","yEMjNNWgsYpAE1yBfxi9aEcdyVR7h7Oqh1kQmucfFgre3wn-rvQmUCeBgiJ-OL-sAAAAAF77JAJraG9haW5ib3g=","1989b4ea-bac6-11ea-a6ed-0242ac140004"))
        iceServers.add(IceServer("turns:ss-turn2.xirsys.com:5349?transport=tcp","yEMjNNWgsYpAE1yBfxi9aEcdyVR7h7Oqh1kQmucfFgre3wn-rvQmUCeBgiJ-OL-sAAAAAF77JAJraG9haW5ib3g=","1989b4ea-bac6-11ea-a6ed-0242ac140004"))
        val rtcConfig = RTCConfiguration(iceServers)
        val pcConstraints = MediaConstraints()
        val pcObserver: Observer = object : Observer {
            override fun onSignalingChange(signalingState: SignalingState) {
                Log.e(TAG,"onSignalingChange: ${signalingState.name} - ordinal: ${signalingState.ordinal}")
//                tvSignaling.text = " ${signalingState.name} - ordinal: ${signalingState.ordinal}"
            }

            override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                Log.e(TAG,"onIceConnectionChange: ${iceConnectionState.name} - ordinal: ${iceConnectionState.ordinal}")
//                tvIceServerStatus.text = " ${iceConnectionState.name} - ordinal: ${iceConnectionState.ordinal}"
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                Log.e(TAG,"onIceConnectionReceivingChange: $b")
            }

            override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                Log.e(TAG,"onIceGatheringChange: ${iceGatheringState.name} - ordinal: ${iceGatheringState.ordinal}")
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
//                Log.e(TAG,"onIceCandidate: ")
                val message = JSONObject()
                try {
                    message.put("type", "candidate")
                    message.put("label", iceCandidate.sdpMLineIndex)
                    message.put("id", iceCandidate.sdpMid)
                    message.put("candidate", iceCandidate.sdp)
                    Log.e(TAG,"onIceCandidate: sending candidate $message")
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                Log.e(TAG,"onIceCandidatesRemoved: ${iceCandidates.size}")
            }

            override fun onAddStream(mediaStream: MediaStream) {
                Log.e(TAG,"onAddStream: " + mediaStream.videoTracks.size)
                val remoteVideoTrack = mediaStream.videoTracks[0]
                remoteVideoTrack.setEnabled(true)
                remoteVideoTrack.addRenderer(VideoRenderer(surfaceView2))
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.e(TAG,"onRemoveStream: ")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.e(TAG,"onDataChannel: ")
            }

            override fun onRenegotiationNeeded() {
                Log.e(TAG,"onRenegotiationNeeded: ")
            }
        }
        return factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        return if (useCamera2()) {
            createCameraCapturer(Camera2Enumerator(this))
        } else {
            createCameraCapturer(Camera1Enumerator(true))
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(this)
    }

}