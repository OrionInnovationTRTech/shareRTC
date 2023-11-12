package com.sharertc.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.asLiveData
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import com.google.zxing.WriterException
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import com.sharertc.App
import com.sharertc.databinding.ActivityReceiverBinding
import com.sharertc.model.FileDescription
import com.sharertc.model.FileTransferCompleted
import com.sharertc.model.FileTransferStart
import com.sharertc.model.FilesInfo
import com.sharertc.model.FilesInfoReceived
import com.sharertc.model.ReceiveReady
import com.sharertc.model.SendReady
import com.sharertc.model.TransferProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer


/**
 * Activity for the receiver client side
 */
class ReceiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiverBinding
    private lateinit var peerConnection: PeerConnection
    private lateinit var dataChannel: DataChannel

    private val REQUEST_CODE_QR_PERMISSION = 101
    private val REQUEST_CODE_FILE_PERMISSION = 102
    private val REQUIRED_QR_PERMISSION = arrayOf(Manifest.permission.CAMERA)
    private val REQUIRED_FILE_PERMISSION = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private val app get() = application as App

    private var files: List<FileDescription> = listOf()
    private var receivedBytes = 0
    private var processingFile: FileDescription? = null
    private var outputStream: OutputStream? = null

    private val progress = MutableStateFlow(0)

    private var baseDocumentTreeUri: Uri? = null
    private val selectDocumentTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        baseDocumentTreeUri = uri
        if (uri != null) sendMessage(TransferProtocol(ReceiveReady))
    }

    /**
     * Start point of the activity
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initPeerConnection()
        init()

        progress.asLiveData().distinctUntilChanged().observe(this) {
            processingFile?.name?.let { name ->
                log("receiveFile -> name: $name progress: %$it")
            }
        }
    }

    private fun allPermissionsGranted(permissions: Array<String>) = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startQRScanner() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Scan Offer Sdp QR")
        integrator.setCameraId(0)
        integrator.initiateScan()
    }

    private fun initPeerConnection() {
        peerConnection = app.peerConnectionFactory.createPeerConnection(
            app.iceServers,
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
                    sendAnswerSdp(peerConnection.localDescription)
                    log("PeerConnection.Observer:onIceGatheringChange: ${state.toString()}")
                }

                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    if (iceCandidate.serverUrl.contains("google.com")) {
                        sendAnswerSdp(peerConnection.localDescription)
                    }
                    log("PeerConnection.Observer:onIceCandidate: $iceCandidate")
                }

                override fun onIceCandidatesRemoved(iceCandidates: Array<out IceCandidate>?) {
                    log("PeerConnection.Observer:onIceCandidatesRemoved: ${iceCandidates.toString()}")
                }

                override fun onAddStream(stream: MediaStream?) {
                    log("PeerConnection.Observer:onAddStream: ${stream.toString()}")
                }

                override fun onRemoveStream(stream: MediaStream?) {
                    log("PeerConnection.Observer:onRemoveStream: ${stream.toString()}")
                }

                override fun onDataChannel(dataChannel: DataChannel) {
                    observeDataChannel(dataChannel)
                    log("PeerConnection.Observer:onDataChannel: $dataChannel")
                }

                override fun onRenegotiationNeeded() {
                    log("PeerConnection.Observer:onRenegotiationNeeded")
                }
            }
        ) ?: return
    }


    private fun handleMessage(data: TransferProtocol?) {
        when(data?.type) {
            SendReady -> {
                if (SDK_INT >= TIRAMISU) {
                    if (baseDocumentTreeUri == null) {
                        selectDocumentTree.launch(null)
                    } else {
                        sendMessage(TransferProtocol(ReceiveReady))
                    }
                } else {
                    if (allPermissionsGranted(REQUIRED_FILE_PERMISSION)) {
                        sendMessage(TransferProtocol(ReceiveReady))
                    } else {
                        ActivityCompat.requestPermissions(this, REQUIRED_FILE_PERMISSION, REQUEST_CODE_FILE_PERMISSION)
                    }
                }
            }
            FilesInfo -> {
                files = data.files
                sendMessage(TransferProtocol(FilesInfoReceived))
            }
            FileTransferStart -> openOutputStream(data.processingFile!!)
            FileTransferCompleted -> finishFileWrite()
            else -> {}
        }
    }

    private fun openOutputStream(fileDescription: FileDescription) {
        when {
            SDK_INT >= TIRAMISU -> {
                if (baseDocumentTreeUri == null) {
                    log("Cannot find selected directory to save files!")
                    return
                }
                val directory = DocumentFile.fromTreeUri(this, baseDocumentTreeUri!!)
                val file = directory?.createFile("application/octet-stream", fileDescription.name)
                file?.uri?.let { uri ->
                    outputStream = contentResolver.openOutputStream(uri)
                }
            }
            SDK_INT >= Q -> {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileDescription.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val uriData = contentResolver.insert(contentUri, contentValues)
                uriData?.let { uri ->
                    outputStream = contentResolver.openOutputStream(uri)
                }
            }
            else -> {
                val downloadsDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDirectory.exists()) downloadsDirectory.mkdirs()

                val file = File(downloadsDirectory, fileDescription.name)

                try {
                    outputStream = FileOutputStream(file)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        if (outputStream != null) {
            processingFile = fileDescription
            lifecycleScope.launch(Dispatchers.Default) { progress.emit(0) }
        } else {
            log("outputStream is null!")
        }
    }

    private fun writeToFile(data: ByteBuffer) {
        val size = data.capacity()
        val bytes = ByteArray(size)
        data.get(bytes)
        outputStream?.write(bytes)
        receivedBytes += size
        lifecycleScope.launch(Dispatchers.Default) {
            processingFile?.size?.let { fileSize ->
                val progress: Int = ((receivedBytes * 100) / fileSize).toInt()
                this@ReceiverActivity.progress.emit(progress)
            }
        }
    }

    private fun finishFileWrite() {
        outputStream?.close()
        outputStream = null
        receivedBytes = 0
        processingFile = null
        lifecycleScope.launch(Dispatchers.Default) { progress.emit(100) }
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
        binding.btnScanOfferSdpQr.setOnClickListener {
            if (allPermissionsGranted(REQUIRED_QR_PERMISSION)) {
                startQRScanner()
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_QR_PERMISSION, REQUEST_CODE_QR_PERMISSION)
            }
        }
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
            override fun onCreateSuccess(sdp: SessionDescription?) {
            }

            override fun onSetSuccess() {
                log("setRemoteDescription:onSetSuccess")
                createAnswerAndSetLocalDescription()
            }

            override fun onCreateFailure(error: String?) {
            }

            override fun onSetFailure(error: String?) {
                log("setRemoteDescription:onSetFailure: ${error.toString()}")
            }
        }, offerSdp)
    }

    internal fun createAnswerAndSetLocalDescription() {
        peerConnection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(answerSdp: SessionDescription) {
                log("createAnswer:onCreateSuccess")
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
                }, answerSdp)
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

    internal fun sendAnswerSdp(answerSdp: SessionDescription) = runOnUiThread {
        val answerJson = JSONObject()
        answerJson.put("sdpType", answerSdp.type.canonicalForm())
        answerJson.put("sdpDescription", answerSdp.description)
        val answerData = answerJson.toString()
        binding.tvAnswerSdpJson.text = answerData

        try {
            val bitmap = app.generateQRCode(answerData)
            binding.ivAnswerSdpQr.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            REQUEST_CODE_QR_PERMISSION -> {
                if (allPermissionsGranted(REQUIRED_QR_PERMISSION)) {
                    startQRScanner()
                } else {
                    Toast.makeText(
                        this,
                        "Kamera izni verilmedi, QR kod okuma işlemi yapılamaz.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            REQUEST_CODE_FILE_PERMISSION -> {
                if (allPermissionsGranted(REQUIRED_FILE_PERMISSION)) {
                    sendMessage(TransferProtocol(ReceiveReady))
                } else {
                    Toast.makeText(
                        this,
                        "Depolama izni verilmedi, dosya transferi gerçekleşemez!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
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
                binding.etOfferSdp.setText(qrResult)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun sendMessage(data: TransferProtocol) {
        val buffer = ByteBuffer.wrap(app.gson.toJson(data).toByteArray(Charsets.UTF_8))
        dataChannel.send(DataChannel.Buffer(buffer, false))
        log("sendMessage: $data")
    }

    @SuppressLint("SetTextI18n")
    internal fun log(message: String) = lifecycleScope.launch(Dispatchers.Main) {
        Log.d(tag, message)
        binding.etLogs.text = "--$message\n${binding.etLogs.text}"
    }

    companion object {
        private const val tag = "ShareRtcLogging"
    }
}
