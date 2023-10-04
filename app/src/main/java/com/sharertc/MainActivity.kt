package com.sharertc

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceConnectionState
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnectionFactory
import org.webrtc.PeerConnectionFactory.InitializationOptions
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription


class MainActivity : AppCompatActivity() {

    private val peerConnectionFactory by lazy {
        InitializationOptions
            .builder(applicationContext)
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
            IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init()
    }

    private fun init() {
        val peerConnection = peerConnectionFactory.createPeerConnection(
            iceServers,
            object : PeerConnection.Observer {
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {

                    Log.d(tag, "onSignalingChange: ${p0.toString()}")
                }

                override fun onIceConnectionChange(p0: IceConnectionState?) {
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

        peerConnection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                Log.d(tag, "onCreateSuccess: ${sdp.toString()}")
                sdp?.let { setUI(it) }
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

        /*val dcInit = DataChannel.Init()
        val dataChannel = peerConnection.createDataChannel("1", dcInit)
        dataChannel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {
                Log.d(tag, "onIceConnectionChange: ${p0.toString()}")
            }

            override fun onStateChange() {
                Log.d(tag, "onIceConnectionChange: ${p0.toString()}")
            }

            override fun onMessage(p0: DataChannel.Buffer?) {
                Log.d(tag, "onIceConnectionChange: ${p0.toString()}")
            }
        })*/
    }

    private fun setUI(sdp: SessionDescription) = runOnUiThread {
        val sdpType = findViewById<TextView>(R.id.tvSdpType)
        val sdpDescription = findViewById<TextView>(R.id.tvSdpDescription)
        sdpType.text = sdp.type.toString()
        sdpDescription.text = sdp.description
    }
    
    companion object {
        private const val tag = "ShareRTC"
    }
}