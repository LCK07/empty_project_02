# kotlinx.serialization: keep generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.lck07.lumen.**$$serializer { *; }
-keepclassmembers class com.lck07.lumen.** { *** Companion; }
-keepclasseswithmembers class com.lck07.lumen.** { kotlinx.serialization.KSerializer serializer(...); }

# The FFmpeg extension is loaded by reflection from Media3.
-keep class io.github.anilbeesetti.nextlib.media3ext.** { *; }
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# NanoHTTPD and kxml are referenced reflectively in places.
-dontwarn fi.iki.elonen.**
-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
