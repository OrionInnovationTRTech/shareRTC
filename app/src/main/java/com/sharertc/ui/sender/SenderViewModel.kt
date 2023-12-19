package com.sharertc.ui.sender

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import com.sharertc.util.SenderPCM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import java.io.IOException
import java.nio.ByteBuffer

class SenderViewModel(application: Application): AndroidViewModel(application) {

    private val app = application as App
    private val pcm = SenderPCM(app)

    var files: ArrayList<FileDescription> = arrayListOf()
    var processingFile: FileDescription? = null
    var uris: List<Uri> = listOf()
    var sendInfo : Boolean = false

   fun handleSendInfo (newSendInfo:Boolean){
       sendInfo = newSendInfo
   }


    private val _progress = MutableStateFlow(0)
    val progress: LiveData<Int> = _progress.map {
        it.coerceAtLeast(0).coerceAtMost(100)
    }.asLiveData(viewModelScope.coroutineContext)

    val qrStr: LiveData<String> = pcm.qrStr.asLiveData(viewModelScope.coroutineContext)
    val pcState: LiveData<PeerConnection.IceConnectionState> = pcm.pcState.asLiveData(viewModelScope.coroutineContext)
    val dcState: LiveData<DataChannel.State> = pcm.dcState.asLiveData(viewModelScope.coroutineContext)
    val logs: Flow<String> = pcm.logs


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
        handleSendInfo(true)
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
                handleSendInfo(true)
                sendMessage(TransferProtocol(FileTransferCompleted, processingFile = fileDescription))


            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}