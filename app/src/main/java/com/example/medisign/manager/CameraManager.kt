package com.example.medisign.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner // 👈 Import ตัวนี้เพิ่มมา
import com.google.mediapipe.framework.image.BitmapImageBuilder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner, // 🛡️ รับค่า LifecycleOwner มาตรงๆ ปลอดภัยกว่า!
    private val previewView: PreviewView,
    private val onFrame: (mpImage: com.google.mediapipe.framework.image.MPImage, timestamp: Long) -> Unit
) {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var isPaused = true

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val targetResolution = Size(480, 640)

            preview = Preview.Builder()
                .setTargetResolution(targetResolution)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(targetResolution)
                .setTargetRotation(previewView.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
                if (isPaused) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                try {
                    // 🚀 ใช้ toBitmap() ของ CameraX โดยตรง (ไม่ต้องเขียนเองแล้ว) เร็วและประหยัด RAM กว่ามาก
                    val bitmap = imageProxy.toBitmap()
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees.toFloat()

                    // หมุนภาพเฉพาะตอนที่จำเป็นจริงๆ (ถ้าองศาเป็น 0 ก็ใช้ภาพเดิมเลย ลดภาระ CPU)
                    val finalBitmap = if (rotationDegrees != 0f) {
                        val matrix = Matrix().apply { postRotate(rotationDegrees) }
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    } else {
                        bitmap
                    }

                    val mpImage = BitmapImageBuilder(finalBitmap).build()
                    val timestamp = System.currentTimeMillis()

                    onFrame(mpImage, timestamp)

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    imageProxy.close() // ต้องปิดเสมอ ป้องกัน Memory Leak
                }
            }

            bindCamera()

        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCamera() {
        val provider = cameraProvider ?: return
        val prev = preview ?: return
        val analyzer = imageAnalyzer ?: return

        try {
            provider.unbindAll()

            if (!isPaused) {
                // 🛡️ ใช้ตัวแปร lifecycleOwner ที่รับมาแบบเซฟตี้ 100%
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    prev,
                    analyzer
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleCamera(active: Boolean) {
        isPaused = !active
        if (active) {
            bindCamera()
        } else {
            cameraProvider?.unbindAll()
        }
    }

    // 🧹 ฟังก์ชันสำหรับปิดกล้องและล้าง Thread คืนระบบ (สำคัญมาก!)
    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}