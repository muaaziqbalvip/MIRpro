package com.mi.routermanagerpro.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mi.routermanagerpro.MainActivity
import com.mi.routermanagerpro.R
import com.mi.routermanagerpro.databinding.FragmentHomeBinding
import com.mi.routermanagerpro.ui.wificonfig.RouterWebActivity
import com.mi.routermanagerpro.util.NetworkUtils
import android.content.Intent

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        refreshStatus()

        binding.btnOpenPanel.setOnClickListener {
            val snapshot = NetworkUtils.getWifiSnapshot(requireContext())
            val ip = if (snapshot.gatewayIp != "—") snapshot.gatewayIp else "192.168.100.1"
            startActivity(RouterWebActivity.newIntent(requireContext(), ip))
        }

        binding.btnScanDevices.setOnClickListener {
            (activity as? MainActivity)?.switchToTab(R.id.nav_devices)
        }

        binding.btnSpeedTest.setOnClickListener {
            (activity as? MainActivity)?.switchToTab(R.id.nav_speed)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        if (_binding == null) return
        val snapshot = NetworkUtils.getWifiSnapshot(requireContext())

        binding.tvConnectionStatus.text = if (snapshot.isConnected)
            getString(R.string.home_status_connected) else getString(R.string.home_status_disconnected)
        binding.tvConnectionStatus.setTextColor(
            resources.getColor(
                if (snapshot.isConnected) R.color.success_green else R.color.error_red,
                null
            )
        )
        binding.tvSsid.text = snapshot.ssid
        binding.tvGatewayIp.text = snapshot.gatewayIp
        binding.tvSignal.text = "${snapshot.signalLevelPercent}%"
        binding.tvDeviceIp.text = snapshot.deviceIp
        binding.tvLinkSpeed.text = "${snapshot.linkSpeedMbps} Mbps"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
