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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer

abstract class PeerConnectionManager(
    private val context: Context
) {

    protected val peerConnectionFactory: PeerConnectionFactory by lazy {
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

    protected val iceServers by lazy {
        listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
    }

    val gson by lazy {
        Gson()
    }

    protected lateinit var peerConnection: PeerConnection
    protected var dataChannel: DataChannel? = null

    protected abstract val pcObserver: PeerConnection.Observer
    protected abstract val dcObserver: DataChannel.Observer

    protected val scope = CoroutineScope(Job() + Dispatchers.Default)

    private val _logs = MutableSharedFlow<String>()
    val logs: Flow<String> = _logs

    protected val _messages = MutableSharedFlow<TransferProtocol>()
    val messages: Flow<TransferProtocol> = _messages

    protected val _pcState = MutableStateFlow(PeerConnection.IceConnectionState.NEW)
    val pcState: Flow<PeerConnection.IceConnectionState> = _pcState

    protected val _dcState = MutableStateFlow(DataChannel.State.CLOSED)
    val dcState: Flow<DataChannel.State> = _dcState

    protected val _sdpToQR = MutableSharedFlow<SessionDescription>()
    val qrStr: Flow<String> = _sdpToQR.map(::toSdpString)

    protected abstract fun initPeerConnection()

    protected fun observeDataChannel(dataChannel: DataChannel) {
        this.dataChannel = dataChannel
        dataChannel.registerObserver(dcObserver)
    }

    protected fun setLocalSdp(
        sdp: SessionDescription,
        onSuccess: (() -> Unit)? = null,
        onFailure: (() -> Unit)? = null
    ) {
        peerConnection.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
            }

            override fun onSetSuccess() {
                log("setLocalDescription:onSetSuccess")
                onSuccess?.invoke()
            }

            override fun onCreateFailure(error: String?) {
            }

            override fun onSetFailure(error: String?) {
                log("setLocalDescription:onSetFailure: ${error.toString()}")
                onFailure?.invoke()
            }
        }, sdp)
    }

    protected fun setRemoteSdp(
        answerSdp: SessionDescription,
        onSuccess: (() -> Unit)? = null,
        onFailure: (() -> Unit)? = null
    ) {
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
            }

            override fun onSetSuccess() {
                log("setRemoteDescription:onSetSuccess")
                onSuccess?.invoke()
            }

            override fun onCreateFailure(error: String?) {
            }

            override fun onSetFailure(error: String?) {
                log("setRemoteDescription:onSetFailure: ${error.toString()}")
                onFailure?.invoke()
            }
        }, answerSdp)
    }

    fun toSdp(sdpStr: String): SessionDescription? {
        return kotlin.runCatching {
            val answerJson = JSONObject(sdpStr)
            val sdpType: String = answerJson.getString("sdpType")
            val sdpDescription: String = answerJson.getString("sdpDescription")
            sdpType to sdpDescription
        }.onFailure {
            log(it.toString())
        }.getOrNull()?.let { (sdpType, sdpDescription) ->
            SessionDescription(
                SessionDescription.Type.fromCanonicalForm(sdpType),
                sdpDescription
            )
        }
    }

    private fun toSdpString(sdp: SessionDescription): String {
        val json = JSONObject()
        json.put("sdpType", sdp.type.canonicalForm())
        json.put("sdpDescription", sdp.description)
        return json.toString()
    }

    fun sendMessage(data: TransferProtocol) {
        val buffer = ByteBuffer.wrap(gson.toJson(data).toByteArray(Charsets.UTF_8))
        dataChannel?.send(DataChannel.Buffer(buffer, false))
        log("sendMessage: $data")
    }

    fun sendData(buffer: DataChannel.Buffer) {
        dataChannel?.send(buffer)
    }

    protected open fun log(message: String) {
        Log.d(tag, message)
        scope.launch {
            _logs.emit(message)
        }
    }

    companion object {
        private const val tag = "ShareRtcLogging"
        internal const val DC_SIGNALING_LABEL = "Signaling"
    }
}