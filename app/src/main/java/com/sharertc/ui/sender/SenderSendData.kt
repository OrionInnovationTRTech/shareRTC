package com.sharertc.ui.sender

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.sharertc.R
import com.sharertc.databinding.FragmentSenderSendDataBinding
import com.sharertc.model.FileDescription
import com.sharertc.model.SendReady
import com.sharertc.model.TransferProtocol
import kotlinx.coroutines.delay
import org.webrtc.DataChannel


class SenderSendData : Fragment() {

    private lateinit var binding:FragmentSenderSendDataBinding

    private val viewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(requireActivity())[SenderViewModel::class.java]
    }

    fun funSentInfo(){
        if( viewModel.sendInfo == true){
            Navigation.findNavController(requireView()).navigate(R.id.action_senderSendData_to_completed)
        }
      else{Navigation.findNavController(requireView()).navigate(R.id.action_senderSendData_to_notCompleted)}
    }


    private val pickMultipleMedia =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
            viewModel.uris = uris
            viewModel.files.clear()
            uris.forEach { uri ->
                requireActivity().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    cursor.moveToFirst()
                    val name = cursor.getString(nameIndex)
                    val size = cursor.getLong(sizeIndex)
                    viewModel.files.add(FileDescription(name, size))
                }
            }

            if (uris.isNotEmpty()) {
                viewModel.sendMessage(TransferProtocol(SendReady))
                val a = viewModel.sendInfo
                Log.d("deneme",a.toString()) // True bilgisini alırsa Logdan dosya aktarımı olmuş demektir
                funSentInfo()
            }



        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle? ): View? {

        binding=FragmentSenderSendDataBinding.inflate(inflater,container,false)
        init()
        setObservers()
        return binding.root
    }

    private fun init() {
        binding.btnSendData.setOnClickListener {
            pickMultipleMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setObservers() {
        viewModel.dcState.observe(viewLifecycleOwner) {
            it == DataChannel.State.OPEN
        }

    }

}