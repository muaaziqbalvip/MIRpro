package com.mi.routermanagerpro

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.mi.routermanagerpro.databinding.ActivityMainBinding
import com.mi.routermanagerpro.ui.devices.DevicesFragment
import com.mi.routermanagerpro.ui.home.HomeFragment
import com.mi.routermanagerpro.ui.settings.SettingsFragment
import com.mi.routermanagerpro.ui.speedtest.SpeedTestFragment
import com.mi.routermanagerpro.ui.wificonfig.WifiConfigFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home -> HomeFragment()
                R.id.nav_wifi -> WifiConfigFragment()
                R.id.nav_devices -> DevicesFragment()
                R.id.nav_speed -> SpeedTestFragment()
                R.id.nav_settings -> SettingsFragment()
                else -> HomeFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun switchToTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }
}
