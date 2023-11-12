package com.sharertc.util

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.sharertc.model.TransferProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer

class PeerConnectionManager(
    private val context: Context,
    private val peerConnectionSide: PeerConnectionSide
) {

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.InitializationOptions
            .builder(context)
            .createInitializationOptions().also {
                PeerConnectionFactory.initialize(it)
            }
        PeerConnectionFactory
            .builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private val iceServers by lazy {
        listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
    }

    val gson by lazy {
        Gson()
    }

    private lateinit var peerConnection: PeerConnection
    private var dataChannel: DataChannel? = null
    private val dcObserver = object : DataChannel.Observer {
        override fun onBufferedAmountChange(amount: Long) {
            log("DataChannel.Observer:onBufferedAmountChange: $amount")
        }

        override fun onStateChange() {
            dataChannel?.state()?.let { state ->
                log("DataChannel.Observer:onStateChange -> state: $state")
                scope.launch {
                    _dcState.emit(state)
                }
            }
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            if (buffer.binary) {
                //writeToFile(buffer.data)
            } else {
                val transferProtocol = gson.fromJson(Charsets.UTF_8.decode(buffer.data).toString(), TransferProtocol::class.java)
                scope.launch {
                    _messages.emit(transferProtocol)
                }
                log("DataChannel.Observer:onMessage: $transferProtocol")
            }
        }
    }

    private val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val _logs = MutableSharedFlow<String>()
    val logs: Flow<String> = _logs

    private val _messages = MutableSharedFlow<TransferProtocol>()
    val messages: Flow<TransferProtocol> = _messages

    private val _dcState = MutableStateFlow(DataChannel.State.CLOSED)
    val dcState: Flow<DataChannel.State> = _dcState

    private val _sdpToQR = MutableSharedFlow<SessionDescription>()
    val sdpToQR: Flow<SessionDescription> = _sdpToQR

    init {
        initPeerConnection()
    }

    private fun initPeerConnection() {
        peerConnection = peerConnectionFactory.createPeerConnection(
            iceServers,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    log("PeerConnection.Observer:onSignalingChange: ${state.toString()}")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    log("PeerConnection.Observer:onIceConnectionChange: ${state.toString()}")
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    log("PeerConnection.Observer:onIceConnectionReceivingChange: $receiving")
                }

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    log("PeerConnection.Observer:onIceGatheringChange: ${state.toString()}")
                }

                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    if (iceCandidate.serverUrl.contains("google.com")) {
                        scope.launch {
                            _sdpToQR.emit(peerConnection.localDescription)
                        }
                    }
                    log("PeerConnection.Observer:onIceCandidate: $iceCandidate")
                }

                override fun onIceCandidatesRemoved(iceCandidates: Array<out IceCandidate>?) {
                    log("PeerConnection.Observer:onIceCandidatesRemoved: ${iceCandidates.toString()}")
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    log("PeerConnection.Observer:onAddStream: $mediaStream")
                }

                override fun onRemoveStream(mediaStream: MediaStream) {
                    log("PeerConnection.Observer:onRemoveStream: $mediaStream")
                }

                override fun onDataChannel(dataChannel: DataChannel) {
                    observeDataChannel(dataChannel)
                    log("PeerConnection.Observer:onDataChannel: $dataChannel")
                }

                override fun onRenegotiationNeeded() {
                    log("PeerConnection.Observer:onRenegotiationNeeded")
                    createOffer()
                }
            }
        ) ?: return

        if (peerConnectionSide == PeerConnectionSide.SENDER) createDataChannel()
    }

    private fun createDataChannel() {
        val dcInit = DataChannel.Init()
        dataChannel = peerConnection.createDataChannel(DC_SIGNALING_LABEL, dcInit)
        dataChannel?.registerObserver(dcObserver)
    }

    private fun observeDataChannel(dataChannel: DataChannel) {
        this.dataChannel = dataChannel
        dataChannel.registerObserver(dcObserver)
    }

    fun createOffer() {
        peerConnection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                log("createOffer:onCreateSuccess")
                setLocalSdp(sdp)
            }

            override fun onSetSuccess() {
                log("createOffer:onSetSuccess")
            }

            override fun onCreateFailure(error: String?) {
                log("createOffer:onCreateFailure: ${error.toString()}")
            }

            override fun onSetFailure(error: String?) {
                log("createOffer:onSetFailure: ${error.toString()}")
            }
        }, MediaConstraints())
    }

    private fun setLocalSdp(sdp: SessionDescription) {
        peerConnection.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
            }

            override fun onSetSuccess() {
                log("setLocalDescription:onSetSuccess")
            }

            override fun onCreateFailure(error: String?) {
            }

            override fun onSetFailure(error: String?) {
                log("setLocalDescription:onSetFailure: ${error.toString()}")
            }
        }, sdp)
    }

    fun getLocalSdp() = peerConnection.localDescription

    fun parseAnswerSdp(answerSdpStr: String) {
        kotlin.runCatching {
            val answerJson = JSONObject(answerSdpStr)
            val sdpType: String = answerJson.getString("sdpType")
            val sdpDescription: String = answerJson.getString("sdpDescription")
            sdpType to sdpDescription
        }
            .onSuccess { (sdpType, sdpDescription) ->
                val answerSdp = SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(sdpType),
                    sdpDescription
                )
                setRemoteSdp(answerSdp)
            }
            .onFailure {
                log(it.toString())
            }
    }

    private fun setRemoteSdp(answerSdp: SessionDescription) {
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
            }

            override fun onSetSuccess() {
                log("setRemoteDescription:onSetSuccess")
            }

            override fun onCreateFailure(error: String?) {
            }

            override fun onSetFailure(error: String?) {
                log("setRemoteDescription:onSetFailure: ${error.toString()}")
            }
        }, answerSdp)
    }

    fun sendMessage(data: TransferProtocol) {
        val buffer = ByteBuffer.wrap(gson.toJson(data).toByteArray(Charsets.UTF_8))
        dataChannel?.send(DataChannel.Buffer(buffer, false))
        log("sendMessage: $data")
    }

    fun sendData(buffer: DataChannel.Buffer) {
        dataChannel?.send(buffer)
    }

    private fun log(message: String) {
        Log.d(tag, message)
        scope.launch {
            _logs.emit(message)
        }
    }

    companion object {
        private const val tag = "ShareRtcLogging"
        private const val DC_SIGNALING_LABEL = "Signaling"
    }
}