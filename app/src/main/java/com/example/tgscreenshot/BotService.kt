package com.example.tgscreenshot

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class BotService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var client: OkHttpClient
    private var token = ""
    private var chatId = ""
    private var projection: MediaProjection? = null
    private lateinit var projMgr: MediaProjectionManager
    private lateinit var helper: ScreenshotHelper

    // متغیر جدید برای کنترل حلقه به جای isActive
    @Volatile
    private var isServiceRunning = true

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotif("در حال اجرا..."))
        projMgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)
        token = prefs.getString("token", "")!!
        chatId = prefs.getString("chatId", "")!!

        val code = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra("resultData", Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra("resultData")

        if (data != null) {
            projection = projMgr.getMediaProjection(code, data)
            helper = ScreenshotHelper(this, projection!!, resources.displayMetrics)
        }

        isServiceRunning = true
        scope.launch { pollUpdates() }
        return START_STICKY
    }

    private suspend fun pollUpdates() {
        var offset = 0L
        // استفاده از متغیر خودمان به جای isActive
        while (isServiceRunning) {
            try {
                val url = "https://api.telegram.org/bot$token/getUpdates?timeout=30&offset=$offset"
                val resp = client.newCall(Request.Builder().url(url).build()).execute()
                val body = resp.body?.string() ?: continue
                val arr = JSONObject(body).optJSONArray("result") ?: continue
                for (i in 0 until arr.length()) {
                    val upd = arr.getJSONObject(i)
                    offset = upd.getLong("update_id") + 1
                    val msg = upd.optJSONObject("message") ?: continue
                    val text = msg.optString("text", "")
                    val fromChat = msg.getJSONObject("chat").getLong("id").toString()
                    if (fromChat != chatId) continue

                    if (text == "/shot" || text == "/start") {
                        sendMessage("⏳ در حال گرفتن اسکرین‌شات...")
                        val bmp = helper.capture()
                        if (bmp != null) {
                            val ok = sendPhoto(bmp)
                            sendMessage(if (ok) "✅ ارسال شد" else "❌ خطا در ارسال")
                            bmp.recycle()
                        } else {
                            sendMessage("❌ صفحه خاموش است یا دسترسی قطع شده")
                        }
                    }
                }
            } catch (e: Exception) {
                delay(3000)
            }
        }
    }

    private fun sendMessage(text: String) {
        val url = "https://api.telegram.org/bot$token/sendMessage"
        val json = JSONObject().put("chat_id", chatId).put("text", text)
        client.newCall(
            Request.Builder().url(url)
                .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()
        ).execute().close()
    }

    private fun sendPhoto(bmp: Bitmap): Boolean {
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val bytes = baos.toByteArray()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("photo", "shot.jpg",
                bytes.toRequestBody("image/jpeg".toMediaTypeOrNull()))
            .build()
        val req = Request.Builder()
            .url("https://api.telegram.org/bot$token/sendPhoto")
            .post(body).build()
        return client.newCall(req).execute().use { it.isSuccessful }
    }

    private fun buildNotif(text: String): Notification {
        val chId = "bot_ch"
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(chId) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(chId, "Bot", NotificationManager.IMPORTANCE_LOW))
            }
        }
        return NotificationCompat.Builder(this, chId)
            .setContentTitle("TG Screenshot")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isServiceRunning = false
        scope.cancel()
        projection?.stop()
        super.onDestroy()
    }
}
