package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.ManaBlackBg
import com.example.ui.theme.ManaBlackFg
import com.example.ui.theme.ManaBlueBg
import com.example.ui.theme.ManaBlueFg
import com.example.ui.theme.ManaColorlessBg
import com.example.ui.theme.ManaColorlessFg
import com.example.ui.theme.ManaGreenBg
import com.example.ui.theme.ManaGreenFg
import com.example.ui.theme.ManaRedBg
import com.example.ui.theme.ManaRedFg
import com.example.ui.theme.ManaWhiteBg
import com.example.ui.theme.ManaWhiteFg

enum class ManaColor(
    val code: String,
    val label: String,
    val bgColor: Color,
    val fgColor: Color
) {
    W("W", "White", ManaWhiteBg, ManaWhiteFg),
    U("U", "Blue", ManaBlueBg, ManaBlueFg),
    B("B", "Black", ManaBlackBg, ManaBlackFg),
    R("R", "Red", ManaRedBg, ManaRedFg),
    G("G", "Green", ManaGreenBg, ManaGreenFg),
    C("C", "Colorless", ManaColorlessBg, ManaColorlessFg);

    companion object {
        fun fromCode(code: String): ManaColor? =
            entries.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }

        fun orderedList(codes: List<String>): List<ManaColor> {
            val mapped = codes.mapNotNull { fromCode(it) }.distinct()
            return entries.filter { it in mapped }
        }

        fun identityName(codes: List<String>): String {
            val ordered = orderedList(codes)
            if (ordered.isEmpty() || ordered == listOf(C)) return "Colorless"
            val wubrg = ordered.filter { it != C }.map { it.code }.joinToString("")
            return when (wubrg) {
                "W" -> "Mono-White"
                "U" -> "Mono-Blue"
                "B" -> "Mono-Black"
                "R" -> "Mono-Red"
                "G" -> "Mono-Green"
                "WU" -> "Azorius"
                "UB" -> "Dimir"
                "BR" -> "Rakdos"
                "RG" -> "Gruul"
                "WG" -> "Selesnya"
                "WB" -> "Orzhov"
                "UR" -> "Izzet"
                "BG" -> "Golgari"
                "WR" -> "Boros"
                "UG" -> "Simic"
                "WUB" -> "Esper"
                "UBR" -> "Grixis"
                "BRG" -> "Jund"
                "WRG" -> "Naya"
                "WUG" -> "Bant"
                "WBG" -> "Abzan"
                "WUR" -> "Jeskai"
                "UBG" -> "Sultai"
                "WBR" -> "Mardu"
                "URG" -> "Temur"
                "WUBR" -> "Yore-Tiller"
                "UBRG" -> "Glint-Eye"
                "WBRG" -> "Dune-Brood"
                "WURG" -> "Ink-Treader"
                "WUBG" -> "Witch-Maw"
                "WUBRG" -> "5-Color"
                else -> wubrg
            }
        }
    }
}
