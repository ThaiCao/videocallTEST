package com.app.testvideocall

//import io.socket.client.IO
//import io.socket.client.Socket
//import io.socket.emitter.Emitter
import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_test.*
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.PeerConnection.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
import java.util.concurrent.ScheduledExecutorService

class TestActivity : AppCompatActivity(), SdpObserver {

    private val TAG = "TestActivity"

    companion object {
        private const val RC_CALL: Int = 111
        private const val UserId: String = "User11211"
    }

    val VIDEO_TRACK_ID = "ARDAMSv0"
    val AUDIO_TRACK_ID = "ARDAMSa0"
    val VIDEO_TRACK_TYPE = "video"
    private val VIDEO_CODEC_VP8 = "VP8"
    private val VIDEO_CODEC_VP9 = "VP9"
    private val VIDEO_CODEC_H264 = "H264"
    private val AUDIO_CODEC_OPUS = "opus"
    private val AUDIO_CODEC_ISAC = "ISAC"
    private val VIDEO_CODEC_PARAM_START_BITRATE = "x-google-start-bitrate"
    private val VIDEO_FLEXFEC_FIELDTRIAL = "WebRTC-FlexFEC-03/Enabled/"
    private val AUDIO_CODEC_PARAM_BITRATE = "maxaveragebitrate"
    private val AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
    private val AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
    private val AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter"
    private val AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"
    private val AUDIO_LEVEL_CONTROL_CONSTRAINT = "levelControl"
    private val DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT = "DtlsSrtpKeyAgreement"


    val VIDEO_RESOLUTION_WIDTH = 1280
    val VIDEO_RESOLUTION_HEIGHT = 720
    val FPS = 30

//    private var socket: Socket? = null
    private var isInitiator = false
    private var isChannelReady = false
    private var isStarted = false

    private var peerConnection: PeerConnection? = null
    private var rootEglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var videoTrackFromCamera: VideoTrack? = null

    var options: PeerConnectionFactory.Options? = null
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private val remoteVideoTrack: VideoTrack? = null
    private var localVideoSender: RtpSender? = null

