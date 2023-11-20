package com.sharertc.ui.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sharertc.databinding.ActivityReceiverBinding
import com.sharertc.model.ReceiveReady
import com.sharertc.model.TransferProtocol
import com.sharertc.util.generateQRCode
import kotlinx.coroutines.launch


/**
 * Activity for the receiver client side
 */
class ReceiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiverBinding
    private val viewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this)[ReceiverViewModel::class.java]
    }

    private val selectDocumentTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.baseDocumentTreeUri = uri
        if (uri != null) viewModel.sendMessage(TransferProtocol(ReceiveReady))
    }

    private val cameraContract = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) {
            startQRScanner()
        } else {
            Toast.makeText(
                this,
                "Kamera izni verilmedi, QR kod okuma işlemi yapılamaz.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val qrScanContract = registerForActivityResult(ScanContract()) {
        it.contents?.let { qrResult ->
            binding.etOfferSdp.setText(qrResult)
        }
    }

    /**
     * Start point of the activity
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
        setObservers()
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startQRScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan Offer Sdp QR")
        options.setCameraId(0)
        qrScanContract.launch(options)
    }

    private fun init() {
        binding.btnGenerateAnswerSdp.setOnClickListener {
            val offerSdpStr = binding.etOfferSdp.text.toString()
            if (offerSdpStr.isBlank()) {
                Toast.makeText(this, "Öncelikle offer sdp json değerini girmelisiniz!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.parseOfferSdp(offerSdpStr)
        }
        binding.btnScanOfferSdpQr.setOnClickListener {
            if (isCameraPermissionGranted()) {
                startQRScanner()
            } else {
                cameraContract.launch(Manifest.permission.CAMERA)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.documentTreeLauncher.collect {
                    selectDocumentTree.launch(null)
                }
                viewModel.logs.collect {
                    val text = binding.etLogs.text?.toString() ?: ""
                    binding.etLogs.text = "$text-$it\n"
                }
            }
        }
        viewModel.qrStr.observe(this) {
            showAnswerSdp(it)
        }
        viewModel.progress.observe(this) {
        }
    }

    private fun showAnswerSdp(answerSdp: String) {
        binding.tvAnswerSdpJson.text = answerSdp

        try {
            val bitmap = generateQRCode(answerSdp)
            binding.ivAnswerSdpQr.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
    }
}
