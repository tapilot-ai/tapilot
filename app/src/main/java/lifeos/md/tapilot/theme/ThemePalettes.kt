// Generated from theme/brand.json.
package lifeos.md.tapilot.theme

import lifeos.md.tapilot.R

internal data class ThemePalette(
    val background: Int,
    val surface: Int,
    val text: Int,
    val muted: Int,
    val line: Int,
    val primary: Int,
    val primaryDark: Int,
    val soft: Int,
    val success: Int,
    val danger: Int,
    val bubbleStart: Int,
    val bubbleMiddle: Int,
    val bubbleEnd: Int,
    val input: Int,
    val header: Int,
    val secondaryText: Int,
    val contrast: Int,
    val bubbleText: Int,
    val assistantBubble: Int,
    val cardAlt: Int,
    val strongLine: Int,
    val codeBackground: Int,
    val codeText: Int,
    val softStrong: Int,
    val softAlt: Int,
)

internal object ThemePalettes {
    private val light = ThemePalette(
        background = BrandColors.lightBackground,
        surface = BrandColors.lightSurface,
        text = BrandColors.lightText,
        muted = BrandColors.lightMuted,
        line = BrandColors.lightLine,
        primary = BrandColors.lightPrimary,
        primaryDark = BrandColors.lightPrimaryDark,
        soft = BrandColors.lightSoft,
        success = BrandColors.lightSuccess,
        danger = BrandColors.lightDanger,
        bubbleStart = BrandColors.lightBubbleStart,
        bubbleMiddle = BrandColors.lightBubbleMiddle,
        bubbleEnd = BrandColors.lightBubbleEnd,
        input = BrandColors.lightInput,
        header = BrandColors.lightHeader,
        secondaryText = BrandColors.lightSecondaryText,
        contrast = BrandColors.lightContrast,
        bubbleText = BrandColors.lightBubbleText,
        assistantBubble = BrandColors.lightAssistantBubble,
        cardAlt = BrandColors.lightCardAlt,
        strongLine = BrandColors.lightStrongLine,
        codeBackground = BrandColors.lightCodeBackground,
        codeText = BrandColors.lightCodeText,
        softStrong = BrandColors.lightSoftStrong,
        softAlt = BrandColors.lightSoftAlt,
    )
    private val dark = ThemePalette(
        background = BrandColors.darkBackground,
        surface = BrandColors.darkSurface,
        text = BrandColors.darkText,
        muted = BrandColors.darkMuted,
        line = BrandColors.darkLine,
        primary = BrandColors.darkPrimary,
        primaryDark = BrandColors.darkPrimaryDark,
        soft = BrandColors.darkSoft,
        success = BrandColors.darkSuccess,
        danger = BrandColors.darkDanger,
        bubbleStart = BrandColors.darkBubbleStart,
        bubbleMiddle = BrandColors.darkBubbleMiddle,
        bubbleEnd = BrandColors.darkBubbleEnd,
        input = BrandColors.darkInput,
        header = BrandColors.darkHeader,
        secondaryText = BrandColors.darkSecondaryText,
        contrast = BrandColors.darkContrast,
        bubbleText = BrandColors.darkBubbleText,
        assistantBubble = BrandColors.darkAssistantBubble,
        cardAlt = BrandColors.darkCardAlt,
        strongLine = BrandColors.darkStrongLine,
        codeBackground = BrandColors.darkCodeBackground,
        codeText = BrandColors.darkCodeText,
        softStrong = BrandColors.darkSoftStrong,
        softAlt = BrandColors.darkSoftAlt,
    )
    private val minimalLight = ThemePalette(
        background = BrandColors.minimalLightBackground,
        surface = BrandColors.minimalLightSurface,
        text = BrandColors.minimalLightText,
        muted = BrandColors.minimalLightMuted,
        line = BrandColors.minimalLightLine,
        primary = BrandColors.minimalLightPrimary,
        primaryDark = BrandColors.minimalLightPrimaryDark,
        soft = BrandColors.minimalLightSoft,
        success = BrandColors.minimalLightSuccess,
        danger = BrandColors.minimalLightDanger,
        bubbleStart = BrandColors.minimalLightBubbleStart,
        bubbleMiddle = BrandColors.minimalLightBubbleMiddle,
        bubbleEnd = BrandColors.minimalLightBubbleEnd,
        input = BrandColors.minimalLightInput,
        header = BrandColors.minimalLightHeader,
        secondaryText = BrandColors.minimalLightSecondaryText,
        contrast = BrandColors.minimalLightContrast,
        bubbleText = BrandColors.minimalLightBubbleText,
        assistantBubble = BrandColors.minimalLightAssistantBubble,
        cardAlt = BrandColors.minimalLightCardAlt,
        strongLine = BrandColors.minimalLightStrongLine,
        codeBackground = BrandColors.minimalLightCodeBackground,
        codeText = BrandColors.minimalLightCodeText,
        softStrong = BrandColors.minimalLightSoftStrong,
        softAlt = BrandColors.minimalLightSoftAlt,
    )
    private val minimalDark = ThemePalette(
        background = BrandColors.minimalDarkBackground,
        surface = BrandColors.minimalDarkSurface,
        text = BrandColors.minimalDarkText,
        muted = BrandColors.minimalDarkMuted,
        line = BrandColors.minimalDarkLine,
        primary = BrandColors.minimalDarkPrimary,
        primaryDark = BrandColors.minimalDarkPrimaryDark,
        soft = BrandColors.minimalDarkSoft,
        success = BrandColors.minimalDarkSuccess,
        danger = BrandColors.minimalDarkDanger,
        bubbleStart = BrandColors.minimalDarkBubbleStart,
        bubbleMiddle = BrandColors.minimalDarkBubbleMiddle,
        bubbleEnd = BrandColors.minimalDarkBubbleEnd,
        input = BrandColors.minimalDarkInput,
        header = BrandColors.minimalDarkHeader,
        secondaryText = BrandColors.minimalDarkSecondaryText,
        contrast = BrandColors.minimalDarkContrast,
        bubbleText = BrandColors.minimalDarkBubbleText,
        assistantBubble = BrandColors.minimalDarkAssistantBubble,
        cardAlt = BrandColors.minimalDarkCardAlt,
        strongLine = BrandColors.minimalDarkStrongLine,
        codeBackground = BrandColors.minimalDarkCodeBackground,
        codeText = BrandColors.minimalDarkCodeText,
        softStrong = BrandColors.minimalDarkSoftStrong,
        softAlt = BrandColors.minimalDarkSoftAlt,
    )
    fun get(id: String, dark: Boolean): ThemePalette =
        if (id == "minimal") { if (dark) minimalDark else minimalLight } else { if (dark) this.dark else light }

