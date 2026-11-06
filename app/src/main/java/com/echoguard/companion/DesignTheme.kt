package com.echoguard.companion

import android.graphics.Color

/**
 * Echoguard Design Theme - Obsidian Shield
 * Extracted from Stitch MCP server
 * Dark mode, accessible, with strong secondary (green) and tertiary (red) alerts
 */
object DesignTheme {

    var isDarkMode = true

    // ========== COLORS ==========
    object Colors {
        // Surface & Background (dynamic - theme-aware)
        val BACKGROUND: String get() = if (isDarkMode) "#121414" else "#FFFFFF"
        val SURFACE: String get() = if (isDarkMode) "#121414" else "#FFFFFF"
        val SURFACE_DIM: String get() = if (isDarkMode) "#121414" else "#F5F5F5"
        val SURFACE_BRIGHT: String get() = if (isDarkMode) "#37393a" else "#E8E8E8"
        val SURFACE_CONTAINER_LOWEST: String get() = if (isDarkMode) "#0c0f0f" else "#F0F0F0"
        val SURFACE_CONTAINER_LOW: String get() = if (isDarkMode) "#1a1c1c" else "#FAFAFA"
        val SURFACE_CONTAINER: String get() = if (isDarkMode) "#1e2020" else "#FFFFFF"
        val SURFACE_CONTAINER_HIGH: String get() = if (isDarkMode) "#282a2b" else "#EEEEEE"
        val SURFACE_CONTAINER_HIGHEST: String get() = if (isDarkMode) "#333535" else "#E0E0E0"
        val SURFACE_VARIANT: String get() = if (isDarkMode) "#333535" else "#E0E0E0"
        val SURFACE_TINT: String get() = if (isDarkMode) "#c8c6c7" else "#797979"

        // Text
        val ON_BACKGROUND: String get() = if (isDarkMode) "#e2e2e2" else "#1c1b1f"
        val ON_SURFACE: String get() = if (isDarkMode) "#e2e2e2" else "#1c1b1f"
        val ON_SURFACE_VARIANT: String get() = if (isDarkMode) "#c7c6ca" else "#49454f"
        val INVERSE_SURFACE: String get() = if (isDarkMode) "#e2e2e2" else "#1c1b1f"
        val INVERSE_ON_SURFACE: String get() = if (isDarkMode) "#2f3131" else "#FFFFFF"

        // Primary (Neutral, used for structure)
        val PRIMARY: String get() = if (isDarkMode) "#c8c6c7" else "#797979"
        val ON_PRIMARY: String get() = if (isDarkMode) "#313031" else "#FFFFFF"
        val PRIMARY_CONTAINER: String get() = if (isDarkMode) "#0b0b0c" else "#E1E1E1"
        val ON_PRIMARY_CONTAINER: String get() = if (isDarkMode) "#7b797a" else "#1c1b1f"
        val PRIMARY_FIXED: String get() = if (isDarkMode) "#e5e2e3" else "#1c1b1f"
        val PRIMARY_FIXED_DIM: String get() = if (isDarkMode) "#c8c6c7" else "#929292"
        val ON_PRIMARY_FIXED: String get() = if (isDarkMode) "#1c1b1c" else "#FFFFFF"
        val ON_PRIMARY_FIXED_VARIANT: String get() = if (isDarkMode) "#474647" else "#49454f"
        val INVERSE_PRIMARY: String get() = if (isDarkMode) "#5f5e5f" else "#FFFFFF"

        // Secondary (Green - Success/Safe)
        val SECONDARY: String get() = if (isDarkMode) "#7dffa2" else "#006E1C"
        val ON_SECONDARY: String get() = if (isDarkMode) "#003918" else "#FFFFFF"
        val SECONDARY_CONTAINER: String get() = if (isDarkMode) "#05e777" else "#A4F4A5"
        val ON_SECONDARY_CONTAINER: String get() = if (isDarkMode) "#00622e" else "#002106"
        val SECONDARY_FIXED: String get() = if (isDarkMode) "#62ff96" else "#003919"
        val SECONDARY_FIXED_DIM: String get() = if (isDarkMode) "#00e475" else "#005228"
        val ON_SECONDARY_FIXED: String get() = if (isDarkMode) "#00210b" else "#FFFFFF"
        val ON_SECONDARY_FIXED_VARIANT: String get() = if (isDarkMode) "#005226" else "#002106"

