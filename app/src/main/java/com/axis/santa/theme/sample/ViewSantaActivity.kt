package com.axis.santa.theme.sample

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import com.axis.santa.theme.SantaController

class ViewSantaActivity : Activity() {
    private var santaController: SantaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val button = findViewById<Button>(R.id.start)
        santaController = SantaController(
            santa = findViewById(R.id.santa),
            button = button,
            overlay = findViewById(R.id.santaOverlay),
        )
        button.setOnClickListener { santaController?.onStartPressed() }
    }

    override fun onDestroy() {
        santaController?.release()
        santaController = null
        super.onDestroy()
    }
}
