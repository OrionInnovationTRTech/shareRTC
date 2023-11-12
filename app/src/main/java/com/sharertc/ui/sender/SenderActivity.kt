package com.sharertc.ui.sender

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.zxing.WriterException
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import com.sharertc.App
import com.sharertc.databinding.ActivitySenderBinding
import com.sharertc.model.FileDescription
import com.sharertc.model.SendReady
import com.sharertc.model.TransferProtocol
import com.sharertc.util.generateQRCode


/**
 * Activity for the sender client side
 */
class SenderActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySenderBinding
    private val viewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this)[SenderViewModel::class.java]
    }

    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private val app get() = application as App

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

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
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
            if (allPermissionsGranted()) {
                startQRScanner()
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            }
        }
        binding.btnSendData.setOnClickListener {
            pickMultipleMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    private fun setObservers() {
        viewModel.qrStr.observe(this) { qRCodeData ->
            binding.tvOfferSdpJson.text = qRCodeData

            try {
                val bitmap = generateQRCode(qRCodeData)
                binding.ivOfferSdpQr.setImageBitmap(bitmap)
            } catch (e: WriterException) {
                e.printStackTrace()
            }
        }
    }

    private fun startQRScanner() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Scan Answer Sdp QR")
        integrator.setCameraId(0)
        integrator.initiateScan()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startQRScanner()
            } else {
                Toast.makeText(
                    this,
                    "Kamera izni verilmedi, QR kod okuma işlemi yapılamaz.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result: IntentResult? =
            IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this, "QR kod bulunamadı", Toast.LENGTH_SHORT).show()
            } else {
                //Toast.makeText(this, "QR Kod: ${result.contents}", Toast.LENGTH_SHORT).show()
                val qrResult = result.contents
                binding.etAnswerSdp.setText(qrResult)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

}