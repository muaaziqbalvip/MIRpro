package com.mi.routermanagerpro.ui.speedtest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mi.routermanagerpro.R
import com.mi.routermanagerpro.databinding.FragmentSpeedTestBinding
import com.mi.routermanagerpro.network.SpeedTestEngine
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

class SpeedTestFragment : Fragment() {

    private var _binding: FragmentSpeedTestBinding? = null
    private val binding get() = _binding!!
    private var isTesting = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeedTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnStartTest.setOnClickListener { runFullTest() }
    }

    private fun runFullTest() {
        if (isTesting || _binding == null) return
        isTesting = true
        binding.btnStartTest.isEnabled = false
        binding.tvDownloadResult.text = "--"
        binding.tvUploadResult.text = "--"
        binding.tvPingResult.text = "--"
        binding.progressRing.progress = 0

        viewLifecycleOwner.lifecycleScope.launch {
            // Ping
            binding.tvSpeedPhase.text = getString(R.string.testing_ping)
            val ping = SpeedTestEngine.measurePing()
            if (_binding == null) return@launch
            binding.tvPingResult.text = if (ping >= 0) "$ping" else "--"

            // Download
            binding.tvSpeedPhase.text = getString(R.string.testing_download)
            val download = SpeedTestEngine.measureDownload { mbps ->
                updateLiveSpeed(mbps)
            }
            if (_binding == null) return@launch
            binding.tvDownloadResult.text = formatSpeed(download)

            // Upload
            binding.tvSpeedPhase.text = getString(R.string.testing_upload)
            binding.progressRing.progress = 0
            val upload = SpeedTestEngine.measureUpload { mbps ->
                updateLiveSpeed(mbps)
            }
            if (_binding == null) return@launch
            binding.tvUploadResult.text = formatSpeed(upload)

            binding.tvSpeedPhase.text = getString(R.string.test_complete)
            binding.progressRing.progress = 100
            binding.btnStartTest.isEnabled = true
            isTesting = false
        }
    }

    private fun updateLiveSpeed(mbps: Double) {
        if (_binding == null) return
        binding.tvSpeedValue.text = formatSpeed(mbps)
        // Scale progress ring visually: cap display scale at 500 Mbps for full ring
        val progress = min(100, ((mbps / 500.0) * 100).roundToInt())
        binding.progressRing.progress = progress
    }

    private fun formatSpeed(mbps: Double): String {
        return SpeedTestEngine.round1(mbps).toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
