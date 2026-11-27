package com.axis.santa.theme

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.axis.santa.theme.core.R
import com.axis.santa.theme.core.SantaAsset
import com.axis.santa.theme.core.SantaAudio
import com.axis.santa.theme.core.SantaFrame
import com.axis.santa.theme.core.SantaPlayer

class SantaController(
    private val santa: LottieAnimationView,
    private val button: View,
    private val overlay: FrameLayout,
    size: Float = 0.5f,
    animationDurationMs: Long = 0L,
    returnDelayMs: Long = 2500,
) {
    private val main = Handler(Looper.getMainLooper())
    private var ignoreEnd = false
    private var playId = 0
    private var cancelId = 0
    private var shownAsset: SantaAsset? = null
    private var lastFrame: SantaFrame? = null
    private var seatComposition: LottieComposition? = null
    private var danceComposition: LottieComposition? = null
    private val audio = SantaAudio(santa.context)
    private val player = SantaPlayer(
        size = size,
        animationDurationMs = animationDurationMs,
        returnDelayMs = returnDelayMs,
        schedule = { delayMs, block -> main.postDelayed(block, delayMs) },
        cancelScheduled = { main.removeCallbacksAndMessages(null) },
    )
    private val animatorListener = object : AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: Animator) {
            val skip = ignoreEnd
            main.post {
                if (skip) return@post
                syncMarkers()
                player.onSegmentEnded()
            }
        }
    }
    private val onFrame: (SantaFrame) -> Unit = onFrame@{ frame ->
        lastFrame = frame
        overlay.isVisible = frame.visible
        val composition = compositionFor(frame.asset) ?: return@onFrame
        if (frame.asset != shownAsset) {
            ignoreEnd = true
            santa.setComposition(composition)
            ignoreEnd = false
            shownAsset = frame.asset
            place()
        }
        santa.speed = speedFor(frame)
        if (frame.cancelId != cancelId) {
            cancelId = frame.cancelId
            ignoreEnd = true
            santa.cancelAnimation()
            audio.stop()
            ignoreEnd = false
        }
        if (frame.playId != playId) {
            playId = frame.playId
            syncMarkers()
            santa.setMinAndMaxFrame(frame.marker)
            santa.playAnimation()
            santa.speed = speedFor(frame)
            audio.onMarker(frame.marker)
        }
    }

    init {
        santa.repeatCount = 0
        santa.addAnimatorListener(animatorListener)
        player.addListener(onFrame)
        installTouch()
        load(R.raw.anim_santa) { composition ->
            seatComposition = composition
            lastFrame?.let(onFrame)
        }
        load(R.raw.anim_santa_dance) { composition ->
            danceComposition = composition
            lastFrame?.let(onFrame)
        }
        button.post {
            syncMarkers()
            place()
            player.onAttached()
        }
        button.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
    }

    fun setSize(size: Float) {
        player.setSize(size)
        place()
    }

    fun setAnimationDuration(durationMs: Long) {
        player.setAnimationDuration(durationMs)
    }

    fun stop() {
        ignoreEnd = true
        player.stop()
        ignoreEnd = false
    }

    fun onStartPressed(onFinished: (() -> Unit)? = null): Boolean {
        syncMarkers()
        return player.onStartPressed(onFinished)
    }

    fun setScreenActive(active: Boolean) {
        syncMarkers()
        if (active) place()
        player.setScreenActive(active)
    }

    fun release() {
        santa.removeAnimatorListener(animatorListener)
        player.removeListener(onFrame)
        ignoreEnd = true
        player.release()
        audio.stop()
        santa.cancelAnimation()
    }

    private fun place() {
        if (button.width == 0 || button.height == 0) return
        val buttonOnScreen = IntArray(2).also(button::getLocationInWindow)
        val overlayOnScreen = IntArray(2).also(overlay::getLocationInWindow)
        val left = (buttonOnScreen[0] - overlayOnScreen[0]).toFloat()
        val top = (buttonOnScreen[1] - overlayOnScreen[1]).toFloat()
        val placement = player.place(
            buttonLeft = left,
            buttonTop = top,
            buttonWidth = button.width.toFloat(),
            buttonHeight = button.height.toFloat(),
        ) ?: return
        santa.layoutParams = FrameLayout.LayoutParams(placement.widthPx.toInt(), placement.heightPx.toInt())
        santa.x = placement.x
        santa.y = placement.y
    }

    private fun load(rawRes: Int, onLoaded: (LottieComposition) -> Unit) {
        LottieCompositionFactory.fromRawRes(santa.context, rawRes)
            .addFailureListener { }
            .addListener { composition -> main.post { onLoaded(composition) } }
    }

    private fun compositionFor(asset: SantaAsset): LottieComposition? {
        return if (asset == SantaAsset.Dance) danceComposition else seatComposition
    }

    private fun speedFor(frame: SantaFrame): Float {
        if (frame.durationMs <= 0L) return frame.speed
        val composition = santa.composition ?: return frame.speed
        val marker = composition.getMarker(frame.marker) ?: return frame.speed
        val nativeMs = marker.durationFrames / composition.frameRate * 1000f
        if (nativeMs <= 0f) return frame.speed
        return nativeMs / frame.durationMs
    }

    private fun syncMarkers() {
        val composition = seatComposition ?: return
        player.markers(
            listOf("idle", "idle_blink", "idle_wave", "fall", "show", "return")
                .filter { composition.getMarker(it) != null }
                .toSet()
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installTouch() {
        overlay.isClickable = false
        santa.isClickable = false
        overlay.setOnTouchListener { _, event ->
            if (!player.isHit(event.x, event.y, santa.x, santa.y)) return@setOnTouchListener false
            if (event.action == MotionEvent.ACTION_DOWN) player.onHitDown()
            true
        }
    }
}
