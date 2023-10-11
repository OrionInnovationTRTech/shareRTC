package com.sharertc

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.sharertc.databinding.ActivitySenderBinding
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

class SenderActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySenderBinding
    private lateinit var peerConnection: PeerConnection
    private val app get() = application as App
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySenderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
    }

    private fun init() {
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

        try {
            val bitmap = generateQRCode(qRCodeData, 300, 300)
            binding.imageView.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
    }

    fun generateQRCode(text: String, width: Int, height: Int): Bitmap {
        val multiFormatWriter = MultiFormatWriter()
        val bitMatrix = multiFormatWriter.encode(text, BarcodeFormat.QR_CODE, width, height)

        // BitMatrix'i Bitmap'e dönüştürme
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                )
            }
        }
        return bitmap
    }

    companion object {
        private const val tag = "SenderActivity"
    }
}