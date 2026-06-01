# 解决串口库报错
-dontwarn com.fazecast.jSerialComm.android.**
-keep class com.fazecast.jSerialComm.** { *; }

# 解决配置库报错
-dontwarn com.typesafe.config.**
-keep class com.typesafe.config.** { *; }

# 解决 UI 引擎和 JVM 内部调用报警
-dontwarn org.jetbrains.skiko.**
-dontwarn sun.misc.**

# 协程
-dontwarn kotlinx.coroutines.internal.MainDispatcherLoader

# Kotlin
-dontwarn kotlin.**

# Compose
-dontwarn androidx.compose.**
-dontwarn androidx.annotation.**

# JDK
-dontwarn java.lang.**
-dontwarn jdk.internal.**

# Metadata
-keep class kotlin.Metadata { *; }
