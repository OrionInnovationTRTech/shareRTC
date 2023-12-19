package com.sharertc.ui.sender

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.sharertc.R
import com.sharertc.databinding.FragmentNotCompletedBinding


class NotCompleted : Fragment() {
    private lateinit var binding: FragmentNotCompletedBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle? ): View? {

        binding= FragmentNotCompletedBinding.inflate(inflater, container, false)
        return binding.root

    }


}