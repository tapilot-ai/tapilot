package lifeos.md.tapilot.report

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportAssetRoutesTest {
    @Test
    fun recognizesReportLogosHostedByMidscene() {
        assertTrue(
            ReportAssetRoutes.isMidsceneImage(
                "https://lf3-static.bytednsdoc.com/obj/eden-cn/nupipfups/Midscene/" +
                    "midscene_with_text_light.png",
            ),
        )
        assertTrue(
            ReportAssetRoutes.isMidsceneImage(
                "https://lf3-static.bytednsdoc.com/obj/eden-cn/vhaeh7vhabf/favicon-32x32.png",
            ),
        )
    }

    @Test
    fun doesNotInterceptUnrelatedRemoteImages() {
        assertFalse(ReportAssetRoutes.isMidsceneImage("https://example.com/Midscene.png"))
        assertFalse(
            ReportAssetRoutes.isMidsceneImage(
                "https://lf3-static.bytednsdoc.com/obj/example/screenshot.png",
            ),
        )
    }
}
