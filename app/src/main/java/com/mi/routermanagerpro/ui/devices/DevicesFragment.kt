package com.mi.routermanagerpro.ui.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mi.routermanagerpro.MainActivity
import com.mi.routermanagerpro.R
import com.mi.routermanagerpro.databinding.FragmentDevicesBinding
import com.mi.routermanagerpro.network.HuaweiDeviceListFetcher
import com.mi.routermanagerpro.network.HuaweiMacFilterClient
import com.mi.routermanagerpro.network.MacFilterActionResult
import com.mi.routermanagerpro.util.RouterSession
import kotlinx.coroutines.launch

class DevicesFragment : Fragment() {

    private var _binding: FragmentDevicesBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: DevicesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDevicesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DevicesAdapter { device, currentlyBlocked ->
            toggleBlock(device.mac, currentlyBlocked)
        }
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter

        binding.btnRefreshDevices.setOnClickListener { loadDevices() }

        loadDevices()
    }

    override fun onResume() {
        super.onResume()
        loadDevices()
    }

    private fun loadDevices() {
        if (_binding == null) return

        val ip = RouterSession.getActiveIp()
        val client = RouterSession.getActiveClient()

        if (ip == null || client == null || !client.isLoggedIn()) {
            binding.tvEmptyState.text = "Login to your router in WiFi Config first to see devices."
            binding.emptyState.visibility = View.VISIBLE
            binding.rvDevices.visibility = View.GONE
            binding.progressScanning.visibility = View.GONE
            binding.emptyState.setOnClickListener {
                (activity as? MainActivity)?.switchToTab(R.id.nav_wifi)
            }
            return
        }

        binding.progressScanning.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.rvDevices.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val devices = HuaweiDeviceListFetcher.fetch(ip, client.getSessionCookie())
            val filterStatus = HuaweiMacFilterClient.fetchStatus(ip, client.getSessionCookie())
            if (_binding == null) return@launch

            binding.progressScanning.visibility = View.GONE

            val blockedMacs = filterStatus?.entries?.map { it.mac }?.toSet() ?: emptySet()

            if (devices.isEmpty()) {
                binding.tvEmptyState.text = getString(R.string.devices_none)
                binding.emptyState.visibility = View.VISIBLE
                binding.rvDevices.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.rvDevices.visibility = View.VISIBLE
                adapter.submitList(devices.sortedByDescending { it.isOnline }, blockedMacs)
            }
        }
    }

    private fun toggleBlock(mac: String, currentlyBlocked: Boolean) {
        val ip = RouterSession.getActiveIp()
        val client = RouterSession.getActiveClient()
        if (ip == null || client == null || mac.isBlank()) return

        Toast.makeText(
            requireContext(),
            if (currentlyBlocked) "Unblocking…" else "Blocking…",
            Toast.LENGTH_SHORT
        ).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (currentlyBlocked) {
                HuaweiMacFilterClient.unblockDevice(ip, client.getSessionCookie(), mac)
            } else {
                HuaweiMacFilterClient.blockDevice(ip, client.getSessionCookie(), mac)
            }
            if (_binding == null) return@launch

            when (result) {
                is MacFilterActionResult.Success -> {
                    Toast.makeText(
                        requireContext(),
                        if (currentlyBlocked) "Device unblocked" else "Device blocked",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadDevices()
                }
                is MacFilterActionResult.Failed -> {
                    Toast.makeText(requireContext(), result.reason, Toast.LENGTH_LONG).show()
                }
                is MacFilterActionResult.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
