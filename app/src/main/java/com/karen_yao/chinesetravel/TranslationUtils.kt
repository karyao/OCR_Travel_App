package com.karen_yao.chinesetravel.util

import android.util.Log

fun translateChineseToEnglish(text: String): String {
    // Should not go here because if there is no chinese, then it shouldn't appear.
    if (text.isBlank()) {
        return "No text"
    }
    if (text.isBlank()) return "No text"
    // TODO: integrate ML Kit Translate for real translation
    Log.d("Translate", "Stub translating: $text")
    return "EN($text)"   // placeholder
}
