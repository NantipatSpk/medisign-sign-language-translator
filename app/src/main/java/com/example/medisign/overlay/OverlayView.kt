package com.example.medisign.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker

class OverlayView(context: Context, attrs: AttributeSet?) :
    View(context, attrs) {

    // ===== Paint =====
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC107") // สี Amber ตาม Material Design
        style = Paint.Style.FILL
    }

    // เส้นเชื่อม (Connections) : สีฟ้าไซอัน พร้อมเงา


//    private val posePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//        color = Color.WHITE
//        style = Paint.Style.FILL
//    }

    private val handLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF") // สี Cyanสว่าง
        strokeWidth = 6f // ขนาดเส้นกำลังดี ไม่หนาเกินไป
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND // ปลายเส้นมน
        strokeJoin = Paint.Join.ROUND // รอยต่อเส้นมน
        // 💥 เคล็ดลับความสวย: ใส่เงาสีดำบางๆ ให้เส้นลอยเด่นขึ้นมา
        setShadowLayer(4f, 0f, 0f, Color.parseColor("#80000000"))
    }

//    private val poseLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//        color = Color.BLUE
//        strokeWidth = 5f
//        style = Paint.Style.STROKE
//        strokeCap = Paint.Cap.ROUND
//        strokeJoin = Paint.Join.ROUND
//    }

    private var handResult: HandLandmarkerResult? = null
    private var poseResult: PoseLandmarkerResult? = null

    private var imageWidth = 0
    private var imageHeight = 0

    // 🔥 ลบพารามิเตอร์ face ออกจากฟังก์ชัน setResults
    fun setResults(
        hand: HandLandmarkerResult?,
        pose: PoseLandmarkerResult?,
        imgWidth: Int,
        imgHeight: Int
    ) {

        if (hand != null) handResult = hand
        if (pose != null) poseResult = pose

        imageWidth = imgWidth
        imageHeight = imgHeight

        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (imageWidth == 0 || imageHeight == 0) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        val scale = maxOf(
            viewWidth / imageWidth,
            viewHeight / imageHeight
        )

        val scaledWidth = imageWidth * scale
        val scaledHeight = imageHeight * scale

        val offsetX = (viewWidth - scaledWidth) / 2f
        val offsetY = (viewHeight - scaledHeight) / 2f

        fun mapX(x: Float): Float {
            return x * imageWidth * scale + offsetX
        }

        fun mapY(y: Float): Float {
            return y * imageHeight * scale + offsetY
        }

        // =========================
        // HAND (Optimized Version)
        // =========================
        handResult?.landmarks()?.forEach { landmarks ->

            // 1. จองพื้นที่หน่วยความจำล่วงหน้า
            val mappedX = FloatArray(21)
            val mappedY = FloatArray(21)

            // 2. คำนวณพิกัดแค่ "ครั้งเดียว" ต่อ 1 จุด แล้ววาดวงกลมเลย
            for (i in 0 until 21) {
                mappedX[i] = mapX(landmarks[i].x())
                mappedY[i] = mapY(landmarks[i].y())

                canvas.drawCircle(
                    mappedX[i],
                    mappedY[i],
                    8f,
                    handPaint
                )
            }

            // 3. วาดเส้นโดยดึงพิกัดที่คำนวณไว้แล้วมาใช้เลย (ไม่ต้องคำนวณใหม่!)
            HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                canvas.drawLine(
                    mappedX[connection.start()],
                    mappedY[connection.start()],
                    mappedX[connection.end()],
                    mappedY[connection.end()],
                    handLinePaint
                )
            }
        }

        // =========================
        // POSE
        // =========================
//        poseResult?.landmarks()?.forEach { landmarks ->
//
//            landmarks.forEach { point ->
//                canvas.drawCircle(
//                    mapX(point.x()),
//                    mapY(point.y()),
//                    8f,
//                    posePaint
//                )
//            }
//
//            val connections = listOf(
//                11 to 12,
//                11 to 13, 13 to 15,
//                12 to 14, 14 to 16,
//                11 to 23, 12 to 24,
//                23 to 24,
//                23 to 25, 25 to 27,
//                24 to 26, 26 to 28
//            )
//
//            connections.forEach { (startIdx, endIdx) ->
//                val start = landmarks[startIdx]
//                val end = landmarks[endIdx]
//
//                canvas.drawLine(
//                    mapX(start.x()),
//                    mapY(start.y()),
//                    mapX(end.x()),
//                    mapY(end.y()),
//                    poseLinePaint
//                )
//            }
//        }
    }
}