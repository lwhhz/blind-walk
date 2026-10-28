# ---------------------------------------------------------------------------
# kotlinx.serialization
# 生成的 serializer 是实现细节，R8 看不到反射调用，必须显式保留。
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**

-keepclassmembers class com.blindwalk.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.blindwalk.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.blindwalk.app.**$$serializer {
    *;
}

# 枚举的序列化依赖 values()/valueOf()
-keepclassmembers enum com.blindwalk.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------------------------------------------------------------------------
# Android / Compose 常规
# ---------------------------------------------------------------------------
# 不保留行号会丢失所有堆栈定位能力，保留但只留必要部分。
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# 前台服务与通知的动作字符串在代码里是常量，不会被反射，无需 keep。

-keep class com.blindwalk.app.BlindWalkApp { *; }
-keep class com.blindwalk.app.MainActivity { *; }
-keep class com.blindwalk.app.location.TripService { *; }
