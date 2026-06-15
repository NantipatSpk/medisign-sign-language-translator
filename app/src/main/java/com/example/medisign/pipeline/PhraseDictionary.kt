package com.example.medisign.pipeline

// 📝 โครงสร้างของสูตรผสมคำ
data class PhraseRule(
    val sequence: List<String>,
    val resultPhrase: String
)

val allergyRules = listOf(
    PhraseRule(listOf("ยา", "แพ้"), "แพ้ยา"),
    PhraseRule(listOf("อาหาร", "แพ้"), "แพ้อาหาร"),
    PhraseRule(listOf("นม", "แพ้"), "แพ้นม"),
    PhraseRule(listOf("ยา", "แพ้","ไม่"), "ไม่แพ้ยา"),
    PhraseRule(listOf("อาหาร", "แพ้","ไม่"), "ไม่แพ้อาหาร")
)

val medicineRules = listOf(
    PhraseRule(listOf("ขอ", "ยา"), "ขอยา"),
    PhraseRule(listOf("ยา", "ปวด"), "ยาแก้ปวด"),
    PhraseRule(listOf("ยา", "ไข้"), "ยาแก้ไข้"),
    PhraseRule(listOf("ยา", "ไอ"), "ยาแก้ไอ"),
    PhraseRule(listOf("ยา", "แพ้"), "ยาแก้แพ้"),
    PhraseRule(listOf("ยา", "ท้องเสีย"), "ยาแก้ท้องเสีย"),
    PhraseRule(listOf("ยา", "ทาแผล"), "ยาทาแผล"),
    PhraseRule(listOf("ยา", "นอนหลับ"), "ยานอนหลับ"),
    PhraseRule(listOf("ยา", "ก่อน","อาหาร"), "ยาก่อนอาหาร"),
    PhraseRule(listOf("ยา", "หลัง","อาหาร"), "ยาหลังอาหาร"),
    PhraseRule(listOf("ยา", "น้ำ"), "ยาน้ำ"),
    PhraseRule(listOf("ยา", "เม็ด"), "ยาเม็ด"),
    PhraseRule(listOf("ยา", "ดม"), "ยาดม"),
    PhraseRule(listOf("ยา", "หยอดตา"), "ยาหยอดตา")
)
val generalRules = listOf(
    PhraseRule(listOf("คนเดียว"), "มาคนเดียว"),
    PhraseRule(listOf("ครอบครัว"), "มากับครอบครัว"),
    PhraseRule(listOf("เพื่อน"), "มากับเพื่อน"),
    PhraseRule(listOf("บัตรประชาชน"), "มีบัตรประชาชน"),
    PhraseRule(listOf("ไม่", "บัตรประชาชน"), "ไม่มีบัตรประชาชน"),
    PhraseRule(listOf("หนึ่ง", "ก่อน"), "เมื่อวานนี้"),
    PhraseRule(listOf("สอง", "ก่อน"), "สองวันก่อน"),
    PhraseRule(listOf("สัปดาห์", "ก่อน"), "อาทิตย์ก่อน"),
    PhraseRule(listOf("เดือน", "ก่อน"), "เดือนก่อน"),
    PhraseRule(listOf("ราคาเท่าไหร่"), "ราคาเท่าไหร่"),
    PhraseRule(listOf("หมอนัด"), "หมอนัด")

)