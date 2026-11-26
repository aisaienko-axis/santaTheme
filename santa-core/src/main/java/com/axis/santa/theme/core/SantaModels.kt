package com.axis.santa.theme.core

data class SantaRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

data class SantaScenario(
    val compWidth: Float = 520f,
    val compHeight: Float = 1000f,
    val seatX: Float = 300f,
    val seatY: Float = 380f,
    val santaHeight: Float = 207f,
    val size: Float = 0.5f,
    val hit: SantaRect = SantaRect(235f, 205f, 375f, 465f),
    val returnDelayMs: Long = 2500L,
)

data class SantaPlacement(
    val widthPx: Float,
    val heightPx: Float,
    val x: Float,
    val y: Float,
    val scale: Float,
)

data class SantaAnchor(
    val leftPx: Float,
    val topPx: Float,
    val widthPx: Float,
    val heightPx: Float,
)

data class SantaFrame(
    val marker: String,
    val speed: Float,
    val visible: Boolean,
    val playId: Int,
    val cancelId: Int,
    val interactive: Boolean,
)
