package com.karen_yao.chinesetravel.shared.utils

import android.util.Log
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

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
        
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.CHINESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        var translator: Translator? = null
        return try {
            val client = Translation.getClient(options)
            translator = client
            client.downloadModelIfNeeded().await()
            client.translate(text).await()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Translation unavailable; using fallback", error)
            getFallbackTranslation(text)
        } finally {
            translator?.close()
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
