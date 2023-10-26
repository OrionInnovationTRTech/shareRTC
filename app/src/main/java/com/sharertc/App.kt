package com.sharertc

import android.app.Application
import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory

class App: Application() {

    val peerConnectionFactory: PeerConnectionFactory by lazy {
        PeerConnectionFactory.InitializationOptions
            .builder(applicationContext)
            .createInitializationOptions().also {
                PeerConnectionFactory.initialize(it)
            }
        PeerConnectionFactory
            .builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    val iceServers by lazy {
        listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
    }

    val gson by lazy {
        Gson()
    }

    override fun onCreate() {
        super.onCreate()
    }

    fun generateQRCode(text: String, width: Int = 900, height: Int = 900): Bitmap {
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
        const val DC_SIGNALING_LABEL = "Signaling"
    }
}