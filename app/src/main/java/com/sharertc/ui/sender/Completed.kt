package com.sharertc.ui.sender

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sharertc.R
import com.sharertc.databinding.FragmentCompletedBinding
import kotlinx.coroutines.launch


class Completed : Fragment() {
    private lateinit var binding: FragmentCompletedBinding
    private val viewModel by lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(requireActivity())[SenderViewModel::class.java]
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle? ): View? {
        binding = FragmentCompletedBinding.inflate(inflater, container, false)

        setObservers()
        return binding.root

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
    }



}