package com.sharertc.ui.sender

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.sharertc.App
import com.sharertc.model.AllTransfersCompleted
import com.sharertc.model.FileDescription
import com.sharertc.model.FileTransferCompleted
import com.sharertc.model.FileTransferStart
import com.sharertc.model.FilesInfo
import com.sharertc.model.FilesInfoReceived
import com.sharertc.model.ReceiveReady
import com.sharertc.model.TransferProtocol
import com.sharertc.util.PeerConnectionManager
import com.sharertc.util.PeerConnectionSide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.DataChannel
import java.io.IOException
import java.nio.ByteBuffer

class SenderViewModel(application: Application): AndroidViewModel(application) {

    private val app = application as App
    private val pcm = PeerConnectionManager(app, PeerConnectionSide.SENDER)

    var files: ArrayList<FileDescription> = arrayListOf()
    private var processingFile: FileDescription? = null
    var uris: List<Uri> = listOf()

    private val _progress = MutableStateFlow(0)
    val progress: LiveData<Int> = _progress.asLiveData(viewModelScope.coroutineContext)

    val qrStr: LiveData<String> = pcm.sdpToQR.map { sdp ->
        val json = JSONObject()
        json.put("sdpType", sdp.type.canonicalForm())
        json.put("sdpDescription", sdp.description)
        json.toString()
    }.asLiveData(viewModelScope.coroutineContext)

    init {
        viewModelScope.launch {
            pcm.messages.collect {
                handleMessage(it)
            }
        }
    }

    fun sendMessage(data: TransferProtocol) = pcm.sendMessage(data)

    fun parseAnswerSdp(answerSdpStr: String) = pcm.parseAnswerSdp(answerSdpStr)

    private fun handleMessage(data: TransferProtocol?) {
        when(data?.type) {
            ReceiveReady -> sendMessage(TransferProtocol(FilesInfo, files))
            FilesInfoReceived -> sendFiles()
            else -> {}
        }
    }

    private fun sendFiles() = viewModelScope.launch(Dispatchers.Default) {
        files.forEachIndexed { index, fileDescription ->
            sendFile(uris[index], fileDescription)
            delay(300)
        }
        sendMessage(TransferProtocol(AllTransfersCompleted))
        processingFile = null
    }

    private suspend fun sendFile(uri: Uri, fileDescription: FileDescription) {
        withContext(Dispatchers.IO) {
            try {
                sendMessage(TransferProtocol(FileTransferStart, processingFile = fileDescription))
                _progress.emit(0)
                processingFile = fileDescription
                delay(300)
                app.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                    val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytesRead = 0
                    while (input.read(bytes).also { totalBytesRead += it } >= 0) {
                        val progress: Int = ((totalBytesRead * 100) / fileDescription.size).toInt()
                        val buffer = DataChannel.Buffer(ByteBuffer.wrap(bytes), true)
                        pcm.sendData(buffer)
                        _progress.emit(progress)
                    }
                }
                sendMessage(TransferProtocol(FileTransferCompleted, processingFile = fileDescription))
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}