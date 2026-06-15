package com.example.medisign.manager

import android.content.Context
import com.example.medisign.mediapipe.KeypointExtractor
import com.example.medisign.mediapipe.LandmarkerHelper
import com.example.medisign.mediapipe.TFLiteClassifier
import com.example.medisign.pipeline.PhraseBuilder
import com.example.medisign.pipeline.PhraseRule
import com.example.medisign.pipeline.SignPredictor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class SignLanguageManager(
    private val context: Context,
    private var currentModel: String,
    private val onResult: (label: String, confidence: Float, handLandmarks: HandLandmarkerResult?, w: Int, h: Int) -> Unit
) {
    private var classifier = TFLiteClassifier(context, currentModel)
    private var predictor = SignPredictor(classifier)

    private val phraseBuilder = PhraseBuilder()
    private var usePhraseBuilder = false

    // 🚦 สร้างกุญแจล็อค (Lock) เพื่อป้องกันการเข้าถึง AI พร้อมกันหลายทาง
    private val lock = Any()

    private val landmarkerHelper = LandmarkerHelper(context) { hand, pose, face, w, h ->
        // 🚦 ถือค้อนล็อคไว้! ป้องกันไม่ให้ใครมาเปลี่ยนโมเดลตอนเรากำลังประมวลผล
        synchronized(lock) {
            val keypoints = KeypointExtractor.extract(pose, face, hand)
            val rawResult = if (keypoints != null) predictor.process(keypoints) else null

            // ถ้าไม่พบมือ หรือ ประมวลผลไม่ได้ ให้ส่งแค่พิกัดมือเปล่าๆ ไปให้หน้าจอวาด
            if (rawResult == null) {
                onResult("", 0f, hand, w, h)
                return@LandmarkerHelper // Early Exit (จบฟังก์ชันตรงนี้เลย โค้ดจะได้ไม่ต้องซ้อนลึก)
            }

            val (word, confidence) = rawResult

            // 🎯 การทำงานโหมดปกติ vs โหมดผสมคำ
            if (!usePhraseBuilder) {
                // โหมดอาการป่วย: ส่งทะลุไปเลย
                onResult(word, confidence, hand, w, h)
            } else {
                // โหมดภูมิแพ้: ส่งให้ PhraseBuilder ตรวจสอบก่อน
                val combinedResult = phraseBuilder.processNewWord(word, confidence)
                if (combinedResult != null) {
                    onResult(combinedResult.first, combinedResult.second, hand, w, h)
                } else {
                    onResult("", 0f, hand, w, h) // อมคำไว้ ยังไม่ส่งผลลัพธ์
                }
            }
        }
    }

    fun processImage(mpImage: MPImage, timestamp: Long) {
        landmarkerHelper.detectAsync(mpImage, timestamp)
    }

    fun switchModel(modelFile: String, rules: List<PhraseRule>?) {
        // 🚦 ถือค้อนล็อคไว้! สั่งให้ AI ที่กำลังประมวลผลภาพอยู่หยุดทำก่อน แล้วค่อยสลับโมเดล
        synchronized(lock) {
            currentModel = modelFile

            // ปิดของเก่า เคลียร์เมมโมรี่
            classifier.close()

            // สร้างของใหม่
            classifier = TFLiteClassifier(context, currentModel)
            predictor = SignPredictor(classifier)
            predictor.reset()

            if (!rules.isNullOrEmpty()) {
                usePhraseBuilder = true
                phraseBuilder.setRules(rules)
            } else {
                usePhraseBuilder = false
                phraseBuilder.reset()
            }
        }
    }

    fun resetPredictor() {
        synchronized(lock) {
            predictor.reset()
            phraseBuilder.reset()
        }
    }

    fun close() {
        synchronized(lock) {
            classifier.close()
            landmarkerHelper.close()
        }
    }
}