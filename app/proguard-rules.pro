# Jaga info crash tapi tetap minify ukuran
-keepattributes Signature, InnerClasses, EnclosingMethod, Annotation
-keepattributes SourceFile, LineNumberTable

# Jangan keep seluruh package — biarkan R8 shrink+obfuscate kode sendiri.
# Cukup keep entry point yang DIPANGGIL VIA REFLECTION/JNI/manifest saja:

# JNI bridge: ganti dengan package spesifik tempat class native callback-mu berada
-keep,allowobfuscation class app.hikari.nativebridge.** { *; }
-keepclasseswithmembers class * {
    native <methods>;
}

# kotlinx.serialization (cukup yang ini saja, hapus yang allowoptimization)
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.hikari.**$$serializer { *; }
-keepclassmembers class app.hikari.** { *** Companion; }
-keepclasseswithmembers class app.hikari.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Dependency eksternal — pertahankan seperti sekarang (sudah cukup baik)
-keep,allowoptimization class androidx.preference.** { public protected *; }
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
-keep,allowoptimization class okhttp3.** { public protected *; }
-keep,allowoptimization class okio.** { public protected *; }
-keep,allowoptimization class org.jsoup.** { public protected *; }
-keep,allowoptimization class rx.** { public protected *; }
-keep,allowoptimization class app.cash.quickjs.** { public protected *; }
-keep class uy.kohesive.injekt.** { *; }

-dontwarn sun.misc.**
-dontwarn androidx.window.**
-dontwarn com.google.re2j.**

-keep public enum nl.adaptivity.xmlutil.EventType { *; }
-keep class com.google.firebase.installations.** { *; }
-keep interface com.google.firebase.installations.** { *; }