    fun resource(id: String, dark: Boolean, resource: Int): Int? {
        val palette = get(id, dark)
        return when (resource) {
            R.color.brand_background, R.color.brand_dark_background -> palette.background
            R.color.brand_surface, R.color.brand_dark_surface -> palette.surface
            R.color.brand_text, R.color.brand_dark_text -> palette.text
            R.color.brand_muted, R.color.brand_dark_muted -> palette.muted
            R.color.brand_line, R.color.brand_dark_line -> palette.line
            R.color.brand_primary, R.color.brand_dark_primary -> palette.primary
            R.color.brand_primary_dark, R.color.brand_dark_primary_dark -> palette.primaryDark
            R.color.brand_soft, R.color.brand_dark_soft -> palette.soft
            R.color.brand_success, R.color.brand_dark_success -> palette.success
            R.color.brand_danger, R.color.brand_dark_danger -> palette.danger
            R.color.brand_bubble_start, R.color.brand_dark_bubble_start -> palette.bubbleStart
            R.color.brand_bubble_middle, R.color.brand_dark_bubble_middle -> palette.bubbleMiddle
            R.color.brand_bubble_end, R.color.brand_dark_bubble_end -> palette.bubbleEnd
            R.color.brand_input, R.color.brand_dark_input -> palette.input
            R.color.brand_header, R.color.brand_dark_header -> palette.header
            R.color.brand_secondary_text, R.color.brand_dark_secondary_text -> palette.secondaryText
            R.color.brand_contrast, R.color.brand_dark_contrast -> palette.contrast
            R.color.brand_bubble_text, R.color.brand_dark_bubble_text -> palette.bubbleText
            R.color.brand_assistant_bubble, R.color.brand_dark_assistant_bubble -> palette.assistantBubble
            R.color.brand_card_alt, R.color.brand_dark_card_alt -> palette.cardAlt
            R.color.brand_strong_line, R.color.brand_dark_strong_line -> palette.strongLine
            R.color.brand_code_background, R.color.brand_dark_code_background -> palette.codeBackground
            R.color.brand_code_text, R.color.brand_dark_code_text -> palette.codeText
            R.color.brand_soft_strong, R.color.brand_dark_soft_strong -> palette.softStrong
            R.color.brand_soft_alt, R.color.brand_dark_soft_alt -> palette.softAlt
            else -> null
        }
    }
}
