package com.example.medisign.manager

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class SpeechManager(context: Context) : TextToSpeech.OnInitListener {

    // 🛡️ ป้องกัน Memory Leak ด้วย applicationContext และทำเป็น Nullable เพื่อให้ทำลายทิ้งได้
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isTtsReady = false

    private var lastSpokenWord = ""
    private var lastSpokenTime = 0L
    private var speakCount = 0

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("th", "TH"))
            isTtsReady = !(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED)

            if (!isTtsReady) {
                Log.w("SpeechManager", "⚠️ ไม่มีชุดเสียงภาษาไทย กำลังสลับไปใช้ภาษาอังกฤษ")
                tts?.setLanguage(Locale.ENGLISH)
                isTtsReady = true
            }
        } else {
            Log.e("SpeechManager", "❌ ระบบ TextToSpeech ขัดข้อง!")
        }
    }

    // 🚦 Synchronized ป้องกัน AI ทายผลรัวๆ จนเข้ามาเรียกฟังก์ชันนี้พร้อมกันหลาย Thread
    @Synchronized
    fun speak(text: String) {
        // เช็กว่า TTS พร้อมไหม, เป็นค่าว่างไหม หรือเป็นคำขยะที่ไม่อยากให้พูดไหม?
        if (!isTtsReady || text.isBlank() || IGNORED_WORDS.contains(text)) return

        val currentTime = System.currentTimeMillis()

        // ถ้าเป็นคำใหม่ รีเซ็ตตัวนับใหม่เลย
        if (text != lastSpokenWord) {
            lastSpokenWord = text
            speakCount = 0
        }

        // 🎯 ลอจิกสุดสมาร์ทของคุณ: พูดไม่เกิน 2 รอบ และต้องพ้น Cooldown
        if (speakCount < MAX_SPEAK_COUNT && (currentTime - lastSpokenTime) > COOLDOWN_MS) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            lastSpokenTime = currentTime
            speakCount++
            Log.d("SpeechManager", "📢 พูดคำว่า: $text (รอบที่ $speakCount)")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null // 🧹 คืน RAM ให้ระบบอย่างสมบูรณ์
    }

    // 📚 แหล่งรวมค่าคงที่และคำขยะ
    companion object {
        private const val COOLDOWN_MS = 2000L
        private const val MAX_SPEAK_COUNT = 2

        // 🗑️ โยนคำที่คุณไม่อยากให้ AI ออกเสียงมาใส่ในถังขยะนี้ได้เลย!
        private val IGNORED_WORDS = setOf(
            "รอทำนาย...",
            "ท่ารอ/ไม่มีความหมาย",
            "เปิดกล้อง..กำลังรอท่าทาง",
            "👀 กำลังประมวลผลคำ..."
        )
    }
}