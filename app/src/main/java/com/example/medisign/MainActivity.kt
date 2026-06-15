package com.example.medisign

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.medisign.pipeline.PhraseRule
import com.example.medisign.pipeline.allergyRules
import com.example.medisign.pipeline.generalRules
import com.example.medisign.pipeline.medicineRules
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip

class MainActivity : AppCompatActivity() {

    private lateinit var chipMode: Chip
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var btnCapture: View

    // 🛡️ ไม่สร้าง (new) Fragment ทันที ป้องกันบั๊กหน้าจอซ้อนทับเวลาหมุนจอ
    private lateinit var gestureFragment: GestureFragment
    private lateinit var textFragment: TextFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupFragments(savedInstanceState)
        setupListeners()
        setupToggleGroup()
    }

    private fun bindViews() {
        chipMode = findViewById(R.id.chipMenu)
        toggleGroup = findViewById(R.id.toggleGroupMode)
        btnCapture = findViewById(R.id.btnCapture)
    }

    private fun setupFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            // โหลดแอปครั้งแรก สร้างใหม่ได้เลย
            gestureFragment = GestureFragment()
            textFragment = TextFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, gestureFragment, TAG_GESTURE)
                .add(R.id.fragmentContainer, textFragment, TAG_TEXT)
                .hide(textFragment)
                .commit()
        } else {
            // 🛡️ ป้องกันบั๊กหมุนจอ: ให้ไปค้นหา Fragment ตัวเดิมที่ระบบแบคอัปไว้กลับมาใช้
            gestureFragment = supportFragmentManager.findFragmentByTag(TAG_GESTURE) as GestureFragment
            textFragment = supportFragmentManager.findFragmentByTag(TAG_TEXT) as TextFragment
        }
    }

    private fun setupListeners() {
        val ivSettings = findViewById<ImageView>(R.id.ivSettings)
        ivSettings.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL))
            startActivity(intent)
        }

        chipMode.setOnClickListener { anchorView ->
            showPopupMenu(anchorView)
        }
    }

    private fun showPopupMenu(anchorView: View) {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_menu_layout, null)

        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 10f
        }

        // 🧹 ยุบโค้ดด้วยฟังก์ชันอัปเดตโมเดล อ่านง่ายและคลีนมาก!
        popupView.findViewById<View>(R.id.menu_sickness)?.setOnClickListener {
            updateMode("ป่วย", R.drawable.sick_24, MODEL_SICKNESS, null)
            popupWindow.dismiss()
        }

        popupView.findViewById<View>(R.id.menu_allergy)?.setOnClickListener {
            updateMode("แพ้", R.drawable.allergy_24, MODEL_ALLERGY, allergyRules)
            popupWindow.dismiss()
        }

        popupView.findViewById<View>(R.id.menu_medicine)?.setOnClickListener {
            updateMode("ยา", R.drawable.pill_24, MODEL_MEDICINE, medicineRules)
            popupWindow.dismiss()
        }

        popupView.findViewById<View>(R.id.menu_general)?.setOnClickListener {
            updateMode("ทั่วไป", R.drawable.conditions_24, MODEL_GENERAL, generalRules)
            popupWindow.dismiss()
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popupWindow.showAsDropDown(anchorView, 0, -(popupView.measuredHeight + anchorView.height + 20))
    }

    // 🎯 ตัวช่วยลดโค้ดซ้ำซ้อนตอนเปลี่ยนโหมด
    private fun updateMode(title: String, iconRes: Int, modelName: String, rules: List<PhraseRule>?) {
        chipMode.text = title
        chipMode.setChipIconResource(iconRes)
        gestureFragment.switchModel(modelName, rules)
    }

    private fun setupToggleGroup() {
        val btnGesture = findViewById<MaterialButton>(R.id.btnGesture)
        val btnText = findViewById<MaterialButton>(R.id.btnText)

        fun updateButtonColors(checkedId: Int) {
            val isGesture = checkedId == R.id.btnGesture

            btnGesture.setBackgroundColor(if (isGesture) Color.parseColor(COLOR_PRIMARY) else Color.TRANSPARENT)
            btnGesture.setTextColor(if (isGesture) Color.WHITE else Color.parseColor(COLOR_TEXT_INACTIVE))

            btnText.setBackgroundColor(if (!isGesture) Color.parseColor(COLOR_PRIMARY) else Color.TRANSPARENT)
            btnText.setTextColor(if (!isGesture) Color.WHITE else Color.parseColor(COLOR_TEXT_INACTIVE))
        }

        updateButtonColors(toggleGroup.checkedButtonId)

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                updateButtonColors(checkedId)

                val transaction = supportFragmentManager.beginTransaction()
                if (checkedId == R.id.btnGesture) {
                    transaction.show(gestureFragment).hide(textFragment)
                    chipMode.visibility = View.VISIBLE
                    btnCapture.visibility = View.VISIBLE
                } else {
                    transaction.show(textFragment).hide(gestureFragment)
                    chipMode.visibility = View.GONE
                    btnCapture.visibility = View.GONE
                }
                transaction.commit()
            }
        }
    }

    // 📚 แหล่งรวมค่าคงที่ แก้สี/แก้ลิงก์/แก้ชื่อโมเดล มาตรงนี้ที่เดียวจบ!
    companion object {
        private const val TAG_GESTURE = "gesture"
        private const val TAG_TEXT = "text"
        private const val WEB_URL = "https://nantipatspk.github.io/MediSign/"

        private const val MODEL_SICKNESS = "sickness_best.tflite"
        private const val MODEL_ALLERGY = "allergy_best.tflite"
        private const val MODEL_MEDICINE = "medicine_best.tflite"
        private const val MODEL_GENERAL = "general_best.tflite"
        private const val COLOR_PRIMARY = "#5D3FD3"
        private const val COLOR_TEXT_INACTIVE = "#99000000"
    }
}