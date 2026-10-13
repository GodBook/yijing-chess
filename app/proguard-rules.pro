# YiJing Proguard rules

# Persisted names are part of the on-device save format, including older SharedPreferences saves.
-keep class com.yijing.chess.session.** { *; }
-keep class com.yijing.chess.model.** { *; }
-keep class com.yijing.chess.storage.SavedGameState { *; }
-keep class com.yijing.chess.storage.AppPreferences { *; }
-keep class com.yijing.chess.storage.ThemeChoice { *; }
-keep class com.yijing.chess.update.UpdateInfo { *; }
