package com.sharertc.ui.sender

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sharertc.R
import com.sharertc.databinding.ActivitySenderBinding
import com.sharertc.model.FileDescription
import com.sharertc.model.SendReady
import com.sharertc.model.TransferProtocol
import com.sharertc.util.generateQRCode
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.PeerConnection


/**
 * Activity for the sender client side
 */
class SenderActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySenderBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySenderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        }

}