# Taskie ProGuard rules
-keep class com.taskie.app.data.model.** { *; }
-keep class com.taskie.app.notification.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
