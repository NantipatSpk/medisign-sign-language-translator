package com.example.medisign

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class TextFragment : Fragment(), TextToSpeech.OnInitListener {

    // 🛡️ ปรับเป็น Nullable เพื่อให้เคลียร์ขยะออกจาก RAM ได้ง่ายขึ้นตอนทำลาย View
    private var tts: TextToSpeech? = null
    private lateinit var etSpeechToText: EditText
    private lateinit var btnMic: MaterialCardView

    private var isTtsReady = false

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val recognized = results[0]
                val current = etSpeechToText.text.toString()

                val newText = if (current.isEmpty()) recognized else "$current $recognized"
                etSpeechToText.setText(newText)
                etSpeechToText.setSelection(newText.length)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_text, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        setupTTS()
        setupListeners()
    }

    private fun bindViews(view: View) {
        etSpeechToText = view.findViewById(R.id.etSpeechToText)
        btnMic         = view.findViewById(R.id.btnMic)

    }

    private fun setupTTS() {
        // 🛡️ Safe Memory: ใช้ applicationContext ป้องกัน Memory Leak ไม่ให้ TTS ยึดติดกับ Fragment
        tts = TextToSpeech(requireContext().applicationContext, this)
    }

    private fun setupListeners() {
        btnMic.setOnClickListener { startSpeechToText() }

    }

    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LOCALE_TH)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LOCALE_TH)
            putExtra(RecognizerIntent.EXTRA_PROMPT, PROMPT_LISTENING)
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            // 🛡️ Safe Call: เช็กก่อนว่าหน้าจอยังเปิดอยู่ไหม ค่อยโชว์ Toast ป้องกันแอปเด้ง
            context?.let {
                Toast.makeText(it, ERROR_STT_NOT_SUPPORTED, Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("th", "TH"))

            // เช็กว่าเครื่องมีภาษาไทยไหม
            isTtsReady = !(result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED)

            if (!isTtsReady) {
                // ถ้าไม่มีภาษาไทย ให้สลับไปใช้ภาษาอังกฤษแก้ขัด
                tts?.setLanguage(Locale.ENGLISH)
                isTtsReady = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 🛡️ ปิดและล้างข้อมูล TTS ทิ้งให้เกลี้ยง คืน RAM ให้ระบบ
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    // 📚 แหล่งรวมค่าคงที่และข้อความภาษาไทย
    companion object {
        private const val LOCALE_TH = "th-TH"
        private const val PROMPT_LISTENING = "กำลังฟัง..."
        private const val ERROR_STT_NOT_SUPPORTED = "อุปกรณ์ไม่รองรับ Speech to Text"
        private const val ERROR_TTS_NOT_READY = "ระบบเสียงยังไม่พร้อมใช้งาน"
    }
}