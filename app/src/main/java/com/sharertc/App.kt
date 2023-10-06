package com.sharertc

import android.app.Application
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory

class App: Application() {

    val peerConnectionFactory by lazy {
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

    override fun onCreate() {
        super.onCreate()
    }
}