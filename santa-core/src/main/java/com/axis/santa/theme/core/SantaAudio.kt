package com.axis.santa.theme.core

import android.content.Context
import android.media.MediaPlayer

class SantaAudio(context: Context) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    fun onMarker(marker: String) {
        val raw = when (marker) {
            "show" -> R.raw.santa_dance
            "fall" -> R.raw.santa_fall
            else -> return
        }
        stop()
        val created = MediaPlayer.create(appContext, raw) ?: return
        player = created
        created.setOnCompletionListener {
            it.release()
            if (player === it) player = null
        }
        created.start()
    }

    fun stop() {
        val current = player ?: return
        player = null
        current.setOnCompletionListener(null)
        current.release()
    }
}
