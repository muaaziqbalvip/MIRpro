package com.mi.routermanagerpro.ui.wificonfig

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mi.routermanagerpro.databinding.FragmentWifiConfigBinding
import com.mi.routermanagerpro.databinding.ItemSavedRouterBinding
import com.mi.routermanagerpro.util.NetworkUtils
import com.mi.routermanagerpro.util.RouterPrefs

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

        binding.btnConnect.setOnClickListener {
            val ip = binding.etRouterIp.text?.toString()?.trim().orEmpty()
            if (ip.isEmpty()) {
                Toast.makeText(requireContext(), "Enter a router IP first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            RouterPrefs.saveRouter(requireContext(), ip)
            startActivity(RouterWebActivity.newIntent(requireContext(), ip))
            renderSavedRouters()
        }

        renderSavedRouters()
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
                startActivity(RouterWebActivity.newIntent(requireContext(), ip))
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
