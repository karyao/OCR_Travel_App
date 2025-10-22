package com.karen_yao.chinesetravel.shared.utils

import android.util.Log
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Utility functions for text translation using ML Kit Translate.
 * Provides real Chinese to English translation.
 */
object TranslationUtils {
    
    private const val TAG = "TranslationUtils"
    
    /**
     * Translates Chinese text to English using ML Kit Translate.
     * 
     * @param text The Chinese text to translate
     * @return Translated text or fallback if translation fails
     */
    suspend fun translateChineseToEnglish(text: String): String {
        if (text.isBlank()) {
            return "No text"
        }
        
        return try {
            Log.d(TAG, "🔄 Starting ML Kit translation for: $text")
            
            // Create translator options for Chinese to English
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.CHINESE)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
            
            val translator = Translation.getClient(options)
            
            // Check if model is downloaded
            val modelDownloaded = checkModelDownloaded(translator)
            Log.d(TAG, "📱 Model downloaded: $modelDownloaded")
            
            if (!modelDownloaded) {
                Log.w(TAG, "⚠️ Translation model not downloaded, using fallback")
                translator.close()
                return getFallbackTranslation(text)
            }
            
            // Perform translation
            val result = translateText(translator, text)
            translator.close()
            
            Log.d(TAG, "✅ ML Kit translation result: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ ML Kit translation failed: ${e.message}")
            Log.d(TAG, "🔄 Using fallback translation for: $text")
            val fallback = getFallbackTranslation(text)
            Log.d(TAG, "📝 Fallback result: $fallback")
            fallback
        }
    }
    
    /**
     * Check if the translation model is downloaded.
     */
    private suspend fun checkModelDownloaded(translator: Translator): Boolean {
        return suspendCancellableCoroutine { continuation ->
            translator.downloadModelIfNeeded()
                .addOnSuccessListener {
                    Log.d(TAG, "✅ Model is ready")
                    continuation.resume(true)
                }
                .addOnFailureListener { exception ->
                    Log.w(TAG, "⚠️ Model not ready: ${exception.message}")
                    continuation.resume(false)
                }
        }
    }
    
    /**
     * Suspend function to handle ML Kit translation.
     */
    private suspend fun translateText(translator: Translator, text: String): String {
        return suspendCancellableCoroutine { continuation ->
            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    continuation.resume(translatedText)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Translation failed: ${exception.message}")
                    continuation.resume(getFallbackTranslation(text))
                }
        }
    }
    
    /**
     * Fallback translation for common travel terms when ML Kit fails.
     */
    private fun getFallbackTranslation(text: String): String {
        return when (text) {
            "欢迎光临" -> "Welcome (fallback)"
            "餐厅" -> "Restaurant (fallback)"
            "地铁站" -> "Subway Station (fallback)"
            "旅游景点" -> "Tourist Attraction (fallback)"
            "银行" -> "Bank (fallback)"
            "医院" -> "Hospital (fallback)"
            "商店" -> "Shop (fallback)"
            "市场" -> "Market (fallback)"
            "酒店" -> "Hotel (fallback)"
            "机场" -> "Airport (fallback)"
            "火车站" -> "Train Station (fallback)"
            "公交站" -> "Bus Stop (fallback)"
            "厕所" -> "Restroom (fallback)"
            "出口" -> "Exit (fallback)"
            "入口" -> "Entrance (fallback)"
            "左转" -> "Turn Left (fallback)"
            "右转" -> "Turn Right (fallback)"
            "直走" -> "Go Straight (fallback)"
            else -> "Translation unavailable (fallback)"
        }
    }
}
