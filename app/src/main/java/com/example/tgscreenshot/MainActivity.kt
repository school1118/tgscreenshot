package com.example.tgscreenshot

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private val REQ_PROJ = 1001
    private val REQ_NOTIF = 1002
    private lateinit var tokenEt: EditText
    private lateinit var chatIdEt: EditText
    private lateinit var startBtn: Button
    private lateinit var projManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenEt = findViewById(R.id.tokenEt)
        chatIdEt = findViewById(R.id.chatIdEt)
        startBtn = findViewById(R.id.startBtn)

        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        tokenEt.setText(prefs.getString("token", ""))
        chatIdEt.setText(prefs.getString("chatId", ""))

        projManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }

        startBtn.setOnClickListener {
            val token = tokenEt.text.toString().trim()
            val chatId = chatIdEt.text.toString().trim()
            if (token.isEmpty() || chatId.isEmpty()) {
                Toast.makeText(this, "توکن و chatId را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit().putString("token", token).putString("chatId", chatId).apply()
            startActivityForResult(projManager.createScreenCaptureIntent(), REQ_PROJ)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PROJ && resultCode == Activity.RESULT_OK && data != null) {
            val svc = Intent(this, BotService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("resultData", data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc) else startService(svc)
            Toast.makeText(this, "سرویس فعال شد. /shot را در بات بزنید", Toast.LENGTH_LONG).show()
        }
    }
}
