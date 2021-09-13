package com.jb.musicplayer

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        var btn_save: Button
        var btn_cancel: Button
        var mMainActivity: MainActivity

        val et_MaxListSize = findViewById<EditText>(R.id.etMaxListSize)

    }
}