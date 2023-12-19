package com.sharertc.ui.receiver

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.WriterException
import com.sharertc.R
import com.sharertc.databinding.FragmentReceiverGenerateQRBinding
import com.sharertc.model.ReceiveReady
import com.sharertc.model.TransferProtocol
import com.sharertc.util.generateQRCode
import kotlinx.coroutines.launch


class ReceiverGenerateQR : Fragment() {

   private lateinit var binding:FragmentReceiverGenerateQRBinding
    private val viewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(requireActivity())[ReceiverViewModel::class.java]
    }

    private val selectDocumentTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.baseDocumentTreeUri = uri
        if (uri != null) viewModel.sendMessage(TransferProtocol(ReceiveReady))
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle? ): View? {
        binding= FragmentReceiverGenerateQRBinding.inflate(inflater,container,false)
        setObservers()
        return binding.root
    }

    @SuppressLint("SetTextI18n")

    private fun setObservers() {

        Log.d("Test", viewModel.toString())
        viewModel.qrStr.observe(viewLifecycleOwner) { //qrı yazdırıyor
            showAnswerSdp(it)
        }


        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.documentTreeLauncher.collect {
                    selectDocumentTree.launch(null)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logs.collect {
                    val text = binding.etLogs.text?.toString() ?: ""
                    binding.etLogs.text = "-$it\n$text"
                }
            }
        }


    }

    private fun showAnswerSdp(answerSdp: String) {
        try {
            val bitmap = generateQRCode(answerSdp)
            binding.ivAnswerSdpQr.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
    }




}