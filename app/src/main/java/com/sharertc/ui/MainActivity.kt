package com.sharertc.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sharertc.databinding.ActivityMainBinding
import com.sharertc.ui.receiver.ReceiverActivity
import com.sharertc.ui.sender.SenderActivity


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setClickListeners()
    }

    private fun setClickListeners() {
        binding.btnSender.setOnClickListener {
            val intent = Intent(this, SenderActivity::class.java)
            startActivity(intent)
        }
        binding.btnReceiver.setOnClickListener {
            val intent = Intent(this, ReceiverActivity::class.java)
            startActivity(intent)
        }
    }
}