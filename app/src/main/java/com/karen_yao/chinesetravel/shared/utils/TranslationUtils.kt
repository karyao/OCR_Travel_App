package com.karen_yao.chinesetravel.shared.utils

import android.util.Log

/**
 * Utility functions for text translation.
 * Currently provides stub implementation for Chinese to English translation.
 */
object TranslationUtils {
    
    /**
     * Translates Chinese text to English.
     * TODO: Integrate with ML Kit Translate for real translation.
     * 
     * @param text The Chinese text to translate
     * @return Translated text or placeholder if translation fails
     */
    fun translateChineseToEnglish(text: String): String {
        if (text.isBlank()) {
            return "No text"
        }
        
        // TODO: integrate ML Kit Translate for real translation
        Log.d("TranslationUtils", "Stub translating: $text")
        return "EN($text)"   // placeholder
    }
}
