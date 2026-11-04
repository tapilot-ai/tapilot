package lifeos.md.tapilot.experiment

/** Coordinates inside a FIT_CENTER image; letterbox touches must never become device input. */
internal object PreviewCoordinates {
    fun map(x: Float, y: Float, viewWidth: Int, viewHeight: Int, width: Int, height: Int): Pair<Float, Float>? {
        if (viewWidth <= 0 || viewHeight <= 0 || width <= 0 || height <= 0 || !x.isFinite() || !y.isFinite()) return null
        val scale = minOf(viewWidth.toFloat() / width, viewHeight.toFloat() / height)
        val mappedX = (x - (viewWidth - width * scale) / 2) / scale
        val mappedY = (y - (viewHeight - height * scale) / 2) / scale
        return if (mappedX >= 0 && mappedY >= 0 && mappedX < width && mappedY < height) mappedX to mappedY else null
    }
}
