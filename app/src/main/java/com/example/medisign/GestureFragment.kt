package com.example.medisign

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.medisign.manager.CameraManager
import com.example.medisign.manager.SignLanguageManager
import com.example.medisign.manager.SpeechManager
import com.example.medisign.overlay.OverlayView
import com.example.medisign.pipeline.PhraseRule
import com.google.android.material.card.MaterialCardView

class GestureFragment : Fragment() {

    // UI Components
    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var textViewResult: TextView
    private lateinit var btnCapture: MaterialCardView
    private lateinit var btnInnerRecord: MaterialCardView

    // Managers
    private lateinit var cameraManager: CameraManager
    private lateinit var signLanguageManager: SignLanguageManager
    private lateinit var speechManager: SpeechManager

    // States
    private var isRecording = false
    private var pendingModel: String = "sickness_best.tflite"
    private var pendingRules: List<PhraseRule>? = null

    // UI Optimization Variables
    private var lastDisplayedText = ""
    private var lastResultTime = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gesture, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews()
        setupManagers()
        setupListeners()
        checkPermissions()

        updateStatusText(STATUS_CAMERA_OFF)
    }

    private fun bindViews() {
        previewView = view?.findViewById(R.id.previewView) ?: return
        overlay = view?.findViewById(R.id.overlay) ?: return
        textViewResult = view?.findViewById(R.id.textViewResult) ?: return

        btnCapture = requireActivity().findViewById(R.id.btnCapture)
        btnInnerRecord = requireActivity().findViewById(R.id.btnInnerRecord)
    }

    private fun setupManagers() {
        speechManager = SpeechManager(requireContext())

        signLanguageManager = SignLanguageManager(
            requireContext(),
            pendingModel
        ) { label, confidence, handLandmarks, w, h ->

            if (!isRecording) return@SignLanguageManager

            // 🛡️ Safe UI Thread: ป้องกันแอปเด้งเวลา Fragment ถูกทำลายไปแล้ว
            val currentActivity = activity ?: return@SignLanguageManager

            currentActivity.runOnUiThread {
                overlay.setResults(handLandmarks, null, w, h)

                val currentTime = System.currentTimeMillis()
                val newTextToDisplay: String

                if (label.isNotEmpty() && confidence > MIN_CONFIDENCE_THRESHOLD) {
                    newTextToDisplay = "$label (${(confidence * 100).toInt()}%)"

                    if (lastDisplayedText != newTextToDisplay) {
                        speechManager.speak(label)
                        lastResultTime = currentTime
                    }
                } else {
                    if (currentTime - lastResultTime > RESULT_HOLD_MS) {
                        newTextToDisplay = if (handLandmarks != null && handLandmarks.landmarks().isNotEmpty()) {
                            STATUS_PROCESSING
                        } else {
                            STATUS_CAMERA_ON
                        }
                    } else {
                        newTextToDisplay = lastDisplayedText
                    }
                }

                updateStatusText(newTextToDisplay)
            }
        }

        if (pendingRules != null) {
            signLanguageManager.switchModel(pendingModel, pendingRules)
        }

        cameraManager = CameraManager(requireContext(), viewLifecycleOwner, previewView) { mpImage, timestamp ->
            if (isRecording) {
                signLanguageManager.processImage(mpImage, timestamp)
            }
        }
    }

    private fun setupListeners() {
        btnCapture.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_CODE
                )
                return@setOnClickListener
            }

            isRecording = !isRecording
            animateRecordButton(isRecording)

            if (isRecording) {
                cameraManager.startCamera()
                cameraManager.toggleCamera(true)
                updateStatusText(STATUS_CAMERA_ON)
                lastResultTime = 0L
            } else {
                cameraManager.toggleCamera(false)
                updateStatusText(STATUS_CAMERA_OFF)
                overlay.setResults(null, null, 0, 0)
                signLanguageManager.resetPredictor()
                lastResultTime = 0L
            }
        }
    }

    fun switchModel(modelName: String, rules: List<PhraseRule>? = null) {
        if (::signLanguageManager.isInitialized) {
            signLanguageManager.switchModel(modelName, rules)
            signLanguageManager.resetPredictor()

            val stateText = if (isRecording) STATUS_CAMERA_ON else STATUS_CAMERA_OFF
            updateStatusText(stateText)
            lastResultTime = 0L
        } else {
            pendingModel = modelName
            pendingRules = rules
        }
    }

    // 🧹 สร้างฟังก์ชันแยกสำหรับอัปเดต Text เพื่อลดโค้ดซ้ำซ้อน
    private fun updateStatusText(newText: String) {
        if (lastDisplayedText != newText) {
            textViewResult.text = newText
            lastDisplayedText = newText
        }
    }

    private fun animateRecordButton(recording: Boolean) {
        val density = resources.displayMetrics.density
        val startSize = if (recording) 58f else 32f
        val endSize = if (recording) 32f else 58f
        val startRadius = if (recording) 29f else 8f
        val endRadius = if (recording) 8f else 29f

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 300L
        animator.addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float
            val currentSize = (startSize + (endSize - startSize) * fraction) * density
            val currentRadius = (startRadius + (endRadius - startRadius) * fraction) * density

            val params = btnInnerRecord.layoutParams
            params.width = currentSize.toInt()
            params.height = currentSize.toInt()
            btnInnerRecord.layoutParams = params
            btnInnerRecord.radius = currentRadius
        }
        animator.start()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 🛡️ ป้องกัน Leak: สั่งหยุดบันทึกก่อนทำลาย View
        isRecording = false
        if (::cameraManager.isInitialized) cameraManager.toggleCamera(false)
        if (::signLanguageManager.isInitialized) signLanguageManager.close()
        if (::speechManager.isInitialized) speechManager.shutdown()
    }

    // 📚 เก็บค่าคงที่ (Constants) และข้อความต่างๆ ไว้ตรงนี้
    // วันหลังจะแก้ข้อความ หรือแก้ระยะเวลา ก็มาแก้ตรงนี้ที่เดียวจบ!
    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val RESULT_HOLD_MS = 3000L
        private const val MIN_CONFIDENCE_THRESHOLD = 0.75f

        private const val STATUS_CAMERA_OFF = "กล้องปิดอยู่"
        private const val STATUS_CAMERA_ON = "เปิดกล้อง..กำลังรอท่าทาง"
        private const val STATUS_PROCESSING = "👀 กำลังประมวลผลคำ..."
    }
}