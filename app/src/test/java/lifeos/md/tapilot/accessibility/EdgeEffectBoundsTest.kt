package lifeos.md.tapilot.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeEffectBoundsTest {
    @Test
    fun usesTheFullOverlayWithoutAnInset() {
        assertEquals(
            EdgeEffectBounds(0f, 0f, 1080f, 2400f),
            edgeEffectBounds(width = 1080, height = 2400),
        )
    }

    @Test
    fun createsTheInnerEdgeOfAUniformBand() {
        assertEquals(
            EdgeEffectBounds(16f, 16f, 1064f, 2384f),
            edgeEffectBounds(width = 1080, height = 2400, inset = 16f),
        )
    }

    @Test
    fun preservesEachPhysicalCornerRadiusEvenWhenSmallerThanFallback() {
        assertEquals(
            EdgeEffectCornerRadii(42f, 48f, 54f, 8f),
            edgeEffectCornerRadii(
                topLeft = 42,
                topRight = 48,
                bottomRight = 54,
                bottomLeft = 8,
                fallback = 24f,
            ),
        )
    }

    @Test
    fun onlyEstimatesMissingOrInvalidRadii() {
        assertEquals(
            EdgeEffectCornerRadii(24f, 24f, 24f, 80f),
            edgeEffectCornerRadii(null, 0, -1, 80, 24f),
        )
    }

    @Test
    fun preservesThePhysicalOuterCurveOnTheInnerEdgeOfABand() {
        assertEquals(
            EdgeEffectCornerRadii(62f, 62f, 62f, 62f),
            EdgeEffectCornerRadii(70f, 70f, 70f, 70f).insetBy(8f),
        )
    }

    @Test
    fun squareModeNeverInventsRoundedCorners() {
        assertEquals(
            EdgeEffectCornerRadii(0f, 0f, 0f, 0f),
            edgeEffectCornerRadiiForMode(
                EdgeEffectGeometryMode.SQUARE,
                EdgeEffectCornerRadii(70f, 70f, 70f, 70f),
                90f,
            ),
        )
    }

    @Test
    fun customModeUsesTheChosenRadiusOnEveryCorner() {
        assertEquals(
            EdgeEffectCornerRadii(96f, 96f, 96f, 96f),
            edgeEffectCornerRadiiForMode(
                EdgeEffectGeometryMode.CUSTOM,
                EdgeEffectCornerRadii(70f, 70f, 70f, 70f),
                96f,
            ),
        )
    }
}
