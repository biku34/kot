# Ktor + kotlinx.serialization
-keep class io.ktor.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.procmon.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn kotlinx.**
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
