package com.example.medisign.mediapipe

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteClassifier(context: Context, private val modelName: String) {

    private var interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null

    // 🧠 โหลดชุดคำศัพท์ (Labels) ตามชื่อโมเดล
    private val labels: List<String> = getLabelsForModel(modelName)

    init {
        val modelBuffer = loadModelFile(context, modelName)
        val options = Interpreter.Options()

        try {
            val compatList = CompatibilityList()

            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatList.bestOptionsForThisDevice
                // 🚀 อนุญาตให้ใช้ Float16 และโมเดลที่ถูก Quantized
                delegateOptions.setPrecisionLossAllowed(true)
                delegateOptions.setQuantizedModelsAllowed(true)

                gpuDelegate = GpuDelegate(delegateOptions)
                options.addDelegate(gpuDelegate)
                Log.d("TFLite", "✅ เปิดใช้ GPU (Float16) สำหรับ $modelName สำเร็จ!")
            } else {
                // ⚠️ เครื่องไม่รองรับ GPU สลับไปใช้ CPU แทน
                options.setNumThreads(NUM_THREADS)
                options.setUseXNNPACK(true)
//                Log.w("TFLite", "⚠️ อุปกรณ์ไม่รองรับ GPU สลับมาใช้ CPU $NUM_THREADS Threads แทน")
            }
        } catch (e: Throwable) {
            // ดัก Error ขั้นสุด สลับกลับมาใช้ CPU ถ้า GPU พัง
            options.setNumThreads(NUM_THREADS)
            options.setUseXNNPACK(true)
//            Log.e("TFLite", "🔥 เกิดข้อผิดพลาดกับ GPU: ${e.message} (สลับมาใช้ CPU แทน)")
        }

        interpreter = Interpreter(modelBuffer, options)
    }

    // 🛡️ ป้องกัน File Stream Leak ด้วยการใช้ .use { } (มันจะสั่ง close() ให้อัตโนมัติเมื่อทำงานจบ)
    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        return context.assets.openFd(modelName).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                inputStream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            }
        }
    }

    // 🚦 ล็อคคิว! ป้องกัน AI ถูกเรียกประมวลผลทับซ้อนกันจน Crash
    @Synchronized
    fun predict(input: Array<Array<FloatArray>>): FloatArray {
        val numClasses = labels.size

        // 🛡️ เช็ก Input Shape จากตัวแปร Constant (แก้เปลี่ยนง่าย)
        if (input.isEmpty() || input[0].size != EXPECTED_FRAMES || input[0][0].size != EXPECTED_KEYPOINTS) {
            Log.e("TFLite", "⚠️ Input Shape ผิดพลาด! ข้ามการทำนายเฟรมนี้")
            return FloatArray(numClasses) { 0f }
        }

        val output = Array(1) { FloatArray(numClasses) }

        return try {
            interpreter.run(input, output)
            output[0]
        } catch (e: Exception) {
            Log.e("TFLite", "🔥 Predict Error: ${e.message}")
            FloatArray(numClasses) { 0f }
        }
    }

    fun getLabel(scores: FloatArray): Pair<String, Float> {
        if (scores.isEmpty()) return Pair(STATUS_WAITING, 0f)

        // หา Index ที่มีความน่าจะเป็นสูงที่สุด
        val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: 0
        return Pair(labels[maxIndex], scores[maxIndex])
    }

    fun predictLabel(input: Array<Array<FloatArray>>): Pair<String, Float> {
        val scores = predict(input)
        return getLabel(scores)
    }

    // 🧹 ล้างสมอง คืนแรมระบบ
    fun close() {
        try {
            interpreter.close()
            gpuDelegate?.close()
        } catch (e: Exception) {
            Log.e("TFLite", "🔥 Close Error: ${e.message}")
        }
    }

    private fun getLabelsForModel(name: String): List<String> {
        return when {
            name.contains("sickness") -> listOf("คลื่นไส้", "คันตา", "ท้องเสีย", "ปวดตา", "ปวดท้อง",
                "ปวดฟัน", "ปวดหลัง", "ปวดหัว", "มีผื่น", "ยืนนิ่ง", "หนาวสั่น", "หายใจไม่ออก", "อาเจียน", "เจ็บคอ", "เจ็บหน้าอก", "เจ็บแผล", "เป็นแผล", "เป็นไข้", "เวียนหัว", "เหนื่อย", "ไอ")
            name.contains("allergy") -> listOf("นม", "ยา", "ยืนนิ่ง", "อาหาร", "แพ้", "ไม่")
            name.contains("medicine") -> listOf("ก่อน", "ขอ", "ดม", "ทาแผล", "ท้องเสีย", "นอนหลับ",
                "น้ำ", "ปวด", "ยา", "ยืนนิ่ง", "หยอดตา", "หลัง", "อาหาร", "เม็ด", "แพ้", "ไข้", "ไอ")
            name.contains("general") -> listOf("ก่อน", "คนเดียว", "ครอบครัว", "บัตรประชาชน", "ยืนนิ่ง",
                "ราคาเท่าไหร่", "สอง", "สัปดาห์", "หนึ่ง", "หมอนัด", "เดือน", "เพื่อน", "ไม่")
            else -> listOf("Unknown Mode")
        }
    }

    // 💡 รวบรวมค่าคงที่ (Magic Numbers) เอาไว้ตรงนี้
    companion object {
        private const val NUM_THREADS = 4
        private const val EXPECTED_FRAMES = 30
        private const val EXPECTED_KEYPOINTS = 344
        private const val STATUS_WAITING = "รอผล..."
    }
}