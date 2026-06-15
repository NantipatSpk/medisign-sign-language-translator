package com.example.medisign.pipeline

import com.example.medisign.mediapipe.TFLiteClassifier

class SignPredictor(private val classifier: TFLiteClassifier) {

    // 🚀 เปลี่ยนเป็น ArrayDeque ทำงานเร็วกว่า ArrayList เวลามีการดึงข้อมูลหัว-ท้าย (FIFO)
    private val sequence = ArrayDeque<FloatArray>(30)
    private val predictionHistory = ArrayDeque<FloatArray>(5)

    private val SEQ_LENGTH = 30
    private val FEATURE_SIZE = 344

    private val HISTORY_SIZE = 20

    private var lastValidFrame: FloatArray? = null
    private var lastPrediction: Pair<String, Float>? = null

    // 🕒 ตัวแปรล็อคจังหวะเวลา (Sampling Control)
    private var lastSampleTime = 0L
    private val TARGET_FPS = 20f
    private val SAMPLE_INTERVAL = (1000 / TARGET_FPS).toLong()

    // 🛡️ ZERO MEMORY ALLOCATION: จองพื้นที่ไว้ล่วงหน้าเลย ไม่ต้องไปสร้างใหม่ทุกเฟรมให้ RAM บวม!
    private val tfliteInput = Array(1) { Array(SEQ_LENGTH) { FloatArray(FEATURE_SIZE) } }
    private var avgScores: FloatArray? = null

    @Synchronized
    fun process(keypoints: FloatArray?): Pair<String, Float>? {
        val currentTime = System.currentTimeMillis()

        // 🛡️ 1. กรองข้อมูลเบื้องต้น
        val frame = when {
            keypoints != null && keypoints.size == FEATURE_SIZE -> {
                lastValidFrame = keypoints
                keypoints
            }
            lastValidFrame != null -> lastValidFrame!!
            else -> return null
        }

        // ⏱️ 2. ตรวจสอบจังหวะเวลา (20 FPS)
        if (currentTime - lastSampleTime < SAMPLE_INTERVAL) {
            return null
        }
        lastSampleTime = currentTime

        // 📥 3. เก็บข้อมูลเข้า Queue
        sequence.addLast(frame)
        if (sequence.size > SEQ_LENGTH) sequence.removeFirst()

        if (sequence.size < SEQ_LENGTH) return null

        // 🧠 4. นำข้อมูลยัดใส่กล่องที่เตรียมไว้ (หลีกเลี่ยงการสร้าง Array ใหม่)
        var i = 0
        for (f in sequence) {
            System.arraycopy(f, 0, tfliteInput[0][i], 0, FEATURE_SIZE)
            i++
        }

        val scores = try {
            classifier.predict(tfliteInput)
        } catch (e: Exception) {
            return null
        }

        // 🌪️ 5. Smoothing (เฉลี่ย 5 เฟรมล่าสุด ลื่นไหล ไม่หน่วง)
        predictionHistory.addLast(scores)
        if (predictionHistory.size > HISTORY_SIZE) predictionHistory.removeFirst()

        // จองพื้นที่ avgScores แค่ครั้งแรกครั้งเดียว
        if (avgScores == null || avgScores!!.size != scores.size) {
            avgScores = FloatArray(scores.size)
        }

        // เคลียร์ค่าเก่าเป็น 0
        avgScores!!.fill(0f)

        // รวมคะแนน
        for (h in predictionHistory) {
            for (j in h.indices) {
                avgScores!![j] += h[j]
            }
        }
        // หารเฉลี่ย
        for (j in avgScores!!.indices) {
            avgScores!![j] /= predictionHistory.size.toFloat()
        }

        // 🚀 6. ส่งผลลัพธ์ออกไป
        lastPrediction = classifier.getLabel(avgScores!!)
        return lastPrediction
    }

    @Synchronized
    fun reset() {
        sequence.clear()
        predictionHistory.clear()
        lastValidFrame = null
        lastPrediction = null
        lastSampleTime = 0L
    }
}