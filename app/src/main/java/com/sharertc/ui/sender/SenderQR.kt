package com.sharertc.ui.sender

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Binder
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
import com.sharertc.databinding.FragmentSenderQRBinding
import com.sharertc.util.generateQRCode
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection


class SenderQR : Fragment() {

private lateinit var binding:FragmentSenderQRBinding

    private val viewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(requireActivity())[SenderViewModel::class.java]
    }

    private var qrResult: String = ""

    fun handleQrResult(newQrResult: String) {
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

        if (it.contents != null) {
            Log.d("scanintent",it.contents)
           handleQrResult(it.contents).toString()
            visible()
        //binding.etAnswerSdp.setText(qrResult) // qr kodundan okunan değeri bu metin kutusunun içine yazdırılır
        }

    }

  fun visible(){
      binding.btnScanAnswerSdpQr.isVisible=false
      binding.btnStartConnection.isVisible=true
  }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle? ): View? {

        binding= FragmentSenderQRBinding.inflate(inflater, container,false)
        binding.btnStartConnection.isVisible=false
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
    private fun init() {
        binding.btnStartConnection.setOnClickListener {
            Log.d("scanintent2",qrResult)
            if (qrResult.isBlank()) {
                return@setOnClickListener
            }
            else {
            viewModel.parseAnswerSdp(qrResult)
            Navigation.findNavController(it).navigate(R.id.action_senderQR_to_senderSendData)
                Log.d("scanintent3",qrResult)
            }
        }


        binding.btnScanAnswerSdpQr.setOnClickListener {
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
                viewModel.logs.collect {  // logs Flow'unu collect ile gözlemleme
                    val text = binding.etLogs.text?.toString() ?: ""
                    binding.etLogs.text = "-$it\n$text"
                }
            }
        }

        viewModel.qrStr.observe(viewLifecycleOwner) { qRCodeData ->
            showOfferSdp(qRCodeData)
            Log.d("deneme",qRCodeData)
        }


        viewModel.pcState.observe(viewLifecycleOwner) {
            if (it == PeerConnection.IceConnectionState.DISCONNECTED) {
              //  binding.btnSendData.isVisible = false
            }

        }
    }


    private fun showOfferSdp(offerSdp: String) {
       // binding.tvOfferSdpJson.text = offerSdp  // offer sdp qrı yazılıyor

        try {
            val bitmap = generateQRCode(offerSdp)
            binding.ivOfferSdpQr.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
    }


    private fun startQRScanner() { //kamerayı başlatır
        val options = ScanOptions() // ayarları nasıl başlatılması gerektiğiğ
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE) // qr tür tiğindeki kodları algıla
        options.setPrompt(getString(R.string.scan_answer_qr_prompt))
        options.setCameraId(0)
        qrScanContract.launch(options) // option kameranın ayarlarını içerir
    }


}