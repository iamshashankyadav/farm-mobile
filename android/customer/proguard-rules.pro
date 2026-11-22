# R8 rules for the release build. kotlinx.serialization, OkHttp, Coil and Play services ship
# their own consumer rules; these cover what is reached by reflection or only from the manifest.

# @Serializable models: keep the generated serializers and companions.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class com.farm.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.farm.**$$serializer { *; }
-keepclasseswithmembers class com.farm.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# osmdroid loads tile providers by name.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# OkHttp optional platform classes.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Keep line numbers so crash reports from a release APK can be read.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
