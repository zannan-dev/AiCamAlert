package com.example.aicamalert.ui.components

/** Reverse luminance for night viewing while retaining geographic color cues. */
internal object NightMapStyle {
    val backgroundColor: Int = 0xFF20262D.toInt()

    fun colorMatrix(): FloatArray {
        val luminance = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
        val contrast = 0.8f
        val saturation = 0.7f
        val highlights = floatArrayOf(224f, 230f, 239f)
        return FloatArray(20).apply {
            for (channel in 0..2) {
                for (input in 0..2) {
                    this[channel * 5 + input] =
                        (if (channel == input) saturation else 0f) -
                            (contrast + saturation) * luminance[input]
                }
                this[channel * 5 + 4] = highlights[channel]
            }
            // Keep yellow road fills warm and distinct from green vegetation.
            this[1] += 0.55f
            this[2] -= 0.55f
            this[6] += 0.25f
            this[7] -= 0.25f
            this[18] = 1f // Keep the tile's alpha unchanged.
        }
    }
}
