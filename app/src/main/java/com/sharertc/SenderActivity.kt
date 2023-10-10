package com.sharertc

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sharertc.databinding.ActivitySenderBinding
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer


class SenderActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySenderBinding
    private lateinit var peerConnection: PeerConnection
    private lateinit var dataChannel: DataChannel
    private val app get() = application as App
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySenderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initPeerConnection()
        init()
    }

    private fun initPeerConnection() {
        peerConnection = app.peerConnectionFactory.createPeerConnection(
            app.iceServers,
            object : PeerConnection.Observer {
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
                    Log.d(tag, "onSignalingChange: ${p0.toString()}")
                }

                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
                    Log.d(tag, "onIceConnectionChange: ${p0.toString()}")
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    Log.d(tag, "onIceConnectionReceivingChange: $p0")
                }

                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                    Log.d(tag, "onIceGatheringChange: ${p0.toString()}")
                }

                override fun onIceCandidate(p0: IceCandidate?) {
                    Log.d(tag, "onIceCandidate: ${p0.toString()}")
                }

                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {
                    Log.d(tag, "onIceCandidatesRemoved: ${p0.toString()}")
                }

                override fun onAddStream(p0: MediaStream?) {
                    Log.d(tag, "onAddStream: ${p0.toString()}")
                }

                override fun onRemoveStream(p0: MediaStream?) {
                    Log.d(tag, "onRemoveStream: ${p0.toString()}")
                }

                override fun onDataChannel(p0: DataChannel?) {
                    Log.d(tag, "onDataChannel: ${p0.toString()}")
                }

                override fun onRenegotiationNeeded() {
                    Log.d(tag, "onRenegotiationNeeded")
                }

            }
        ) ?: return

        createDataChannel()
        createOffer()
    }

    private fun createOffer() {
        peerConnection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Log.d(tag, "onCreateSuccess: ${sdp.toString()}")
                sdp?.let {
                    setLocalSdp(it)
                }
            }

            override fun onSetSuccess() {
                Log.d(tag, "onSetSuccess")
            }

            override fun onCreateFailure(error: String?) {
                Log.d(tag, "onCreateFailure: ${error.toString()}")
            }

            override fun onSetFailure(p0: String?) {
                Log.d(tag, "onSetFailure: ${p0.toString()}")
            }
        }, MediaConstraints())
    }

    private fun createDataChannel() {
        val dcInit = DataChannel.Init()
        dataChannel = peerConnection.createDataChannel("dc", dcInit)
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {
                Log.d(tag, "onBufferedAmountChange: $p0")
            }

            override fun onStateChange() {
                Log.d(tag, "onStateChange")
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data: ByteBuffer = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                val message = String(bytes)
                Log.d(tag, "onMessage: $message")
            }
        })
    }

    private fun init() {
        binding.btnStartConnection.setOnClickListener {
            val answerSdpStr = binding.etAnswerSdp.text.toString()
            if (answerSdpStr.isBlank()) {
                Toast.makeText(this, "Öncelikle answer sdp json değerini girmelisiniz!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parseAnswerSdp(answerSdpStr)
        }
    }

    private fun parseAnswerSdp(answerSdpStr: String) {
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
                Toast.makeText(this, "Geçersiz answer sdp json değeri girildi!", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun setRemoteSdp(answerSdp: SessionDescription) {
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
            }

            override fun onSetSuccess() {
                sendMessage("Selam ben Berivan!")
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
                Log.d(tag, "onSetFailure: ${p0.toString()}")
            }
        }, answerSdp)
    }

    private fun sendMessage(message: String) {
        val buffer = ByteBuffer.wrap(message.toByteArray())
        dataChannel.send(DataChannel.Buffer(buffer, false))
    }

    private fun setLocalSdp(sdp: SessionDescription) {
        peerConnection.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
            }

            override fun onSetSuccess() {
                Log.d(tag, "onSetSuccess")
                generateQRCode(sdp)
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
                Log.d(tag, "onSetFailure: ${p0.toString()}")
            }
        }, sdp)
    }

    private fun generateQRCode(sdp: SessionDescription) = runOnUiThread {
        val json = JSONObject()
        json.put("sdpType", sdp.type.canonicalForm())
        json.put("sdpDescription", sdp.description)

        val qRCodeData: String = json.toString()
        binding.tvSdpJson.text = qRCodeData
        // Bu noktadan sonra sdp bilgileri ile qr kod oluşturulabilir
    }

    companion object {
        private const val tag = "SenderActivity"
    }
}