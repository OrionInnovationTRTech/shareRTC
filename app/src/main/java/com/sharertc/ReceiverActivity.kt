package com.sharertc

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sharertc.databinding.ActivityReceiverBinding
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer


class ReceiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiverBinding
    private lateinit var peerConnection: PeerConnection
    private lateinit var dataChannel: DataChannel
    private val app get() = application as App
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initPeerConnection()
        init()
    }

    private fun initPeerConnection() {
        peerConnection = app.peerConnectionFactory.createPeerConnection(
            app.iceServers,
            object : PeerConnection.Observer {
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
                    log("PeerConnection.Observer:onSignalingChange: ${p0.toString()}")
                }

                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                    log("PeerConnection.Observer:onIceConnectionChange: ${p0.toString()}")
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    log(":PeerConnection.Observer:onIceConnectionReceivingChange: $p0")
                }

                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                    sendAnswerSdp(peerConnection.localDescription)
                    log("PeerConnection.Observer:onIceGatheringChange: ${p0.toString()}")
                }

                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    if (iceCandidate.serverUrl.contains("google.com")) {
                        sendAnswerSdp(peerConnection.localDescription)
                    }
                    log("PeerConnection.Observer:onIceCandidate: $iceCandidate")
                }

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                    log("PeerConnection.Observer:onIceCandidatesRemoved: ${p0.toString()}")
                }

                override fun onAddStream(p0: MediaStream?) {
                    log("PeerConnection.Observer:onAddStream: ${p0.toString()}")
                }

                override fun onRemoveStream(p0: MediaStream?) {
                    log("PeerConnection.Observer:onRemoveStream: ${p0.toString()}")
                }

                override fun onDataChannel(p0: DataChannel?) {
                    log("PeerConnection.Observer:onDataChannel: ${p0.toString()}")
                }

                override fun onRenegotiationNeeded() {
                    log("PeerConnection.Observer:onRenegotiationNeeded")
                }
            }
        ) ?: return

        createDataChannel()
    }

    private fun createDataChannel() {
        val dcInit = DataChannel.Init()
        dataChannel = peerConnection.createDataChannel("dc", dcInit)
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {
                log("DataChannel.Observer:onBufferedAmountChange: $p0")
            }

            override fun onStateChange() {
                val state = dataChannel.state()
                log("DataChannel.Observer:onStateChange -> state: $state")
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data: ByteBuffer = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                val message = String(bytes)
                log("DataChannel.Observer:onMessage: $message")
            }
        })
    }

    private fun init() {
        binding.btnGenerateAnswerSdp.setOnClickListener {
            val offerSdpStr = binding.etOfferSdp.text.toString()
            if (offerSdpStr.isBlank()) {
                Toast.makeText(this, "Öncelikle offer sdp json değerini girmelisiniz!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parseOfferSdp(offerSdpStr)
        }
        binding.tvAnswerSdpJson.setOnKeyListener { _, _, _ -> true }
    }

    private fun parseOfferSdp(offerSdpStr: String) {
        kotlin.runCatching {
            val offerJson = JSONObject(offerSdpStr)
            val sdpType: String = offerJson.getString("sdpType")
            val sdpDescription: String = offerJson.getString("sdpDescription")
            sdpType to sdpDescription
        }
        .onSuccess { (sdpType, sdpDescription) ->
            val offerSdp = SessionDescription(
                SessionDescription.Type.fromCanonicalForm(sdpType),
                sdpDescription
            )
            setRemoteSdp(offerSdp)
        }
        .onFailure {
            Toast.makeText(this, "Geçersiz offer sdp json değeri girildi!", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun setRemoteSdp(offerSdp: SessionDescription) {
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
            }

            override fun onSetSuccess() {
                log("setRemoteDescription:onSetSuccess")
                createAnswerAndSetLocalDescription()
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
                log("setRemoteDescription:onSetFailure: ${p0.toString()}")
            }
        }, offerSdp)
    }

    private fun createAnswerAndSetLocalDescription() {
        peerConnection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(answerSdp: SessionDescription) {
                log("createAnswer:onCreateSuccess")
                peerConnection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }

                    override fun onSetSuccess() {
                        log("setLocalDescription:onSetSuccess")
                        sendAnswerSdp(answerSdp)
                    }

                    override fun onCreateFailure(p0: String?) {
                    }

                    override fun onSetFailure(p0: String?) {
                        log("setLocalDescription:onSetFailure: ${p0.toString()}")
                    }
                }, answerSdp)
            }

            override fun onSetSuccess() {
            }

            override fun onCreateFailure(p0: String?) {
                log("createAnswer:onCreateFailure: ${p0.toString()}")
            }

            override fun onSetFailure(p0: String?) {
            }
        }, MediaConstraints())
    }

    private fun sendAnswerSdp(answerSdp: SessionDescription) = runOnUiThread {
        val answerJson = JSONObject()
        answerJson.put("sdpType", answerSdp.type.canonicalForm())
        answerJson.put("sdpDescription", answerSdp.description)
        val answerData = answerJson.toString()
        binding.tvAnswerSdpJson.text = answerData
    }

    @SuppressLint("SetTextI18n")
    private fun log(message: String) = runOnUiThread {
        Log.d(tag, message)
        binding.etLogs.text = "--$message\n${binding.etLogs.text}"
    }

    companion object {
        private const val tag = "ShareRtcLogging"
    }
}
