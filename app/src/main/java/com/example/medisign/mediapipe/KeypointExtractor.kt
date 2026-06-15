package com.example.medisign.mediapipe

import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.hypot
import kotlin.math.max

object KeypointExtractor {

    private val FACE_KEY_POINTS = intArrayOf(
        70, 63, 105, 66, 107, 336, 296, 334, 293, 300,
        159, 145, 33, 133, 386, 374, 362, 263,
        1, 2, 98, 327, 13, 14, 17, 18
    )

    fun extract(
        poseResult: PoseLandmarkerResult?,
        faceResult: FaceLandmarkerResult?,
        handsResult: HandLandmarkerResult?
    ): FloatArray? {

        val poseLandmarks = poseResult?.landmarks()?.firstOrNull() ?: return null

        // 🚀 สร้าง Array คำตอบสุดท้ายแค่ชิ้นเดียว (ค่าเริ่มต้นคือ 0.0f ทั้งหมด)
        val result = FloatArray(344)
        var idx = 0

        // ==========================================
        // 1. คำนวณ Center และ Scale ล่วงหน้า
        // ==========================================
        val p11 = poseLandmarks[11]
        val p12 = poseLandmarks[12]
        val p23 = poseLandmarks[23]
        val p24 = poseLandmarks[24]

        val cx = (p11.x() + p12.x()) / 2f
        val cy = (p11.y() + p12.y()) / 2f
        val cz = (p11.z() + p12.z()) / 2f

        val torsoLength = hypot(
            (cx - (p23.x() + p24.x()) / 2f).toDouble(),
            (cy - (p23.y() + p24.y()) / 2f).toDouble()
        ).toFloat()

        val shoulderWidth = hypot(
            (p11.x() - p12.x()).toDouble(),
            (p11.y() - p12.y()).toDouble()
        ).toFloat()

        val scale = max(max(torsoLength, shoulderWidth), 0.05f)

        // ==========================================
        // 2. POSE (33 จุด) -> คำนวณ Normalize แล้วยัดลง Array ตรงๆ
        // ==========================================
        var poseVisSum = 0f
        for (i in 0 until 33) {
            val p = poseLandmarks[i]
            result[idx++] = (p.x() - cx) / scale
            result[idx++] = (p.y() - cy) / scale
            result[idx++] = (p.z() - cz) / scale

            val vis = p.visibility().orElse(0f)
            result[idx++] = vis
            poseVisSum += vis // คำนวณผลรวม Visibility ไปในตัว ไม่ต้องใช้ .map()
        }

        // ==========================================
        // 3. FACE (26 จุด)
        // ==========================================
        val faceLandmarks = faceResult?.faceLandmarks()?.firstOrNull()
        val hasFace = faceLandmarks != null
        if (hasFace) {
            for (i in FACE_KEY_POINTS) {
                val f = faceLandmarks!![i]
                result[idx++] = (f.x() - cx) / scale
                result[idx++] = (f.y() - cy) / scale
                result[idx++] = (f.z() - cz) / scale
            }
        } else {
            idx += 26 * 3 // ข้ามไปเลย เพราะค่า default ใน FloatArray คือ 0.0f อยู่แล้ว
        }

        // ==========================================
        // 4. HANDS (ซ้าย 21, ขวา 21)
        // ==========================================
        var leftHandList: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>? = null
        var rightHandList: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>? = null

        val hands = handsResult?.landmarks()
        val handednesses = handsResult?.handednesses()

        if (!hands.isNullOrEmpty() && !handednesses.isNullOrEmpty()) {
            for (i in hands.indices) {
                val category = handednesses[i].firstOrNull()?.categoryName()
                if (category.equals("Left", ignoreCase = true)) leftHandList = hands[i]
                else if (category.equals("Right", ignoreCase = true)) rightHandList = hands[i]
            }
        }

        val hasLh = leftHandList != null
        if (hasLh) {
            for (i in 0 until 21) {
                val h = leftHandList!![i]
                result[idx++] = (h.x() - cx) / scale
                result[idx++] = (h.y() - cy) / scale
                result[idx++] = (h.z() - cz) / scale
            }
        } else {
            idx += 21 * 3
        }

        val hasRh = rightHandList != null
        if (hasRh) {
            for (i in 0 until 21) {
                val h = rightHandList!![i]
                result[idx++] = (h.x() - cx) / scale
                result[idx++] = (h.y() - cy) / scale
                result[idx++] = (h.z() - cz) / scale
            }
        } else {
            idx += 21 * 3
        }

        // ==========================================
        // 5. SPATIAL FEATURES (8 ค่า)
        // ==========================================
        fun dist(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Float {
            return hypot(hypot((x1 - x2).toDouble(), (y1 - y2).toDouble()), (z1 - z2).toDouble()).toFloat()
        }

        // ดึงพิกัดที่ผ่านการ Normalize แล้วออกมาจาก result array
        val noseX = result[0]; val noseY = result[1]; val noseZ = result[2]
        val neckX = 0f; val neckY = 0f; val neckZ = 0f // หลัง Normalize ค่า Center คือ 0,0,0

        // หาพิกัดข้อมือ (จุดที่ 8 ของแต่ละมือ)
        val lh8Idx = 132 + 78 + (8 * 3) // Pose(132) + Face(78) + Offsetมือ
        val rh8Idx = 132 + 78 + 63 + (8 * 3)

        val lh8X = result[lh8Idx]; val lh8Y = result[lh8Idx + 1]; val lh8Z = result[lh8Idx + 2]
        val rh8X = result[rh8Idx]; val rh8Y = result[rh8Idx + 1]; val rh8Z = result[rh8Idx + 2]

        val dRhNose = if (hasRh) dist(rh8X, rh8Y, rh8Z, noseX, noseY, noseZ) else 1.0f
        val dLhNose = if (hasLh) dist(lh8X, lh8Y, lh8Z, noseX, noseY, noseZ) else 1.0f
        val dRhNeck = if (hasRh) dist(rh8X, rh8Y, rh8Z, neckX, neckY, neckZ) else 1.0f
        val dLhNeck = if (hasLh) dist(lh8X, lh8Y, lh8Z, neckX, neckY, neckZ) else 1.0f

        val yRhNose = if (hasRh) rh8Y - noseY else 0.0f
        val yLhNose = if (hasLh) lh8Y - noseY else 0.0f

        val dHands = if (hasRh && hasLh) dist(rh8X, rh8Y, rh8Z, lh8X, lh8Y, lh8Z) else 1.0f
        val poseVisAvg = poseVisSum / 33f

        // ยัด 8 ค่าสุดท้ายลงไปให้ครบ 344
        result[idx++] = dRhNose
        result[idx++] = dLhNose
        result[idx++] = dRhNeck
        result[idx++] = dLhNeck
        result[idx++] = yRhNose
        result[idx++] = yLhNose
        result[idx++] = dHands
        result[idx++] = poseVisAvg

        return result
    }
}