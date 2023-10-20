package com.sharertc.model

import java.io.Serializable

data class TransferProtocol(
    val type: String,
    val files: List<FileDescription> = listOf(),
    val processingFile: ProcessingFile? = null
): Serializable

data class FileDescription(
    val name: String,
    val size: Long
): Serializable

data class ProcessingFile(
    val description: FileDescription,
    val bytes: String
): Serializable {
    override fun toString(): String {
        return "ProcessingFile(description=$description, bytesSize=${bytes.length})"
    }
}

const val SendReady = "SendReady"
const val ReceiveReady = "ReceiveReady"
const val FilesInfo = "FilesInfo"
const val FilesInfoReceived = "FilesInfoReceived"
const val Transfer = "Transfer"
const val FileTransferCompleted = "FileTransferCompleted"
const val AllTransfersCompleted = "AllTransfersCompleted"