    // enableAudio is set to true if audio should be sent.
    private var enableAudio = true
    private var localAudioTrack: AudioTrack? = null
    private var audioConstraints: MediaConstraints? = null
    private var sdpMediaConstraints: MediaConstraints? = null
    private var pcConstraints: MediaConstraints? = null
    private val executor: ScheduledExecutorService? = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    private var isOffer = false
    private var localSdp: SessionDescription? = null
    private var remoteSdp: SessionDescription? = null
    private var queuedRemoteCandidates: ArrayList<IceCandidate>? = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)

        connectToSignallingServer()
        SocketManager.getInstance()!!.setActivity(this)
        btnCall.setOnClickListener {
            isOffer = true
            onInitConnection()
            start(1)
        }

    }

    fun onInitConnection(){
//        executor!!.execute {
        Log.e("TEST_DATA", "onInitConnection createMediaConstraintsInternal")
            createMediaConstraintsInternal()
        Log.e("TEST_DATA", "onInitConnection initializeSurfaceViews")
            initializeSurfaceViews()
        Log.e("TEST_DATA", "onInitConnection initializePeerConnectionFactory")
            initializePeerConnectionFactory()
        Log.e("TEST_DATA", "onInitConnection createVideoTrackFromCameraAndShowIt")
            createVideoTrackFromCameraAndShowIt()
        Log.e("TEST_DATA", "onInitConnection initializePeerConnections")
            initializePeerConnections()
        Log.e("TEST_DATA", "onInitConnection startStreamingVideo")
            startStreamingVideo()

    }

    fun doAnwserSdp(sdp: String?){
        Handler(Looper.getMainLooper()).post {
            onInitConnection()
//            createAnswer()
            remoteSdp = SessionDescription(SessionDescription.Type.OFFER,  sdp)
            setRemoteDescription(remoteSdp)
            start(2)
        }
    }

    @AfterPermissionGranted(RC_CALL)
    fun start(value: Int) {
        val perms = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (EasyPermissions.hasPermissions(this, *perms)) {
            // set tạm để call trước -> viết module xủ lý call


            if(value == 1){
                isOffer = true
                createOffer()
            }else{
                isOffer = false
                createAnswer()
            }
        } else {
            EasyPermissions.requestPermissions(
                this,
                "Need some permissions",
                RC_CALL,
                *perms
            )
        }
    }

    fun createOffer() {
        if (peerConnection == null) return
        Log.e("TEST_DATA", "createOffer")
//        setOffer(true)
        peerConnection!!.createOffer(this, sdpMediaConstraints)
    }

    fun setLocalDescription(sdp: SessionDescription?) {
        Log.e("TEST_DATA", "setLocalDescription")
        if (peerConnection == null) return
        peerConnection!!.setLocalDescription(this, sdp)
    }

    fun setOffer(isOffer: Boolean) {
        this.isOffer = isOffer
    }

    fun createAnswer() {
        if (peerConnection == null) return
        Log.e("TEST_DATA", "createAnswer")
        peerConnection!!.createAnswer(this, sdpMediaConstraints)
    }

    fun setRemoteDescription(sdp: SessionDescription?) {
        if (peerConnection == null) return
        Log.e("TEST_DATA", "setRemoteDescription sdp: $sdp")
        peerConnection!!.setRemoteDescription(this, sdp)
    }

    fun addLocalStream(stream: MediaStream?) {
        if (peerConnection == null) return
        Log.d("dds_test", "addLocalStream: $stream")
        peerConnection!!.addStream(stream)
    }

    private fun drainCandidates() {
        Log.e("TEST_DATA", "drainCandidates queuedRemoteCandidates: $queuedRemoteCandidates")
        if (queuedRemoteCandidates != null) {
            Log.e("TEST_DATA","Add " + queuedRemoteCandidates!!.size + " remote candidates")
            for (candidate in queuedRemoteCandidates!!) {
                peerConnection!!.addIceCandidate(candidate)
            }
            queuedRemoteCandidates = null
        }
    }

    fun addRemoteIceCandidate(candidate: IceCandidate?) {
//        Log.e("dds_test", "addRemoteIceCandidate")
//        Log.e("TEST_DATA","TestActivity addRemoteIceCandidate candidate:  $candidate - peerConnection: $peerConnection")
        if (peerConnection != null) {
            peerConnection!!.addIceCandidate(candidate)
//            if (queuedRemoteCandidates != null) {
//                Log.e("dds_test", "addRemoteIceCandidate  2222")
//                if (candidate != null) {
//                    queuedRemoteCandidates!!.add(candidate)
//                }
//            } else {
//                Log.e("dds_test", "addRemoteIceCandidate1111")
//                peerConnection!!.addIceCandidate(candidate)
//            }
        }
    }

    fun removeRemoteIceCandidates(candidates: Array<IceCandidate?>?) {
        if (peerConnection == null) {
            return
        }
        drainCandidates()
        peerConnection!!.removeIceCandidates(candidates)
    }

    private fun connectToSignallingServer() {
//        wss.setText("ws://192.168.1.138:5000/ws");
//        SocketManager.getInstance().addUserStateCallback(this)
        SocketManager.getInstance()!!.connect("wss://wertcsocket.herokuapp.com/",UserId, 0)
//        try {
//            socket = IO.socket("https://salty-sea-26559.herokuapp.com/")
////            socket = IO.socket("https://wertcsocket.herokuapp.com/")
//            socket!!.on(
//                Socket.EVENT_CONNECT,
//                Emitter.Listener {
////                    tvSocketStatus.text = " connect"
//                    Log.e(TAG,"connectToSignallingServer: connect")
//                    socket!!.emit("create or join", "foo")
//                }
//            ).on("ipaddr") {
////                tvSocketStatus.text = " ipaddr"
//                Log.e(TAG,"connectToSignallingServer: ipaddr")
//            }.on("created") {
////                tvSocketStatus.text = " created"
//                Log.e(TAG,"connectToSignallingServer: created")
//                isInitiator = true
//            }.on("full") {
////                tvSocketStatus.text = " full"
//                Log.e(TAG,"connectToSignallingServer: full")
//            }.on("join") {
////                tvSocketStatus.text = " join room"
//                Log.e(TAG,"connectToSignallingServer: join")
//                Log.e(TAG,"connectToSignallingServer: Another peer made a request to join room $it")
//                Log.e(TAG,"connectToSignallingServer: This peer is the initiator of room")
//                isChannelReady = true
//            }.on("joined") {Log.e(TAG,"connectToSignallingServer: joined")
////                tvSocketStatus.text = " joined room"
//                isChannelReady = true
//            }.on("log") { args: Array<Any> ->
//                for (arg in args) {
//                    Log.e(TAG,"connectToSignallingServer -- log --: $arg")
//                }
//            }.on("message") {
//                Log.e(TAG,"connectToSignallingServer: got a message")
//            }.on("message") { args: Array<Any> ->
//                try {
//                    if (args[0] is String) {
//                        val message = args[0] as String
//                        Log.e(TAG,"connectToSignallingServer: -- got user media-- got message $message")
////                        if (message == "got user media") {
//                        if (message == UserId) {
//                            maybeStart()
//                        }
//                    } else {
//                        val message = args[0] as JSONObject
//                        Log.e(TAG,"connectToSignallingServer: got message $message")
////                        tvSocketStatus.text = message.getString("type")
//                        if (message.getString("type") == "offer") {
//                            Log.e(TAG,"connectToSignallingServer: received an offer $isInitiator $isStarted")
//                            if (!isInitiator && !isStarted) {
//                                maybeStart()
//                            }
//                            peerConnection!!.setRemoteDescription(
//                                SimpleSdpObserver(),
//                                SessionDescription(
//                                    SessionDescription.Type.OFFER,
//                                    message.getString("sdp")
//                                )
//                            )
//                            doAnswer()
//                        } else if (message.getString("type") == "answer" && isStarted) {
//                            peerConnection!!.setRemoteDescription(
//                                SimpleSdpObserver(),
//                                SessionDescription(
//                                    SessionDescription.Type.ANSWER,
//                                    message.getString("sdp")
//                                )
//                            )
//                        } else if (message.getString("type") == "candidate" && isStarted) {
//                            Log.e(TAG,"connectToSignallingServer: receiving candidates")
//                            val candidate = IceCandidate(
//                                message.getString("id"),
//                                message.getInt("label"),
//                                message.getString("candidate")
//                            )
//                            peerConnection!!.addIceCandidate(candidate)
//                        }
//                        /*else if (message === 'bye' && isStarted) {
//                                            handleRemoteHangup()
//                                        }*/
//                    }
//                } catch (e: JSONException) {
//                    e.printStackTrace()
//                }
//            }.on(Socket.EVENT_DISCONNECT) {
//                Log.e(TAG,"connectToSignallingServer: disconnect")
//            }
//            socket!!.connect()
//        } catch (e: URISyntaxException) {
//            e.printStackTrace()
//        }
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
//        val sdpMediaConstraints = MediaConstraints()
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

    fun doAnswer() {
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
        }, sdpMediaConstraints)
    }

    private fun createMediaConstraintsInternal(){

        // Create peer connection constraints.

        // Create peer connection constraints.
        pcConstraints = MediaConstraints()
        // Enable DTLS for normal calls and disable for loopback calls.
        // Enable DTLS for normal calls and disable for loopback calls.
        pcConstraints!!.optional.add(MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT,"true"))
