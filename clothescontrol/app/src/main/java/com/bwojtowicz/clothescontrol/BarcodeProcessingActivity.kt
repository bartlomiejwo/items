package com.bwojtowicz.clothescontrol

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BarcodeProcessingActivity : AppCompatActivity() {
    companion object {
        const val SCAN_DATA_FILENAME = "scan_data.json"
        const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
    }
    data class ScanData(
        val code: String,
        val actionName: String,
        val timestamp: String,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_processing)

        val barcodeValue = intent.getStringExtra("barcodeValue")
        val barcodeValueText = findViewById<TextView>(R.id.barcode_value_text)
        barcodeValueText.text = barcodeValue

        val laundryButton = findViewById<Button>(R.id.button_laundry)
        val complaintButton = findViewById<Button>(R.id.button_complaint)
        val currentTimeString = getCurrentTimeString()

        laundryButton.setOnClickListener {
            handleButtonClick(barcodeValue, "PRANIE", currentTimeString)
        }

        complaintButton.setOnClickListener {
            handleButtonClick(barcodeValue, "REKLAMACJA", currentTimeString)
        }
    }

    private fun handleButtonClick(barcodeValue: String?, actionName: String, timestamp: String) {
        if (barcodeValue != null) {
            val scanData = ScanData(
                code = barcodeValue,
                actionName = actionName,
                timestamp = timestamp
            )
            postScanData(scanData)
            setResult(Activity.RESULT_OK)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    private fun getCurrentTimeString(): String {
        val currentTime = Calendar.getInstance()
        val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        return dateFormat.format(currentTime.time)
    }

    private fun postScanData(scanData: ScanData, ignoreTimestamp: Boolean = true) {
        val sharedPreferences = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val host = sharedPreferences.getString("host", "") ?: ""
        val authToken = sharedPreferences.getString("authToken", "") ?: ""
        val url = "$host/api/scans/item_scan/create/"
        val client = OkHttpClient()

        val actionData = JSONObject().apply {
            put("name", scanData.actionName)
        }

        val requestData = JSONObject().apply {
            put("code", scanData.code)
            put("action", actionData)
            if (!ignoreTimestamp) {
                put("timestamp", scanData.timestamp)
            }
        }

        val data = requestData.toString()
        val body = data.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Items-Device-Auth", authToken)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                storePendingScanData(scanData)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val pendingScanData = getScanDataFromFile()

                    if (pendingScanData != null) {
                        postScanData(pendingScanData, false)
                    }
                } else {
                    storePendingScanData(scanData)
                }
            }
        })
    }

    private fun storePendingScanData(scanData: ScanData) {
        val jsonString = Gson().toJson(scanData)
        val file = File(this.filesDir, SCAN_DATA_FILENAME)
        file.appendText(jsonString + "\n")
    }

    private fun getScanDataFromFile(): ScanData? {
        val file = File(this.filesDir, SCAN_DATA_FILENAME)
        if (!file.exists()) {
            return null
        }

        val scanDataList = mutableListOf<ScanData>()
        file.forEachLine { line ->
            val scanData = Gson().fromJson(line, ScanData::class.java)
            scanDataList.add(scanData)
        }

        if (scanDataList.isNotEmpty()) {
            val firstScanData = scanDataList.removeAt(0)
            file.writeText(scanDataList.joinToString(separator = "\n") { Gson().toJson(it) })

            return firstScanData
        }

        return null
    }
}