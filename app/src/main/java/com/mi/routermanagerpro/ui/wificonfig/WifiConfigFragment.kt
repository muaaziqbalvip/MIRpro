package com.mi.routermanagerpro.ui.wificonfig

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mi.routermanagerpro.R
import com.mi.routermanagerpro.databinding.FragmentWifiConfigBinding
import com.mi.routermanagerpro.databinding.ItemSavedRouterBinding
import com.mi.routermanagerpro.network.HuaweiLoginResult
import com.mi.routermanagerpro.network.HuaweiRouterClient
import com.mi.routermanagerpro.network.HuaweiWlanClient
import com.mi.routermanagerpro.network.WlanUpdateResult
import com.mi.routermanagerpro.util.NetworkUtils
import com.mi.routermanagerpro.util.RouterPrefs
import com.mi.routermanagerpro.util.RouterSession
import kotlinx.coroutines.launch

class WifiConfigFragment : Fragment() {

    private var _binding: FragmentWifiConfigBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWifiConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAutoDetect.setOnClickListener {
            val snapshot = NetworkUtils.getWifiSnapshot(requireContext())
            if (snapshot.gatewayIp != "—") {
                binding.etRouterIp.setText(snapshot.gatewayIp)
                Toast.makeText(requireContext(), "Detected: ${snapshot.gatewayIp}", Toast.LENGTH_SHORT).show()
            } else {
                binding.etRouterIp.setText(NetworkUtils.commonDefaultGateways().first())
                Toast.makeText(
                    requireContext(),
                    "Could not auto-detect. Try a common default IP.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        binding.btnConnect.setOnClickListener { attemptLogin() }
        binding.btnSaveWlan.setOnClickListener { saveWlanSettings() }

        renderSavedRouters()

        // If a session is already active (e.g. returning to this tab), show WLAN card
        if (RouterSession.isLoggedIn()) {
            binding.wlanSettingsCard.visibility = View.VISIBLE
            loadWlanInfo()
        }
    }

    private fun attemptLogin() {
        val ip = binding.etRouterIp.text?.toString()?.trim().orEmpty()
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        if (ip.isEmpty()) {
            Toast.makeText(requireContext(), "Enter a router IP first", Toast.LENGTH_SHORT).show()
            return
        }
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Enter username and password", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        val client = HuaweiRouterClient(ip)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = client.login(username, password)
            if (_binding == null) return@launch
            setLoading(false)

            when (result) {
                is HuaweiLoginResult.Success -> {
                    RouterSession.setActiveSession(ip, client)
                    RouterPrefs.saveRouter(requireContext(), ip)
                    showStatus(getString(R.string.login_success), true)
                    renderSavedRouters()
                    binding.wlanSettingsCard.visibility = View.VISIBLE
                    loadWlanInfo()
                }
                is HuaweiLoginResult.Failed -> {
                    showStatus(result.reason, false)
                }
                is HuaweiLoginResult.Error -> {
                    showStatus(result.message, false)
                }
            }
        }
    }

    private fun loadWlanInfo() {
        val ip = RouterSession.getActiveIp() ?: return
        val client = RouterSession.getActiveClient() ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val info = HuaweiWlanClient.fetchBasicInfo(ip, client.getSessionCookie())
            if (_binding == null) return@launch
            if (info != null) {
                binding.tvCurrentSsid.text = "Current network: ${info.ssid}"
                binding.etNewSsid.hint = "New WiFi Name (currently: ${info.ssid})"
            } else {
                binding.tvCurrentSsid.text = "Could not read current WiFi settings"
            }
        }
    }

    private fun saveWlanSettings() {
        val ip = RouterSession.getActiveIp()
        val client = RouterSession.getActiveClient()

        if (ip == null || client == null || !client.isLoggedIn()) {
            Toast.makeText(requireContext(), "Login to your router first", Toast.LENGTH_SHORT).show()
            return
        }

        val newSsid = binding.etNewSsid.text?.toString()?.trim()
        val newPassword = binding.etNewWifiPassword.text?.toString()?.trim()

        if (newSsid.isNullOrBlank() && newPassword.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Enter a new SSID or password to change", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressWlanSave.visibility = View.VISIBLE
        binding.btnSaveWlan.isEnabled = false
        binding.tvWlanSaveStatus.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = HuaweiWlanClient.updateWifi(ip, client.getSessionCookie(), newSsid, newPassword)
            if (_binding == null) return@launch

            binding.progressWlanSave.visibility = View.GONE
            binding.btnSaveWlan.isEnabled = true
            binding.tvWlanSaveStatus.visibility = View.VISIBLE

            when (result) {
                is WlanUpdateResult.Success -> {
                    binding.tvWlanSaveStatus.text = "WiFi settings updated. Your devices may briefly disconnect."
                    binding.tvWlanSaveStatus.setTextColor(resources.getColor(R.color.success_green, null))
                    loadWlanInfo()
                }
                is WlanUpdateResult.Failed -> {
                    binding.tvWlanSaveStatus.text = result.reason
                    binding.tvWlanSaveStatus.setTextColor(resources.getColor(R.color.error_red, null))
                }
                is WlanUpdateResult.Error -> {
                    binding.tvWlanSaveStatus.text = result.message
                    binding.tvWlanSaveStatus.setTextColor(resources.getColor(R.color.error_red, null))
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        if (_binding == null) return
        binding.progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnConnect.isEnabled = !loading
        if (loading) {
            binding.tvLoginStatus.visibility = View.VISIBLE
            binding.tvLoginStatus.text = getString(R.string.login_in_progress)
            binding.tvLoginStatus.setTextColor(resources.getColor(R.color.text_secondary, null))
        }
    }

    private fun showStatus(message: String, success: Boolean) {
        if (_binding == null) return
        binding.tvLoginStatus.visibility = View.VISIBLE
        binding.tvLoginStatus.text = message
        binding.tvLoginStatus.setTextColor(
            resources.getColor(if (success) R.color.success_green else R.color.error_red, null)
        )
    }

    private fun renderSavedRouters() {
        if (_binding == null) return
        binding.savedRoutersContainer.removeAllViews()
        val saved = RouterPrefs.getSavedRouters(requireContext())
        for (ip in saved) {
            val itemBinding = ItemSavedRouterBinding.inflate(
                layoutInflater, binding.savedRoutersContainer, false
            )
            itemBinding.tvSavedIp.text = ip
            itemBinding.root.setOnClickListener {
                binding.etRouterIp.setText(ip)
            }
            itemBinding.btnDeleteSaved.setOnClickListener {
                RouterPrefs.removeRouter(requireContext(), ip)
                renderSavedRouters()
            }
            binding.savedRoutersContainer.addView(itemBinding.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
