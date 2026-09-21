package lifeos.md.tapilot.report

object ReportAssetRoutes {
    private val midsceneImageNames = setOf(
        "Midscene.png",
        "midscene_with_text_light.png",
        "midscene_with_text_dark.png",
        "favicon-32x32.png",
    )

    fun isMidsceneImage(url: String): Boolean =
        url.startsWith("https://lf3-static.bytednsdoc.com/obj/") &&
            midsceneImageNames.any { name -> url.endsWith("/$name") }
}
