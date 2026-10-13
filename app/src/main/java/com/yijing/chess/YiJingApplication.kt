package com.yijing.chess

import android.app.Application
import com.yijing.chess.storage.SessionRepository
import com.yijing.chess.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class YiJingApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repository by lazy { SessionRepository(this) }
    val updates by lazy { UpdateManager(this) }
}
