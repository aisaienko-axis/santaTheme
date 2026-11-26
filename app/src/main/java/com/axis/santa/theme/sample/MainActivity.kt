package com.axis.santa.theme.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        findViewById<Button>(R.id.openView).setOnClickListener {
            startActivity(Intent(this, ViewSantaActivity::class.java))
        }
        findViewById<Button>(R.id.openCompose).setOnClickListener {
            startActivity(Intent(this, ComposeSantaActivity::class.java))
        }
    }
}
