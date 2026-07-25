package com.mi.routermanagerpro.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mi.routermanagerpro.BuildConfig
import com.mi.routermanagerpro.databinding.FragmentSettingsBinding
import com.mi.routermanagerpro.util.RouterPrefs

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvVersion.text = BuildConfig.VERSION_NAME

        binding.rowClearData.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Saved Data")
                .setMessage("This will remove all saved router IPs. Continue?")
                .setPositiveButton("Clear") { _, _ ->
                    RouterPrefs.clearAll(requireContext())
                    Toast.makeText(requireContext(), "Saved data cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
