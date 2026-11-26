package com.axis.santa.theme.sample.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import com.axis.santa.theme.compose.SantaOverlay
import com.axis.santa.theme.compose.rememberSantaPlayer
import com.axis.santa.theme.core.SantaAnchor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val player = rememberSantaPlayer()
                var overlayOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                var anchor by remember { mutableStateOf(SantaAnchor(0f, 0f, 0f, 0f)) }
                Box(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { overlayOrigin = it.positionInWindow() }
                ) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(280.dp, 72.dp)
                            .background(Color(0xFFFF7A00))
                            .onGloballyPositioned { coords ->
                                val position = coords.positionInWindow()
                                anchor = SantaAnchor(
                                    leftPx = position.x - overlayOrigin.x,
                                    topPx = position.y - overlayOrigin.y,
                                    widthPx = coords.size.width.toFloat(),
                                    heightPx = coords.size.height.toFloat(),
                                )
                            }
                            .clickable { player.onStartPressed() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Розпочати")
                    }
                    SantaOverlay(
                        player = player,
                        anchorInOverlay = anchor,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
