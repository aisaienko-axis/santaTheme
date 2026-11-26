package com.axis.santa.theme.core

class SantaPlayer(
    val scenario: SantaScenario = SantaScenario(),
    size: Float = scenario.size,
    animationDurationMs: Long = 0L,
    private val returnDelayMs: Long = scenario.returnDelayMs,
    private val schedule: (Long, () -> Unit) -> Unit,
    private val cancelScheduled: () -> Unit,
) {
    var size: Float = size.coerceAtLeast(0.01f)
        private set
    var animationDurationMs: Long = animationDurationMs.coerceAtLeast(0L)
        private set

    private enum class Pending { Dance, Fall }

    var asset: SantaAsset = SantaAsset.Seat
        private set

    private var state = "idle"
    private var loops = 0
    private var pending: Pending? = null
    private var onFallFinished: (() -> Unit)? = null
    private var screenActive = true
    private var stopped = false
    private var scale = 1f
    private var hasReturn = true
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
        hasReturn = "return" in names
    }

    fun setSize(value: Float) {
        if (value <= 0f) return
        size = value
    }

    fun setAnimationDuration(durationMs: Long) {
        animationDurationMs = durationMs.coerceAtLeast(0L)
    }

    fun stop() {
        stopped = true
        pending = null
        onFallFinished = null
        loops = 0
        state = "idle"
        asset = SantaAsset.Seat
        segmentRunning = false
        cancelScheduled()
        publish(
            marker = "idle",
            speed = 1f,
            visible = false,
            durationMs = 0L,
            asset = SantaAsset.Seat,
            cancel = true,
            interactive = false,
        )
    }

    fun place(buttonLeft: Float, buttonTop: Float, buttonWidth: Float, buttonHeight: Float): SantaPlacement? {
        if (buttonWidth == 0f || buttonHeight == 0f) return null
        val fitted = buttonHeight / scenario.santaHeight
        scale = fitted * size
        val compWidth = if (asset == SantaAsset.Dance) scenario.danceCompWidth else scenario.compWidth
        val compHeight = if (asset == SantaAsset.Dance) scenario.danceCompHeight else scenario.compHeight
        val seatX = buttonLeft + buttonHeight / 2f + 10f * fitted
        return SantaPlacement(
            widthPx = compWidth * scale,
            heightPx = compHeight * scale,
            x = seatX - scenario.seatX * scale,
            y = buttonTop - scenario.seatY * scale,
            scale = scale,
        )
    }

    fun onAttached() {
        play("idle")
    }

    fun onStartPressed(onFinished: (() -> Unit)? = null): Boolean {
        if (pending == Pending.Fall || state == "fall" || state == "gone" || state == "return") return false
        if (stopped || state == "show") {
            stopped = false
            cancelScheduled()
            pending = null
            segmentRunning = false
            state = "idle"
            asset = SantaAsset.Seat
            publish(
                marker = "idle",
                speed = 1f,
                visible = true,
                durationMs = 0L,
                asset = SantaAsset.Seat,
                cancel = true,
                interactive = false,
            )
        } else if (pending == Pending.Dance) {
            pending = null
        }
        if (!state.startsWith("idle")) return false
        onFallFinished = onFinished
        return queue(Pending.Fall)
    }

    fun setScreenActive(active: Boolean) {
        screenActive = active
        if (active) {
            val resume = stopped
            stopped = false
            publish(visible = true)
            if (resume || (!segmentRunning && state.startsWith("idle"))) play("idle")
            return
        }
        cancelScheduled()
        val leave = state == "gone" || state == "return" || (state.startsWith("idle") && pending == null)
        if (leave) {
            segmentRunning = false
            pending = null
            state = "idle"
            asset = SantaAsset.Seat
            publish(visible = false, asset = SantaAsset.Seat, cancel = true, interactive = false)
        }
    }

    fun onSegmentEnded() {
        if (stopped) return
        segmentRunning = false
        when {
            state.startsWith("idle") -> {
                if (pending != null) {
                    startPending()
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

            state == "show" -> {
                loops = 0
                if (screenActive) {
                    play("idle")
                } else {
                    state = "idle"
                    asset = SantaAsset.Seat
                    publish(visible = false, asset = SantaAsset.Seat, interactive = false)
                }
            }

            state == "fall" -> {
                val finished = onFallFinished
                onFallFinished = null
                finished?.invoke()
                if (!screenActive) {
                    state = "idle"
                    asset = SantaAsset.Seat
                    publish(visible = false, asset = SantaAsset.Seat, cancel = true, interactive = false)
                    return
                }
                if (!hasReturn) {
                    loops = 0
                    play("idle")
                    return
                }
                state = "gone"
                publish(interactive = false)
                schedule(returnDelayMs) {
                    if (!screenActive) {
                        state = "idle"
                        asset = SantaAsset.Seat
                        publish(visible = false, asset = SantaAsset.Seat, interactive = false)
                        return@schedule
                    }
                    play("return")
                }
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
        if (stopped || !frame.visible || scale <= 0f || pending != null || !state.startsWith("idle")) return false
        val compositionX = (eventX - santaX) / scale
        val compositionY = (eventY - santaY) / scale
        return scenario.hit.contains(compositionX, compositionY)
    }

    fun onHitDown() {
        if (stopped) return
        queue(Pending.Dance)
    }

    fun release() {
        cancelScheduled()
        onFallFinished = null
        segmentRunning = false
        listeners.clear()
    }

    private fun queue(action: Pending): Boolean {
        if (pending != null || !state.startsWith("idle")) return false
        pending = action
        if (segmentRunning) {
            publish(visible = true, speed = 3f)
        } else {
            startPending()
        }
        return true
    }

    private fun startPending() {
        when (pending) {
            Pending.Dance -> play("show", SantaAsset.Dance)
            Pending.Fall -> play("fall")
            null -> return
        }
        pending = null
    }

    private fun play(
        name: String,
        asset: SantaAsset = SantaAsset.Seat,
        visible: Boolean = frame.visible,
    ) {
        state = name
        this.asset = asset
        segmentRunning = true
        val durationMs = if (name.startsWith("idle") || animationDurationMs <= 0L) 0L else animationDurationMs
        publish(
            marker = name,
            speed = 1f,
            visible = visible,
            durationMs = durationMs,
            asset = asset,
            restart = true,
            interactive = name.startsWith("idle"),
        )
    }

    private fun publish(
        marker: String = frame.marker,
        speed: Float = frame.speed,
        visible: Boolean = frame.visible,
        durationMs: Long = frame.durationMs,
        asset: SantaAsset = this.asset,
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
            durationMs = durationMs,
            asset = asset,
        )
        this.asset = asset
        listeners.toList().forEach { it(frame) }
    }
}
