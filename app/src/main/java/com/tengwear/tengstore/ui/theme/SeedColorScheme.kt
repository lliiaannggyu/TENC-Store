package com.tengwear.tengstore.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sin

/**
 * 由种子色生成一整套「官方色阶」风格的深色 M3 配色。
 * （移植自 AppOpsPro 的 SeedColorScheme.kt）
 *
 * 在 Lab 空间里按固定 L 值（即 M3 tone）取色，拿到与官方色调一致的明度层级；
 * 彩度从目标值逐步下调做 gamut mapping，避免高色调越界导致色相偏移。
 *
 * 规则：
 * - 强调色族：彩度按 M3 TonalSpot（primary 36 / secondary 16 / tertiary 24，
 *   tertiary 色相 +60°），色调按 Wear M3E 深色（90 / 80 / 30 / 10 / 95）。
 * - 中性表面：用种子色相但低彩度（neutral 10 / neutralVariant 16），
 *   背景、列表卡、描边都带同一色系。
 */

private const val CHROMA_PRIMARY = 36f
private const val CHROMA_SECONDARY = 16f
private const val CHROMA_TERTIARY = 24f
private const val CHROMA_NEUTRAL = 10f
private const val CHROMA_NEUTRAL_VARIANT = 16f

// D65 白点
private const val XN = 0.95047f
private const val YN = 1.0f
private const val ZN = 1.08883f

/** 由种子色生成完整 [ColorScheme]（深色）。 */
fun colorSchemeFromSeed(seed: Color): ColorScheme {
    val hue = seedHue(seed)
    val hueT = (hue + 60f) % 360f
    return defaultWearColorScheme().copy(
        // ── 强调色族（TonalSpot）─────────────────────
        primary = labColor(90f, CHROMA_PRIMARY, hue),
        primaryDim = labColor(80f, CHROMA_PRIMARY, hue),
        primaryContainer = labColor(30f, CHROMA_PRIMARY, hue),
        onPrimary = labColor(10f, CHROMA_PRIMARY, hue),
        onPrimaryContainer = labColor(95f, CHROMA_PRIMARY, hue),

        secondary = labColor(90f, CHROMA_SECONDARY, hue),
        secondaryDim = labColor(80f, CHROMA_SECONDARY, hue),
        secondaryContainer = labColor(30f, CHROMA_SECONDARY, hue),
        onSecondary = labColor(10f, CHROMA_SECONDARY, hue),
        onSecondaryContainer = labColor(95f, CHROMA_SECONDARY, hue),

        tertiary = labColor(90f, CHROMA_TERTIARY, hueT),
        tertiaryDim = labColor(80f, CHROMA_TERTIARY, hueT),
        tertiaryContainer = labColor(30f, CHROMA_TERTIARY, hueT),
        onTertiary = labColor(10f, CHROMA_TERTIARY, hueT),
        onTertiaryContainer = labColor(95f, CHROMA_TERTIARY, hueT),

        // ── 中性表面（带种子色相，低彩度）────────────
        background = labColor(10f, CHROMA_NEUTRAL, hue),
        onBackground = labColor(92f, CHROMA_NEUTRAL, hue),
        surfaceContainerLow = labColor(10f, CHROMA_NEUTRAL, hue),
        surfaceContainer = labColor(13f, CHROMA_NEUTRAL, hue),
        surfaceContainerHigh = labColor(18f, CHROMA_NEUTRAL, hue),
        onSurface = labColor(92f, CHROMA_NEUTRAL, hue),
        onSurfaceVariant = labColor(80f, CHROMA_NEUTRAL_VARIANT, hue),
        outline = labColor(60f, CHROMA_NEUTRAL_VARIANT, hue),
        outlineVariant = labColor(30f, CHROMA_NEUTRAL_VARIANT, hue),
    )
}

/** 种子色的 Lab 色相角（0..360）。 */
internal fun seedHue(seed: Color): Float {
    val r = srgbToLinear(seed.red)
    val g = srgbToLinear(seed.green)
    val b = srgbToLinear(seed.blue)
    val x = 0.4124f * r + 0.3576f * g + 0.1805f * b
    val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
    val z = 0.0193f * r + 0.1192f * g + 0.9505f * b
    val fx = fLab(x / XN)
    val fy = fLab(y / YN)
    val fz = fLab(z / ZN)
    val a = 500f * (fx - fy)
    val bb = 200f * (fy - fz)
    val deg = atan2(bb, a) * 180f / PI.toFloat()
    return (deg + 360f) % 360f
}

