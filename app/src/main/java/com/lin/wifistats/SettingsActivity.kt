package com.lin.wifistats

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.lin.wifistats.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbarSettings.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        val prefs = Prefs(this)
        binding.etDingtalkWebhook.setText(prefs.dingTalkWebhookUrl ?: "")
        binding.tvVersion.text = getString(R.string.app_version_copyright)
        try {
            binding.btnRequestBattery.setOnClickListener {
                BatteryHelper.requestIgnoreBatteryOptimizations(this)
            }
            binding.btnOpenAppSettings.setOnClickListener {
                BatteryHelper.openAppDetailSettings(this)
            }
        } catch (_: Exception) { /* 部分机型可能无该视图 */ }
    }

    override fun onPause() {
        super.onPause()
        val url = binding.etDingtalkWebhook.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        Prefs(this).dingTalkWebhookUrl = url
    }
}
