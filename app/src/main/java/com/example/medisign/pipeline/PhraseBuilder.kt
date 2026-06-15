package com.example.medisign.pipeline

import android.util.Log

class PhraseBuilder {
    private val wordBuffer = mutableListOf<Pair<String, Float>>()
    private var activeRules = listOf<PhraseRule>()
    private var vocabulary = setOf<String>()

    private var cooldownUntil = 0L
    private val COOLDOWN_MS = 3000L // พูดจบแล้วพัก 2 วิ
    private val MIN_CONFIDENCE = 0.80f // เกราะป้องกันท่าขยะ

    @Synchronized
    fun setRules(rules: List<PhraseRule>) {
        activeRules = rules
        vocabulary = rules.flatMap { it.sequence }.toSet()
        reset()
        Log.d("AI_TEST", "📚 โหลดพจนานุกรม: $vocabulary")
    }

    fun processNewWord(newWord: String, confidence: Float): Pair<String, Float>? {
        val currentTime = System.currentTimeMillis()

        // 1. โหมดพัก (ปิดหูปิดตา 2 วินาทีหลังพูดจบ)
        if (currentTime < cooldownUntil) return null

        // =======================================================
        // 🎯 2. ลอจิกปุ่ม ENTER (ท่ายืนนิ่ง)
        // ต้องมั่นใจเกิน MIN_CONFIDENCE ด้วย ป้องกันขยะตอนเปลี่ยนมือ!
        // =======================================================
        if (newWord == "ยืนนิ่ง" && confidence >= MIN_CONFIDENCE) {
            if (wordBuffer.isEmpty()) return null // ถ้าไม่ได้อมคำอะไรไว้ ก็ปล่อยผ่าน

            Log.d("AI_TEST", "🛑 ตรวจพบท่ายืนนิ่ง (กด Enter!) กำลังสรุปผล...")
            val result = evaluateBuffer()
            reset() // สรุปผลเสร็จ ล้าง Buffer ทันที

            if (result != null) {
                cooldownUntil = currentTime + COOLDOWN_MS
                Log.d("AI_TEST", "📢 ส่งคำตอบไปพูด: [${result.first}] (พัก 2 วิ)")
                return result
            } else {
                Log.d("AI_TEST", "❌ กด Enter แต่ไม่เข้าสูตรไหนเลย -> ล้างทิ้ง")
                return null
            }
        }

        // 3. กรองคำขยะ (ถ้าไม่ใช่คำในสูตร หรือมั่นใจน้อยไป ให้เตะทิ้ง)
        if (!vocabulary.contains(newWord) || confidence < MIN_CONFIDENCE) {
            return null
        }

        // 4. ดึงคำเข้า Buffer
        if (wordBuffer.isEmpty() || wordBuffer.last().first != newWord) {

            val testSequence = wordBuffer.map { it.first } + newWord
            val isPathValid = activeRules.any { rule ->
                rule.sequence.take(testSequence.size) == testSequence
            }

            if (isPathValid) {
                // ✅ ตรงสูตร -> อมคำไว้
                wordBuffer.add(Pair(newWord, confidence))
                Log.d("AI_TEST", "✅ อมคำเข้าปาก: ${wordBuffer.map { it.first }}")
            } else {
                // ⚠️ ผิดสูตร -> ล้างไพ่
                Log.d("AI_TEST", "⚠️ ผิดสูตร! ล้างไพ่ทิ้ง แล้วเริ่มใหม่ที่: [$newWord]")
                wordBuffer.clear()
                wordBuffer.add(Pair(newWord, confidence))
            }
        }
        // ถ้าเป็นคำเดิมซ้ำๆ ระบบก็จะแค่ปล่อยผ่านไป ไม่ทำอะไร

        // 🤫 ไม่ว่ายังไงก็จะไม่ส่งคำตอบ จนกว่าจะเจอท่ายืนนิ่ง
        return null
    }

    private fun evaluateBuffer(): Pair<String, Float>? {
        val currentSequence = wordBuffer.map { it.first }
        val matchedRule = activeRules.find { it.sequence == currentSequence }

        if (matchedRule != null) {
            val avgConfidence = wordBuffer.map { it.second }.average().toFloat()
            Log.d("AI_TEST", "🎯 ตรงสูตร! สรุปรวมคำได้ -> ${matchedRule.resultPhrase}")
            return Pair(matchedRule.resultPhrase, avgConfidence)
        }

        // 💡 ของแถม: ถ้าอยากให้ทำท่าเดียวเดี่ยวๆ แล้วกดยืนนิ่ง เพื่อให้แอปพูดคำนั้นออกมา ให้เปิดคอมเมนต์ตรงนี้ครับ:
        /*
        if (currentSequence.size == 1) {
            Log.d("AI_TEST", "🧍 ท่าเดี่ยวโดดๆ -> ส่งออก ${currentSequence.first()}")
            return Pair(currentSequence.first(), wordBuffer.first().second)
        }
        */

        return null
    }

    fun reset() {
        wordBuffer.clear()
    }
}