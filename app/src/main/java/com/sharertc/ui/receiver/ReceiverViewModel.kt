package com.sharertc.ui.receiver

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.sharertc.App
import com.sharertc.model.FileDescription
import com.sharertc.model.FileTransferCompleted
import com.sharertc.model.FileTransferStart
import com.sharertc.model.FilesInfo
import com.sharertc.model.FilesInfoReceived
import com.sharertc.model.ReceiveReady
import com.sharertc.model.SendReady
import com.sharertc.model.TransferProtocol
import com.sharertc.util.ReceiverPCM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.nio.ByteBuffer

class ReceiverViewModel(application: Application): AndroidViewModel(application) {

    private val app = application as App
    private val pcm = ReceiverPCM(app, ::writeToFile)

    private var files: List<FileDescription> = listOf()
    private var receivedBytes = 0
    private var processingFile: FileDescription? = null
    private var outputStream: OutputStream? = null
    var baseDocumentTreeUri: Uri? = null

    private val _progress = MutableStateFlow(0)
    val progress: LiveData<Int> = _progress.map {
        it.coerceAtLeast(0).coerceAtMost(100)
    }.asLiveData(viewModelScope.coroutineContext)

    private val _documentTreeLauncher = MutableSharedFlow<Unit>()
    val documentTreeLauncher: Flow<Unit> = _documentTreeLauncher

    val qrStr: LiveData<String> = pcm.qrStr.asLiveData(viewModelScope.coroutineContext)
    val logs: Flow<String> = pcm.logs

    init {
        viewModelScope.launch {
            pcm.messages.collect {
                handleMessage(it)
            }
        }
    }

    private fun handleMessage(data: TransferProtocol?) {
        when(data?.type) {
            SendReady -> {
                if (baseDocumentTreeUri == null) {
                    viewModelScope.launch {
                        _documentTreeLauncher.emit(Unit)
                    }
                } else {
                    pcm.sendMessage(TransferProtocol(ReceiveReady))
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

    fun sendMessage(data: TransferProtocol) = pcm.sendMessage(data)

    fun parseOfferSdp(offerSdpStr: String) = pcm.parseOfferSdp(offerSdpStr)

    private fun openOutputStream(fileDescription: FileDescription) {
        if (baseDocumentTreeUri == null) {
            pcm.log("Cannot find selected directory to save files!")
            return
        }
        val directory = DocumentFile.fromTreeUri(app, baseDocumentTreeUri!!)
        val file = directory?.createFile("application/octet-stream", fileDescription.name)
        file?.uri?.let { uri ->
            outputStream = app.contentResolver.openOutputStream(uri)
        }
        if (outputStream != null) {
            processingFile = fileDescription
            viewModelScope.launch(Dispatchers.Default) { _progress.emit(0) }
        } else {
            pcm.log("outputStream is null!")
        }
    }

    private fun writeToFile(data: ByteBuffer) {
        val size = data.capacity()
        val bytes = ByteArray(size)
        data.get(bytes)
        outputStream?.write(bytes)
        receivedBytes += size
        viewModelScope.launch(Dispatchers.Default) {
            processingFile?.size?.let { fileSize ->
                val progress: Int = ((receivedBytes * 100) / fileSize).toInt()
                _progress.emit(progress)
            }
        }
    }

    private fun finishFileWrite() {
        outputStream?.close()
        outputStream = null
        receivedBytes = 0
        processingFile = null
        viewModelScope.launch(Dispatchers.Default) { _progress.emit(100) }
    }
}