/** 由 (L 色调, Lab 彩度, 色相角) 生成 sRGB 颜色，带 gamut mapping。 */
internal fun labColor(tone: Float, chroma: Float, hueDeg: Float): Color {
    var c = chroma
    while (c > 0.5f) {
        val rgb = labToRgb(tone, c, hueDeg)
        if (rgb[0] in 0f..1f && rgb[1] in 0f..1f && rgb[2] in 0f..1f) {
            return Color(rgb[0], rgb[1], rgb[2])
        }
        c -= 1f
    }
    val rgb = labToRgb(tone, 0f, hueDeg)
    return Color(rgb[0].coerceIn(0f, 1f), rgb[1].coerceIn(0f, 1f), rgb[2].coerceIn(0f, 1f))
}

/** (L, chroma, hue) → 原始 sRGB 三分量（0..1，可能越界，交给 gamut mapping 处理）。 */
private fun labToRgb(tone: Float, chroma: Float, hueDeg: Float): FloatArray {
    val rad = hueDeg * PI.toFloat() / 180f
    val a = chroma * cos(rad)
    val b = chroma * sin(rad)
    val fy = (tone + 16f) / 116f
    val fx = fy + a / 500f
    val fz = fy - b / 200f
    val x = XN * fLabInv(fx)
    val y = YN * fLabInv(fy)
    val z = ZN * fLabInv(fz)
    val r = 3.2406f * x - 1.5372f * y - 0.4986f * z
    val g = -0.9689f * x + 1.8758f * y + 0.0415f * z
    val bb = 0.0557f * x - 0.2040f * y + 1.0570f * z
    return floatArrayOf(linearToSrgb(r), linearToSrgb(g), linearToSrgb(bb))
}

// ── sRGB ↔ 线性 / Lab 基础数学 ──────────────────────────────────────────

private fun srgbToLinear(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun linearToSrgb(c: Float): Float =
    if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1f / 2.4f) - 0.055f

private fun fLab(t: Float): Float =
    if (t > 0.008856f) cbrt(t) else (7.787f * t + 16f / 116f)

private fun fLabInv(t: Float): Float {
    val t3 = t * t * t
    return if (t3 > 0.008856f) t3 else (t - 16f / 116f) / 7.787f
}

/**
 * API 回退配色（Wear OS 深色基调），也是 [colorSchemeFromSeed] 的中性底。
 * Wear M3 的 ColorScheme 没有 surface / surfaceVariant 参数，
 * 使用 surfaceContainer / surfaceContainerLow / surfaceContainerHigh 替代。
 */
internal fun defaultWearColorScheme(): ColorScheme {
    return ColorScheme(
        primary = Color(0xFFA8C7FA),
        onPrimary = Color(0xFF00315D),
        primaryContainer = Color(0xFF004A77),
        onPrimaryContainer = Color(0xFFD3E3FD),
        secondary = Color(0xFFBFC6DC),
        onSecondary = Color(0xFF293041),
        secondaryContainer = Color(0xFF3F4759),
        onSecondaryContainer = Color(0xFFDBE2F9),
        tertiary = Color(0xFFDDBCE0),
        onTertiary = Color(0xFF3F2844),
        tertiaryContainer = Color(0xFF573E5C),
        onTertiaryContainer = Color(0xFFFAD7FC),
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFF9DEDC),
        background = Color(0xFF1B1B1F),
        onBackground = Color(0xFFE3E2E6),
        surfaceContainerLow = Color(0xFF1B1B1F),
        surfaceContainer = Color(0xFF1B1B1F),
        surfaceContainerHigh = Color(0xFF2B2B2F),
        onSurface = Color(0xFFE3E2E6),
        onSurfaceVariant = Color(0xFFC4C6D0),
        outline = Color(0xFF8E9099),
    )
}

/** 取色的内容前景色（黑 / 白），用于在任意底色的色块上画对勾（移植自 AppOpsPro）。 */
fun contentOn(background: Color): Color {
    // 相对亮度（sRGB → 线性后按 Rec.709 加权）
    val l = 0.2126f * srgbToLinear(background.red) +
        0.7152f * srgbToLinear(background.green) +
        0.0722f * srgbToLinear(background.blue)
    return if (l > 0.5f) Color.Black else Color.White
}
