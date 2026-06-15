package com.example.medisign.mediapipe

import android.content.Context
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class LandmarkerHelper(
    context: Context,
    private val resultListener: (
        HandLandmarkerResult?,
        PoseLandmarkerResult?,
        FaceLandmarkerResult?,
        Int,
        Int
    ) -> Unit
) {

    private val handLandmarker: HandLandmarker
    private val poseLandmarker: PoseLandmarker
    private val faceLandmarker: FaceLandmarker

    // 🛡️ ใช้ @Volatile เพื่อการันตีว่า Thread อื่นๆ จะมองเห็นค่าล่าสุดเสมอ (แก้ปัญหา Memory Visibility แบบไม่กิน CPU)
    @Volatile private var latestPoseResult: PoseLandmarkerResult? = null
    @Volatile private var latestFaceResult: FaceLandmarkerResult? = null

    init {
        // ==========================================
        // 1. POSE (ลูกน้อง) - หาเจอแล้วเก็บเข้ากระเป๋าเงียบๆ
        // ==========================================
        val poseBaseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_full.task")
            .setDelegate(Delegate.GPU)
            .build()

        val poseOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(poseBaseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result: PoseLandmarkerResult, _ ->
                latestPoseResult = result // แอบอัปเดตค่าล่าสุดไว้เงียบๆ
            }
            .build()
        poseLandmarker = PoseLandmarker.createFromOptions(context, poseOptions)

        // ==========================================
        // 2. FACE (ลูกน้อง) - หาเจอแล้วเก็บเข้ากระเป๋าเงียบๆ
        // ==========================================
        val faceBaseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .setDelegate(Delegate.GPU)
            .build()

        val faceOptions = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(faceBaseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result: FaceLandmarkerResult, _ ->
                latestFaceResult = result // แอบอัปเดตค่าล่าสุดไว้เงียบๆ
            }
            .build()
        faceLandmarker = FaceLandmarker.createFromOptions(context, faceOptions)

        // ==========================================
        // 3. HAND (หัวหน้า!) - ทำงานเสร็จเมื่อไหร่ เป็นคนสั่งส่งของ!
        // ==========================================
        val handBaseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(Delegate.GPU)
            .build()

        val handOptions = HandLandmarker.HandLandmarkerOptions.builder()
            .setNumHands(2)
            .setBaseOptions(handBaseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result: HandLandmarkerResult, input: MPImage ->
                // 🎯 ทันทีที่มือทำงานเสร็จ ให้รวบรวมของทั้งหมดแล้วส่งออกไปเลย 1 ครั้งต่อเฟรม!
                dispatchResults(result, input.width, input.height)
            }
            .build()
        handLandmarker = HandLandmarker.createFromOptions(context, handOptions)
    }

    private fun dispatchResults(handResult: HandLandmarkerResult, width: Int, height: Int) {
        // ส่งมอบงานของ Hand, Pose, และ Face กลับไปให้ SignLanguageManager
        resultListener(handResult, latestPoseResult, latestFaceResult, width, height)
    }

    fun detectAsync(mpImage: MPImage, timestamp: Long) {
        // สั่งให้ทั้ง 3 ตัวเริ่มทำงานพร้อมกันใน Background
        try {
            handLandmarker.detectAsync(mpImage, timestamp)
            poseLandmarker.detectAsync(mpImage, timestamp)
            faceLandmarker.detectAsync(mpImage, timestamp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        try {
            handLandmarker.close()
            poseLandmarker.close()
            faceLandmarker.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}