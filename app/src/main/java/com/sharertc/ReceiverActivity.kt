package com.sharertc

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.sharertc.databinding.ActivityReceiverBinding


class ReceiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiverBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
