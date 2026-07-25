package com.mi.routermanagerpro.ui.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mi.routermanagerpro.databinding.FragmentDevicesBinding
import com.mi.routermanagerpro.network.DeviceScanner
import com.mi.routermanagerpro.util.NetworkUtils
import kotlinx.coroutines.launch

class DevicesFragment : Fragment() {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!
    private val adapter = DevicesAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter

        binding.btnRefreshDevices.setOnClickListener { startScan() }

        startScan()
    }

    private fun startScan() {
        val snapshot = NetworkUtils.getWifiSnapshot(requireContext())
        val subnet = NetworkUtils.localSubnetPrefix(snapshot.deviceIp)

        if (subnet == null) {
            binding.tvEmptyState.text = "Connect to WiFi first to scan the network."
            binding.emptyState.visibility = View.VISIBLE
            binding.rvDevices.visibility = View.GONE
            return
        }

        binding.progressScanning.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.rvDevices.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val devices = DeviceScanner.scanSubnet(subnet)
            if (_binding == null) return@launch

            binding.progressScanning.visibility = View.GONE

            if (devices.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.rvDevices.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.rvDevices.visibility = View.VISIBLE
                adapter.submitList(devices)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
