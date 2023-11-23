package com.sharertc.model

import java.io.Serializable

data class TransferProtocol(
    val type: String,
    val files: List<FileDescription> = listOf(),
    val processingFile: FileDescription? = null
): Serializable

data class FileDescription(
    val name: String,
    val size: Long
): Serializable

const val SendReady = "SendReady"
const val ReceiveReady = "ReceiveReady"
const val FilesInfo = "FilesInfo"
const val FilesInfoReceived = "FilesInfoReceived"
const val FileTransferStart = "FileTransferStart"
const val FileTransferCompleted = "FileTransferCompleted"
const val AllTransfersCompleted = "AllTransfersCompleted"