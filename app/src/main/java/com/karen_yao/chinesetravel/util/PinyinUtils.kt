package com.karen_yao.chinesetravel.util

import android.icu.text.Transliterator
import java.text.Normalizer

/**
 * Converts Chinese text to Pinyin using built-in ICU transliteration.
 * - Removes tone marks if [noToneMarks] = true
 * - Lowercases output if [lowercase] = true
 */
fun toPinyin(han: String, lowercase: Boolean = true, noToneMarks: Boolean = true): String {
    val hanLatin = Transliterator.getInstance("Han-Latin")
    var out = hanLatin.transliterate(han) // 上海火车站 -> Shàng hǎi huǒ chē zhàn

    if (noToneMarks) {
        out = Normalizer.normalize(out, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "") // strip accents
    }

    out = out.replace("\\s+".toRegex(), " ").trim()
    return if (lowercase) out.lowercase() else out
}
