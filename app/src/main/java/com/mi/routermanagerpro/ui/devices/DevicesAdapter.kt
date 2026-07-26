package com.mi.routermanagerpro.ui.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mi.routermanagerpro.R
import com.mi.routermanagerpro.databinding.ItemDeviceBinding
import com.mi.routermanagerpro.network.HuaweiDevice

class DevicesAdapter(
    private val onToggleBlock: (HuaweiDevice, currentlyBlocked: Boolean) -> Unit
) : RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {

    private val items = mutableListOf<HuaweiDevice>()
    private val blockedMacs = mutableSetOf<String>()

    fun submitList(newItems: List<HuaweiDevice>, blocked: Set<String>) {
        items.clear()
        items.addAll(newItems)
        blockedMacs.clear()
        blockedMacs.addAll(blocked.map { it.lowercase() })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding, onToggleBlock)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = items[position]
        val isBlocked = blockedMacs.contains(device.mac.lowercase())
        holder.bind(device, isBlocked)
    }

    override fun getItemCount(): Int = items.size

    class DeviceViewHolder(
        private val binding: ItemDeviceBinding,
        private val onToggleBlock: (HuaweiDevice, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: HuaweiDevice, isBlocked: Boolean) {
            binding.tvDeviceName.text = device.name
            binding.tvDeviceIp.text = if (device.mac.isNotBlank())
                "${device.ip} • ${device.mac}" else device.ip

            val statusText = if (device.isOnline) "Online" else "Offline"
            binding.tvDeviceMeta.text = if (device.isOnline)
                "$statusText • ${device.connectionDuration}" else statusText

            binding.statusDot.setBackgroundResource(
                if (isBlocked) R.drawable.dot_offline
                else if (device.isOnline) R.drawable.dot_online else R.drawable.dot_offline
            )

            if (isBlocked) {
                binding.btnBlockToggle.text = "Unblock"
                binding.btnBlockToggle.setTextColor(
                    binding.root.context.getColor(R.color.success_green)
                )
                binding.btnBlockToggle.strokeColor =
                    android.content.res.ColorStateList.valueOf(
                        binding.root.context.getColor(R.color.success_green)
                    )
            } else {
                binding.btnBlockToggle.text = "Block"
                binding.btnBlockToggle.setTextColor(
                    binding.root.context.getColor(R.color.error_red)
                )
                binding.btnBlockToggle.strokeColor =
                    android.content.res.ColorStateList.valueOf(
                        binding.root.context.getColor(R.color.error_red)
                    )
            }

            binding.btnBlockToggle.setOnClickListener { onToggleBlock(device, isBlocked) }
        }
    }
}
