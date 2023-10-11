package com.sharertc

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.WriterException
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
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

    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private val app get() = application as App

    private var messageCounter: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySenderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initPeerConnection()
        init()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun initPeerConnection() {
        peerConnection = app.peerConnectionFactory.createPeerConnection(
            app.iceServers,
            object : PeerConnection.Observer {
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
                    log("PeerConnection.Observer:onSignalingChange: ${p0.toString()}")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    runOnUiThread {
                        val isEnabled = state == PeerConnection.IceConnectionState.CONNECTED ||
                                state == PeerConnection.IceConnectionState.COMPLETED
                        binding.btnSendData.isEnabled = isEnabled
                    }
                    log("PeerConnection.Observer:onIceConnectionChange: ${state.toString()}")
                }

                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    log(":PeerConnection.Observer:onIceConnectionReceivingChange: $p0")
                }

                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
                    generateQRCode(peerConnection.localDescription)
                    log("PeerConnection.Observer:onIceGatheringChange: ${p0.toString()}")
                }

                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    if (iceCandidate.serverUrl.contains("google.com")) {
                        generateQRCode(peerConnection.localDescription)
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
                    createOffer()
                }
            }
        ) ?: return

        createDataChannel()
    }

    private fun createOffer() {
        peerConnection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                log("createOffer:onCreateSuccess")
                sdp?.let {
                    setLocalSdp(it)
                }
            }

            override fun onSetSuccess() {
                log("createOffer:onSetSuccess")
            }

            override fun onCreateFailure(error: String?) {
                log("createOffer:onCreateFailure: ${error.toString()}")
            }

            override fun onSetFailure(p0: String?) {
                log("createOffer:onSetFailure: ${p0.toString()}")
            }
        }, MediaConstraints())
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
        binding.btnStartConnection.setOnClickListener {
            val answerSdpStr = binding.etAnswerSdp.text.toString()
            if (answerSdpStr.isBlank()) {
                Toast.makeText(this, "Öncelikle answer sdp json değerini girmelisiniz!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parseAnswerSdp(answerSdpStr)
        }
        binding.btnScanAnswerSdpQr.setOnClickListener {
            if (allPermissionsGranted()) {
                startQRScanner()
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }
        }
        binding.btnSendData.setOnClickListener {
            sendMessage("Hi, new value ${++messageCounter}")
        }
    }

    private fun startQRScanner() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Scan Answer Sdp QR")
        integrator.setCameraId(0)
        integrator.initiateScan()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startQRScanner()
            } else {
                Toast.makeText(
                    this,
                    "Kamera izni verilmedi, QR kod okuma işlemi yapılamaz.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result: IntentResult? =
            IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this, "QR kod bulunamadı", Toast.LENGTH_SHORT).show()
            } else {
                //Toast.makeText(this, "QR Kod: ${result.contents}", Toast.LENGTH_SHORT).show()
                val qrResult = result.contents
                binding.etAnswerSdp.setText(qrResult)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
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
                log("setRemoteDescription:onSetSuccess")
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
                log("setRemoteDescription:onSetFailure: ${p0.toString()}")
            }
        }, answerSdp)
    }

    private fun sendMessage(message: String) {
        val buffer = ByteBuffer.wrap(message.toByteArray())
        dataChannel.send(DataChannel.Buffer(buffer, false))
        log("sendMessage:message: $message")
    }

    private fun setLocalSdp(sdp: SessionDescription) {
        peerConnection.setLocalDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
            }

            override fun onSetSuccess() {
                log("setLocalDescription:onSetSuccess")
            }

            override fun onCreateFailure(p0: String?) {
            }

            override fun onSetFailure(p0: String?) {
                log("setLocalDescription:onSetFailure: ${p0.toString()}")
            }
        }, sdp)
    }

    private fun generateQRCode(sdp: SessionDescription) = runOnUiThread {
        val json = JSONObject()
        json.put("sdpType", sdp.type.canonicalForm())
        json.put("sdpDescription", sdp.description)

        val qRCodeData: String = json.toString()
        binding.tvOfferSdpJson.text = qRCodeData

        try {
            val bitmap = app.generateQRCode(qRCodeData)
            binding.ivOfferSdpQr.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
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