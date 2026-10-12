package com.yijing.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.yijing.chess.model.GameMode
import com.yijing.chess.model.GameType
import com.yijing.chess.ui.screens.ChessScreen
import com.yijing.chess.ui.screens.GoScreen
import com.yijing.chess.ui.screens.HomeScreen
import com.yijing.chess.ui.screens.XiangqiScreen
import com.yijing.chess.ui.theme.YiJingTheme

sealed class AppDestination {
    object Home : AppDestination()
    data class GameArena(val gameType: GameType, val mode: GameMode) : AppDestination()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YiJingTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentDestination by remember { mutableStateOf<AppDestination>(AppDestination.Home) }

                    when (val dest = currentDestination) {
                        is AppDestination.Home -> {
                            HomeScreen(
                                onNavigateToGame = { type, mode ->
                                    currentDestination = AppDestination.GameArena(type, mode)
                                }
                            )
                        }
                        is AppDestination.GameArena -> {
                            BackHandler {
                                currentDestination = AppDestination.Home
                            }

                            when (dest.gameType) {
                                GameType.XIANGQI -> {
                                    XiangqiScreen(
                                        initialMode = dest.mode,
                                        onBack = { currentDestination = AppDestination.Home }
                                    )
                                }
                                GameType.GO -> {
                                    GoScreen(
                                        initialMode = dest.mode,
                                        onBack = { currentDestination = AppDestination.Home }
                                    )
                                }
                                GameType.CHESS -> {
                                    ChessScreen(
                                        initialMode = dest.mode,
                                        onBack = { currentDestination = AppDestination.Home }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
