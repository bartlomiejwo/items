package com.bwojtowicz.clothescontrol

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var editTextHost: EditText
    private lateinit var editTextAuthToken: EditText
    private lateinit var buttonSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        editTextHost = findViewById(R.id.edit_text_host)
        editTextAuthToken = findViewById(R.id.edit_text_auth_token)
        buttonSave = findViewById(R.id.button_save)

        loadSavedValues()

        buttonSave.setOnClickListener {
            saveValues()
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedValues() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val host = sharedPreferences.getString("host", "")
        val authToken = sharedPreferences.getString("authToken", "")

        editTextHost.setText(host)
        editTextAuthToken.setText(authToken)
    }

    private fun saveValues() {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("host", editTextHost.text.toString())
        editor.putString("authToken", editTextAuthToken.text.toString())
        editor.apply()
    }
}