        // Tertiary (Red - Warning/Error)
        val TERTIARY: String get() = if (isDarkMode) "#ffb3b3" else "#7D5260"
        val ON_TERTIARY: String get() = if (isDarkMode) "#680014" else "#FFFFFF"
        val TERTIARY_CONTAINER: String get() = if (isDarkMode) "#210003" else "#FFD8E4"
        val ON_TERTIARY_CONTAINER: String get() = if (isDarkMode) "#f3013d" else "#31111D"
        val TERTIARY_FIXED: String get() = if (isDarkMode) "#ffdad9" else "#FFD8CB"
        val TERTIARY_FIXED_DIM: String get() = if (isDarkMode) "#ffb3b3" else "#FFB3B3"
        val ON_TERTIARY_FIXED: String get() = if (isDarkMode) "#400009" else "#FFFFFF"
        val ON_TERTIARY_FIXED_VARIANT: String get() = if (isDarkMode) "#920021" else "#7D5260"

        // Error
        val ERROR: String get() = if (isDarkMode) "#ffb4ab" else "#BA1A1A"
        val ON_ERROR: String get() = if (isDarkMode) "#690005" else "#FFFFFF"
        val ERROR_CONTAINER: String get() = if (isDarkMode) "#93000a" else "#FFDAD6"
        val ON_ERROR_CONTAINER: String get() = if (isDarkMode) "#ffdad6" else "#410002"

        // Outline
        val OUTLINE: String get() = if (isDarkMode) "#919094" else "#797979"
        val OUTLINE_VARIANT: String get() = if (isDarkMode) "#46464a" else "#D5D5D5"

        // Risk Score Colors (Custom)
        val RISK_LOW: String get() = if (isDarkMode) "#00e475" else "#006E1C"
        val RISK_MEDIUM: String get() = if (isDarkMode) "#ffb3b3" else "#FFB3B3"
        val RISK_HIGH: String get() = if (isDarkMode) "#ff1744" else "#BA1A1A"
    }
    
    // ========== TYPOGRAPHY ==========
    object Typography {
        const val FONT_HEADLINE = "Lexend"
        const val FONT_BODY = "Inter"
        const val FONT_LABEL = "Inter"
        
        object HeadlineXL {
            const val SIZE = 40f
            const val WEIGHT = 700
            const val LINE_HEIGHT = 48f
            const val LETTER_SPACING = -0.02f
        }
        
        object HeadlineLg {
            const val SIZE = 32f
            const val WEIGHT = 600
            const val LINE_HEIGHT = 40f
            const val LETTER_SPACING = -0.01f
        }
        
        object HeadlineLgMobile {
            const val SIZE = 28f
            const val WEIGHT = 600
            const val LINE_HEIGHT = 34f
            const val LETTER_SPACING = 0f
        }
        
        object TitleMd {
            const val SIZE = 20f
            const val WEIGHT = 500
            const val LINE_HEIGHT = 28f
            const val LETTER_SPACING = 0f
        }
        
        object BodyLg {
            const val SIZE = 18f
            const val WEIGHT = 400
            const val LINE_HEIGHT = 28f
            const val LETTER_SPACING = 0f
        }
        
        object BodyMd {
            const val SIZE = 16f
            const val WEIGHT = 400
            const val LINE_HEIGHT = 24f
            const val LETTER_SPACING = 0f
        }
        
        object LabelMd {
            const val SIZE = 14f
            const val WEIGHT = 600
            const val LINE_HEIGHT = 20f
            const val LETTER_SPACING = 0.02f
        }
        
        object LabelSm {
            const val SIZE = 12f
            const val WEIGHT = 500
            const val LINE_HEIGHT = 16f
            const val LETTER_SPACING = 0.05f
        }
    }
    
    // ========== SPACING ==========
    object Spacing {
        const val XS = 8f
        const val SM = 12f
        const val MD = 16f
        const val LG = 24f
        const val XL = 32f
        const val GUTTER_MOBILE = 12f
        const val MARGIN_MOBILE = 20f
    }
    
    // ========== CORNER RADIUS ==========
    object Radius {
        const val SMALL = 8f
        const val MEDIUM = 12f
        const val LARGE = 16f
        const val FULL = 999f
    }
    
    // ========== RISK LEVEL HELPERS ==========
    fun getRiskColor(score: Int): Int {
        return when {
            score < 30 -> Color.parseColor(Colors.RISK_LOW)
            score < 70 -> Color.parseColor(Colors.TERTIARY)
            else -> Color.parseColor(Colors.RISK_HIGH)
        }
    }
    
    fun getRiskLabel(score: Int): String {
        return when {
            score < 30 -> "SAFE"
            score < 70 -> "CAUTION"
            else -> "DANGER"
        }
    }
    
    fun dpi(dp: Float): Int {
        // Will be called with context for actual dp conversion
        return (dp * 2.5).toInt() // Placeholder
    }
}
