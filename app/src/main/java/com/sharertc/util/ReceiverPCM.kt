package com.sharertc.util

import android.content.Context
import com.sharertc.model.TransferProtocol
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer

class ReceiverPCM(
    context: Context,
    private val writeToFile: (ByteBuffer) -> Unit
) : PeerConnectionManager(context) {

    override val pcObserver: PeerConnection.Observer = object : PeerConnection.Observer {
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
        }
    }

    override val dcObserver: DataChannel.Observer = object : DataChannel.Observer {
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
                writeToFile(buffer.data)
            } else {
                val transferProtocol = gson.fromJson(Charsets.UTF_8.decode(buffer.data).toString(), TransferProtocol::class.java)
                scope.launch {
                    _messages.emit(transferProtocol)
                }
                log("DataChannel.Observer:onMessage: $transferProtocol")
            }
        }
    }

    init {
        initPeerConnection()
    }

    override fun initPeerConnection() {
        peerConnection = peerConnectionFactory.createPeerConnection(iceServers, pcObserver) ?: return
    }

    fun parseOfferSdp(offerSdpStr: String) {
        toSdp(offerSdpStr)?.let {
            setRemoteSdp(it, onSuccess = {
                createAnswerAndSetLocalDescription()
            })
        }
    }

    private fun createAnswerAndSetLocalDescription() {
        peerConnection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(answerSdp: SessionDescription) {
                log("createAnswer:onCreateSuccess")
                setLocalSdp(answerSdp)
            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(error: String?) {
                log("createAnswer:onCreateFailure: ${error.toString()}")
            }

            override fun onSetFailure(error: String?) {
            }
        }, MediaConstraints())
    }

    public override fun log(message: String) = super.log(message)
}