package com.mi.routermanagerpro.ui.devices

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mi.routermanagerpro.databinding.ItemDeviceBinding
import com.mi.routermanagerpro.network.ScannedDevice

class DevicesAdapter : RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {

    private val items = mutableListOf<ScannedDevice>()

    fun submitList(newItems: List<ScannedDevice>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class DeviceViewHolder(private val binding: ItemDeviceBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(device: ScannedDevice) {
            binding.tvDeviceName.text = device.hostname
            binding.tvDeviceIp.text = device.ip
        }
    }
}