//        if (peerConnectionParameters.loopback) {
//            pcConstraints!!.optional.add(MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT,"false"))
//        } else {
//            pcConstraints!!.optional.add(MediaConstraints.KeyValuePair(DTLS_SRTP_KEY_AGREEMENT_CONSTRAINT,"true"))
//        }

        // Create SDP constraints.
        sdpMediaConstraints = MediaConstraints()
        sdpMediaConstraints!!.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        sdpMediaConstraints!!.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
//        sdpMediaConstraints!!.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
//        if (videoCallEnabled || peerConnectionParameters.loopback) {
//            sdpMediaConstraints!!.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
//        } else {
//            sdpMediaConstraints!!.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
//        }



        // Create audio constraints.
        audioConstraints = MediaConstraints()
        // added for audio performance measurements
        // added for audio performance measurements
//        audioConstraints!!.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT,"false"))
//        audioConstraints!!.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT,"false"))
//        audioConstraints!!.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT,"false"))
//        audioConstraints!!.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT,"false"))

//        if (peerConnectionParameters.noAudioProcessing) {
//            Log.e(TAG,"Disabling audio processing")
//            audioConstraints!!.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT,"false"))
//            audioConstraints!!.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT,"false"))
//            audioConstraints!!.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT,"false"))
//            audioConstraints!!.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT,"false"))
//        }
//        if (peerConnectionParameters.enableLevelControl) {
//            Log.e(TAG,"Enabling level control.")
//            audioConstraints!!.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_LEVEL_CONTROL_CONSTRAINT,"true"))
//        }
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
//        socket!!.emit("message", message)
        SocketManager.getInstance()!!.sendMessage(message as String)
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
        mediaStream.addTrack(createAudioTrack())
        peerConnection!!.addStream(mediaStream)
