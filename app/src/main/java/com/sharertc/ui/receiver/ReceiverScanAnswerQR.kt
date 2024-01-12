package com.sharertc.ui.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.Navigation
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sharertc.R
import com.sharertc.databinding.FragmentReceiverScanAnswerQRBinding
import com.sharertc.databinding.FragmentSenderQRBinding
import com.sharertc.util.generateQRCode
import kotlinx.coroutines.launch


class ReceiverScanAnswerQR : Fragment() {
    private lateinit var binding:FragmentReceiverScanAnswerQRBinding
    private val viewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(requireActivity())[ReceiverViewModel::class.java]
    }

    private var qrResult: String = ""
    private fun handleQrResult(newQrResult: String) {
        qrResult = newQrResult
    }


    private val cameraContract = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) {
            startQRScanner()
        } else {
            Toast.makeText(
                requireContext(),
                R.string.camera_permission_warning,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val qrScanContract = registerForActivityResult(ScanContract()) {
        it.contents?.let { qrResult ->
            handleQrResult(qrResult)
            viewModel.parseOfferSdp(qrResult)
            Log.d("deneme1",qrResult)
            visible()
        }
    }


fun visible(){
    binding.btnScanOfferSdpQr.isVisible=false
    binding.btnGenerateAnswerSdp.isVisible=true

}

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?  ): View? {

        binding= FragmentReceiverScanAnswerQRBinding.inflate(inflater, container,false)
        binding.btnGenerateAnswerSdp.isVisible=false
        init()
        setObservers()
        return binding.root
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startQRScanner() {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt(getString(R.string.scan_offer_qr_prompt))
        options.setCameraId(0)
        qrScanContract.launch(options)
    }

    private fun init() {
        binding.btnGenerateAnswerSdp.setOnClickListener {
            val offerSdpStr = qrResult
            Log.d("deneme2",offerSdpStr)

            if (offerSdpStr.isBlank()) {
                return@setOnClickListener
            }
            Navigation.findNavController(it).navigate(R.id.action_receiverScanAnswerQR_to_receiverGenerateQR)
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
        Log.d("Test", viewModel.toString())
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logs.collect {
                    val text = binding.etLogs.text?.toString() ?: ""
                    binding.etLogs.text = "-$it\n$text"
                }
            }
        }
    }


}