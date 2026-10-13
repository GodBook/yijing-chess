package com.yijing.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.yijing.chess.ui.YiJingApp

class MainActivity : ComponentActivity() {
    lateinit var model: GameViewModel
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = ViewModelProvider(this)[GameViewModel::class.java]
        setContent { YiJingApp(model) }
    }

    override fun onStop() {
        model.persistCurrent()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        (application as YiJingApplication).updates.onResume()
    }
}
