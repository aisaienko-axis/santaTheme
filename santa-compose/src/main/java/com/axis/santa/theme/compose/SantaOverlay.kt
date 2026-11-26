package com.axis.santa.theme.compose

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.LottieComposition
import com.axis.santa.theme.core.R
import com.axis.santa.theme.core.SantaAnchor
import com.axis.santa.theme.core.SantaAsset
import com.axis.santa.theme.core.SantaFrame
import com.axis.santa.theme.core.SantaPlayer
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun rememberSantaPlayer(
    size: Float = 0.5f,
    animationDurationMs: Long = 0L,
    returnDelayMs: Long = 2500,
): SantaPlayer {
    val scope = rememberCoroutineScope()
    val player = remember {
        var delayJob: Job? = null
        SantaPlayer(
            size = size,
            animationDurationMs = animationDurationMs,
            returnDelayMs = returnDelayMs,
            schedule = { delayMs, block ->
                delayJob?.cancel()
                delayJob = scope.launch {
                    delay(delayMs)
                    block()
                }
            },
            cancelScheduled = { delayJob?.cancel() },
        )
    }
    player.setSize(size)
    player.setAnimationDuration(animationDurationMs)
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

@Composable
fun SantaOverlay(
    player: SantaPlayer,
    anchorInOverlay: SantaAnchor,
    modifier: Modifier = Modifier,
    size: Float = player.size,
    animationDurationMs: Long = player.animationDurationMs,
) {
    player.setSize(size)
    player.setAnimationDuration(animationDurationMs)
    val seatComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.anim_santa))
    val danceComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.anim_santa_dance))
    var frame by remember { mutableStateOf(player.frame) }
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val playback = remember(player) { Playback() }
    val compositions = remember(player) { CompositionHolder() }
    compositions.seat = seatComposition
    compositions.dance = danceComposition
    val shown = if (frame.asset == SantaAsset.Dance) danceComposition else seatComposition
    val placement = player.place(
        buttonLeft = anchorInOverlay.leftPx,
        buttonTop = anchorInOverlay.topPx,
        buttonWidth = anchorInOverlay.widthPx,
        buttonHeight = anchorInOverlay.heightPx,
    )

    val listener = remember(player) {
        { next: SantaFrame ->
            frame = next
            val loaded = if (next.asset == SantaAsset.Dance) compositions.dance else compositions.seat
            if (next.cancelId != playback.cancelId) {
                playback.cancelId = next.cancelId
                playback.job?.cancel()
                playback.job = null
            }
            if (next.playId != playback.playId && next.playId != 0) {
                if (loaded != null) {
                    playback.playId = next.playId
                    playback.job?.cancel()
                    val marker = loaded.getMarker(next.marker)
                    if (marker != null && loaded.durationFrames > 0f) {
                        progress = marker.startFrame / loaded.durationFrames
                    }
                    playback.job = scope.launch {
                        runSegment(loaded, next.marker, next.durationMs, player) { progress = it }
                        if (isActive) player.onSegmentEnded()
                    }
                }
            }
        }
    }

    DisposableEffect(player) {
        player.addListener(listener)
        listener(player.frame)
        onDispose {
            player.removeListener(listener)
            playback.job?.cancel()
        }
    }

    LaunchedEffect(seatComposition, danceComposition) {
        listener(player.frame)
    }

    LaunchedEffect(seatComposition) {
        val loaded = seatComposition ?: return@LaunchedEffect
        player.markers(loaded.markers.map { it.name }.toSet())
        if (!playback.attached) {
            playback.attached = true
            player.onAttached()
        }
    }

    if (placement == null || !frame.visible || shown == null) return
    val density = LocalDensity.current
    Box(modifier) {
        LottieAnimation(
            composition = shown,
            progress = { progress },
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .offset { IntOffset(placement.x.roundToInt(), placement.y.roundToInt()) }
                .size(
                    with(density) { placement.widthPx.toDp() },
                    with(density) { placement.heightPx.toDp() },
                ),
        )
        if (frame.interactive) {
            val hit = player.scenario.hit
            val hitLeft = placement.x + hit.left * placement.scale
            val hitTop = placement.y + hit.top * placement.scale
            val hitWidth = (hit.right - hit.left) * placement.scale
            val hitHeight = (hit.bottom - hit.top) * placement.scale
            Box(
                Modifier
                    .offset { IntOffset(hitLeft.roundToInt(), hitTop.roundToInt()) }
                    .size(
                        with(density) { hitWidth.toDp() },
                        with(density) { hitHeight.toDp() },
                    )
                    .pointerInput(player) {
                        detectTapGestures { player.onHitDown() }
                    }
            )
        }
    }
}

private class CompositionHolder {
    var seat: LottieComposition? = null
    var dance: LottieComposition? = null
}

private class Playback {
    var playId = 0
    var cancelId = 0
    var attached = false
    var job: Job? = null
}

private suspend fun runSegment(
    composition: com.airbnb.lottie.LottieComposition,
    markerName: String,
    durationMs: Long,
    player: SantaPlayer,
    onProgress: (Float) -> Unit,
) {
    val marker = composition.getMarker(markerName) ?: return
    val duration = composition.durationFrames
    if (duration <= 0f) return
    val end = marker.startFrame + marker.durationFrames
    val nativeMs = marker.durationFrames / composition.frameRate * 1000f
    val fixedSpeed = if (durationMs > 0L && nativeMs > 0f) nativeMs / durationMs else 0f
    var current = marker.startFrame
    var lastNanos = 0L
    onProgress(current / duration)
    while (current < end && coroutineContext.isActive) {
        withFrameNanos { now ->
            if (lastNanos == 0L) {
                lastNanos = now
                return@withFrameNanos
            }
            val dt = (now - lastNanos) / 1_000_000_000f
            lastNanos = now
            val speed = if (fixedSpeed > 0f) fixedSpeed else player.frame.speed.coerceAtLeast(0f)
            current = (current + speed * composition.frameRate * dt).coerceAtMost(end)
            onProgress(current / duration)
        }
    }
}
