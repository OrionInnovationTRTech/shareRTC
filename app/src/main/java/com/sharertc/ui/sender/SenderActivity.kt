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
import com.sharertc.databinding.ActivitySenderBinding
import com.sharertc.model.FileDescription
import com.sharertc.model.SendReady
import com.sharertc.model.TransferProtocol
import com.sharertc.util.generateQRCode
import kotlinx.coroutines.launch
import org.webrtc.DataChannel


/**
 * Activity for the sender client side
 */
class SenderActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySenderBinding
    private val viewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this)[SenderViewModel::class.java]
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
        if (it.contents != null) {
            val qrResult = it.contents
            binding.etAnswerSdp.setText(qrResult)
        }
    }

    /**
     * Photo picker result
     */
    private val pickMultipleMedia =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            viewModel.uris = uris
            uris.forEach { uri ->
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    cursor.moveToFirst()
                    val name = cursor.getString(nameIndex)
                    val size = cursor.getLong(sizeIndex)
                    viewModel.files.add(FileDescription(name, size))
                }
            }
            if (uris.isEmpty()) {
                viewModel.files.clear()
                viewModel.uris = listOf()
                Toast.makeText(this, "No media selected", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.sendMessage(TransferProtocol(SendReady))
            }
        }

    /**
     * Start point of the activity
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySenderBinding.inflate(layoutInflater)
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

    private fun init() {
        binding.btnStartConnection.setOnClickListener {
            val answerSdpStr = binding.etAnswerSdp.text.toString()
            if (answerSdpStr.isBlank()) {
                Toast.makeText(this, "Öncelikle answer sdp json değerini girmelisiniz!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.parseAnswerSdp(answerSdpStr)
        }
        binding.btnScanAnswerSdpQr.setOnClickListener {
            if (isCameraPermissionGranted()) {
                startQRScanner()
            } else {
                cameraContract.launch(Manifest.permission.CAMERA)
            }
        }
        binding.btnSendData.setOnClickListener {
            pickMultipleMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logs.collect {
                    val text = binding.etLogs.text?.toString() ?: ""
                    binding.etLogs.text = "$text-$it\n"
                }
            }
        }
        viewModel.qrStr.observe(this) { qRCodeData ->
            showOfferSdp(qRCodeData)
        }
        viewModel.dcState.observe(this) {
            binding.btnSendData.isVisible = it == DataChannel.State.OPEN
        }
        viewModel.progress.observe(this) {
        }
    }

    private fun showOfferSdp(offerSdp: String) {
        binding.tvOfferSdpJson.text = offerSdp

        try {
            val bitmap = generateQRCode(offerSdp)
            binding.ivOfferSdpQr.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
    }

    private fun startQRScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Scan Answer Sdp QR")
        options.setCameraId(0)
        qrScanContract.launch(options)
    }

}