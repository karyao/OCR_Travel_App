package com.karen_yao.chinesetravel.shared.utils

import android.icu.text.Transliterator
import java.text.Normalizer

/**
 * Utility functions for Chinese text to Pinyin conversion.
 * Uses built-in ICU transliteration for accurate conversion.
 */
object PinyinUtils {
    
    /**
     * Converts Chinese text to Pinyin using built-in ICU transliteration.
     * 
     * @param han The Chinese text to convert
     * @param lowercase Whether to return lowercase output
     * @param noToneMarks Whether to remove tone marks from output
     * @return Pinyin representation of the Chinese text
     */
    fun toPinyin(han: String, lowercase: Boolean = true, noToneMarks: Boolean = false): String {
        val hanLatin = Transliterator.getInstance("Han-Latin")
        var out = hanLatin.transliterate(han) // 上海火车站 -> Shàng hǎi huǒ chē zhàn

        if (noToneMarks) {
            out = Normalizer.normalize(out, Normalizer.Form.NFD)
                .replace("\\p{M}+".toRegex(), "") // strip accents
        }

        out = out.replace("\\s+".toRegex(), " ").trim()
        return if (lowercase) out.lowercase() else out
    }
}