//        sendMessage("got user media")
//        sendMessage(UserId)
        findVideoSender()
    }

    private fun findVideoSender() {
        for (sender in peerConnection!!.senders) {
            if (sender.track() != null) {
                val trackType = sender.track().kind()
                if (trackType == VIDEO_TRACK_TYPE) {
                    Log.e(TAG,"Found video sender.")
                    localVideoSender = sender
                }
            }
        }
    }

    fun setAudioEnabled(enable: Boolean) {
        executor!!.execute {
            enableAudio = enable
            if (localAudioTrack != null) {
                localAudioTrack!!.setEnabled(enableAudio)
            }
        }
    }

//    fun onSaveRemoteCandidate(){
//        if (peerConnection == null) return
//        val candidate = IceCandidate()
//        peerConnection!!.addIceCandidate(candidate)
//    }

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
        rtcConfig.continualGatheringPolicy = ContinualGatheringPolicy.GATHER_CONTINUALLY
        rtcConfig.rtcpMuxPolicy = RtcpMuxPolicy.NEGOTIATE
//        val pcConstraints = offerOrAnswerConstraint()
//        val pcConstraints = MediaConstraints()
        val pcObserver: Observer = object : Observer {
            override fun onSignalingChange(signalingState: SignalingState) {
                Log.e(TAG,"PeerConnection onSignalingChange: ${signalingState.name} - ordinal: ${signalingState.ordinal}")
//                tvSignaling.text = " ${signalingState.name} - ordinal: ${signalingState.ordinal}"
            }

            override fun onIceConnectionChange(iceConnectionState: IceConnectionState) {
                Log.e(TAG,"PeerConnection onIceConnectionChange: ${iceConnectionState.name} - ordinal: ${iceConnectionState.ordinal}")
//                tvIceServerStatus.text = " ${iceConnectionState.name} - ordinal: ${iceConnectionState.ordinal}"
            }

            override fun onIceConnectionReceivingChange(b: Boolean) {
                Log.e(TAG,"PeerConnection onIceConnectionReceivingChange: $b")
            }

            override fun onIceGatheringChange(iceGatheringState: IceGatheringState) {
                Log.e(TAG,"PeerConnection onIceGatheringChange: ${iceGatheringState.name} - ordinal: ${iceGatheringState.ordinal}")
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
//                Log.e(TAG,"onIceCandidate: ")
                Log.e(TAG,"onIceCandidate: sending iceCandidate $iceCandidate")
                executor!!.execute {
//                    try {
//                        Thread.sleep(100)
//                    } catch (e: InterruptedException) {
//                        e.printStackTrace()
//                    }

                    SocketManager.getInstance()!!.sendIceCandidate(iceCandidate.sdp, iceCandidate.sdpMid, iceCandidate.sdpMLineIndex)

//                    val message = JSONObject()
//                    try {
//                        message.put("type", "candidate")
//                        message.put("label", iceCandidate.sdpMLineIndex)
//                        message.put("id", iceCandidate.sdpMid)
//                        message.put("candidate", iceCandidate.sdp)
//                        Log.e(TAG,"onIceCandidate: sending candidate $message")
//                    sendMessage(message)
//                    } catch (e: JSONException) {
//                        e.printStackTrace()
//                    }
                }

            }

            override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                Log.e(TAG,"PeerConnection onIceCandidatesRemoved: ${iceCandidates.size}")
            }

            override fun onAddStream(mediaStream: MediaStream) {
                Log.e(TAG,"PeerConnection onAddStream: " + mediaStream.videoTracks.size)
                val remoteVideoTrack = mediaStream.videoTracks[0]
                remoteVideoTrack.setEnabled(true)
                remoteVideoTrack.addRenderer(VideoRenderer(surfaceView2))
            }

            override fun onRemoveStream(mediaStream: MediaStream) {
                Log.e(TAG,"PeerConnection onRemoveStream: $mediaStream")
            }

            override fun onDataChannel(dataChannel: DataChannel) {
                Log.e(TAG,"PeerConnection onDataChannel: $dataChannel")
            }

            override fun onRenegotiationNeeded() {
                Log.e(TAG,"PeerConnection onRenegotiationNeeded: ")
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


    private fun createAudioTrack(): AudioTrack? {
        audioSource = factory!!.createAudioSource(audioConstraints)
        localAudioTrack = factory!!.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        localAudioTrack!!.setEnabled(enableAudio)
        return localAudioTrack
    }

    override fun onSetFailure(p0: String?) {
        Log.e("TEST_DATA", "PeerConnection onSetFailure  :$p0")
    }

    override fun onSetSuccess() {
        if (peerConnection == null) return
        Log.e("TEST_DATA", "PeerConnection onSetSuccess isOffer: $isOffer -- signalingState: " + peerConnection!!.signalingState().toString())
        Log.e("TEST_DATA", "PeerConnection peerConnection!!.remoteDescription : ${peerConnection!!.remoteDescription} -- peerConnection!!.localDescription: ${peerConnection!!.localDescription}" )
        if (!isOffer) {
//                    mEvent.onSendAnswer(mUserId, localSdp)
//            if(localSdp !=null)
            if (peerConnection!!.localDescription != null) {
                SocketManager.getInstance()!!.sendAnswer(localSdp!!.description)
            }

        } else {
//                    mEvent.onSendOffer(mUserId, localSdp)
            if (peerConnection!!.remoteDescription  == null) {
                SocketManager.getInstance()!!.sendOffer(localSdp!!.description)
            }
        }
//        if (isOffer) {
//            Log.e("TEST_DATA", "PeerConnection onSetSuccess Remote SDP set succesfully :$isOffer")
//            if (!isOffer) {
////                    mEvent.onSendAnswer(mUserId, localSdp)
//                SocketManager.getInstance()!!.sendAnswer(localSdp!!.description)
//            } else {
////                    mEvent.onSendOffer(mUserId, localSdp)
//                SocketManager.getInstance()!!.sendOffer(localSdp!!.description)
//            }
////            if (peerConnection!!.remoteDescription == null) {
////                Log.e("TEST_DATA", "PeerConnection onSetSuccess Remote SDP set succesfully :$isOffer")
////                if (!isOffer) {
//////                    mEvent.onSendAnswer(mUserId, localSdp)
////                    SocketManager.getInstance()!!.sendAnswer(localSdp!!.description)
////                } else {
//////                    mEvent.onSendOffer(mUserId, localSdp)
////                    SocketManager.getInstance()!!.sendOffer(localSdp!!.description)
////                }
////            } else {
////                Log.e("TEST_DATA", "Remote SDP set succesfully")
////                drainCandidates()
////            }
//        } else {
//            Log.e("TEST_DATA", "Local SDP set succesfully :$isOffer")
//            if (!isOffer) {
////                    mEvent.onSendAnswer(mUserId, localSdp)
//                SocketManager.getInstance()!!.sendAnswer(localSdp!!.description)
//            } else {
//                SocketManager.getInstance()!!.sendOffer(localSdp!!.description)
////                    mEvent.onSendOffer(mUserId, localSdp)
//            }
////            if (peerConnection!!.localDescription != null) {
////                Log.e("TEST_DATA", "Local SDP set succesfully :$isOffer")
////                if (!isOffer) {
//////                    mEvent.onSendAnswer(mUserId, localSdp)
////                    SocketManager.getInstance()!!.sendAnswer(localSdp!!.description)
////                } else {
////                    SocketManager.getInstance()!!.sendOffer(localSdp!!.description)
//////                    mEvent.onSendOffer(mUserId, localSdp)
////                }
////                drainCandidates()
////            } else {
////                Log.e("TEST_DATA", "Remote SDP set succesfully")
////            }
//        }
    }

    override fun onCreateSuccess(origSdp: SessionDescription?) {
        Log.e("TEST_DATA", "PeerConnection onCreateSuccess     " + origSdp!!.type)
        val sdpString: String = origSdp.description
        val sdp = SessionDescription(origSdp.type, sdpString)
        localSdp = sdp
        setLocalDescription(sdp)
    }

    override fun onCreateFailure(p0: String?) {
        TODO("Not yet implemented")
    }



//    private fun createVideoTrack(capturer: VideoCapturer): VideoTrack? {
//        videoSource = factory!!.createVideoSource(capturer)
////        capturer.startCapture(videoWidth, videoHeight, videoFps)
//        capturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)
//        localVideoTrack = factory!!.createVideoTrack(VIDEO_TRACK_ID,
//            videoSource
//        )
//        localVideoTrack!!.setEnabled(renderVideo)
//        localVideoTrack!!.addRenderer(VideoRenderer(localRender))
//        return localVideoTrack
//    }

}