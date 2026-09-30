package com.nikeboss.keyboard

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Switch

class KeyboardSettings : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)

        val prefs = getSharedPreferences("nikeboss_keyboard", Context.MODE_PRIVATE)

        val vibSwitch = findViewById<Switch>(R.id.switch_vibration)
        val soundSwitch = findViewById<Switch>(R.id.switch_sound)
        val heightSeek = findViewById<SeekBar>(R.id.seek_height)

        vibSwitch.isChecked = prefs.getBoolean("vibration", true)
        soundSwitch.isChecked = prefs.getBoolean("sound", true)
        heightSeek.progress = prefs.getInt("height", 60)

        vibSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("vibration", isChecked).apply()
        }
        soundSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sound", isChecked).apply()
        }
        heightSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.edit().putInt("height", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
}
