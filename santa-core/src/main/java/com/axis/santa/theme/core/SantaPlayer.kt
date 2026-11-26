package com.axis.santa.theme.core

class SantaPlayer(
    val scenario: SantaScenario = SantaScenario(),
    private val returnDelayMs: Long = scenario.returnDelayMs,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val cancelScheduled: () -> Unit,
) {
    private var state = "idle"
    private var loops = 0
    private var tapped = false
    private var screenActive = true
    private var scale = 1f
    private var actionName = "show"
    private var hasReturn = false
    private var segmentRunning = false
    private val listeners = mutableListOf<(SantaFrame) -> Unit>()

    var frame = SantaFrame(
        marker = "idle",
        speed = 1f,
        visible = true,
        playId = 0,
        cancelId = 0,
        interactive = true,
    )
        private set

    fun addListener(listener: (SantaFrame) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (SantaFrame) -> Unit) {
        listeners -= listener
    }

    fun markers(names: Set<String>) {
        actionName = if ("fall" in names) "fall" else "show"
        hasReturn = "return" in names
    }

    fun place(buttonLeft: Float, buttonTop: Float, buttonWidth: Float, buttonHeight: Float): SantaPlacement? {
        if (buttonWidth == 0f || buttonHeight == 0f) return null
        val fitted = buttonHeight / scenario.santaHeight
        scale = fitted * scenario.size
        val seatX = buttonLeft + buttonHeight / 2f + 10f * fitted
        return SantaPlacement(
            widthPx = scenario.compWidth * scale,
            heightPx = scenario.compHeight * scale,
            x = seatX - scenario.seatX * scale,
            y = buttonTop - scenario.seatY * scale,
            scale = scale,
        )
    }

    fun onAttached() {
        play("idle")
    }

    fun onStartPressed() {
        if (tapped || !state.startsWith("idle")) return
        tapped = true
        if (segmentRunning) {
            publish(visible = true, speed = 3f)
        } else {
            play(actionName, visible = true)
        }
    }

    fun setScreenActive(active: Boolean) {
        screenActive = active
        if (active) {
            publish(visible = true)
            if (!segmentRunning && state.startsWith("idle")) play("idle")
            return
        }
        if (state.startsWith("idle") && !tapped) {
            segmentRunning = false
            publish(visible = false, cancel = true, interactive = false)
        }
    }

    fun onSegmentEnded() {
        segmentRunning = false
        when {
            state.startsWith("idle") -> {
                if (tapped) {
                    tapped = false
                    play(actionName)
                    return
                }
                if (!screenActive) {
                    publish(visible = false, interactive = false)
                    return
                }
                loops++
                play(
                    when {
                        loops % 9 == 0 -> "idle_wave"
                        loops % 4 == 0 -> "idle_blink"
                        else -> "idle"
                    }
                )
            }

            state == "show" || state == "fall" -> {
                if (!hasReturn) {
                    loops = 0
                    play("idle")
                    return
                }
                state = "gone"
                publish(interactive = false)
                schedule(returnDelayMs) { play("return") }
            }

            state == "return" -> {
                loops = 0
                if (screenActive) {
                    play("idle")
                } else {
                    state = "idle"
                    publish(visible = false, interactive = false)
                }
            }
        }
    }

    fun isHit(eventX: Float, eventY: Float, santaX: Float, santaY: Float): Boolean {
        if (scale <= 0f) return false
        val compositionX = (eventX - santaX) / scale
        val compositionY = (eventY - santaY) / scale
        return scenario.hit.contains(compositionX, compositionY) && state.startsWith("idle")
    }

    fun onHitDown() {
        if (tapped) return
        tapped = true
        publish(speed = 3f)
    }

    fun release() {
        cancelScheduled()
        segmentRunning = false
        listeners.clear()
    }

    private fun play(name: String, visible: Boolean = frame.visible) {
        state = name
        segmentRunning = true
        publish(
            marker = name,
            speed = 1f,
            visible = visible,
            restart = true,
            interactive = name.startsWith("idle"),
        )
    }

    private fun publish(
        marker: String = frame.marker,
        speed: Float = frame.speed,
        visible: Boolean = frame.visible,
        restart: Boolean = false,
        cancel: Boolean = false,
        interactive: Boolean = state.startsWith("idle") && visible,
    ) {
        frame = frame.copy(
            marker = marker,
            speed = speed,
            visible = visible,
            playId = if (restart) frame.playId + 1 else frame.playId,
            cancelId = if (cancel) frame.cancelId + 1 else frame.cancelId,
            interactive = interactive,
        )
        listeners.toList().forEach { it(frame) }
    }
}
