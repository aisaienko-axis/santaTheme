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
import com.axis.santa.theme.core.SantaFrame
import com.axis.santa.theme.core.SantaPlayer

class SantaController(
    private val santa: LottieAnimationView,
    private val button: View,
    private val overlay: FrameLayout,
    returnDelayMs: Long = 2500,
) {
    private val main = Handler(Looper.getMainLooper())
    private var ignoreEnd = false
    private var playId = 0
    private var cancelId = 0
    private val player = SantaPlayer(
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
    private val onFrame: (SantaFrame) -> Unit = { frame ->
        overlay.isVisible = frame.visible
        santa.speed = frame.speed
        if (frame.cancelId != cancelId) {
            cancelId = frame.cancelId
            ignoreEnd = true
            santa.cancelAnimation()
            ignoreEnd = false
        }
        if (frame.playId != playId) {
            playId = frame.playId
            syncMarkers()
            santa.setMinAndMaxFrame(frame.marker)
            santa.playAnimation()
        }
    }

    init {
        santa.repeatCount = 0
        santa.addAnimatorListener(animatorListener)
        player.addListener(onFrame)
        installTouch()
        button.post {
            syncMarkers()
            place()
            player.onAttached()
        }
        button.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> place() }
    }

    fun onStartPressed() {
        syncMarkers()
        player.onStartPressed()
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

    private fun syncMarkers() {
        val composition = santa.composition ?: return
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
