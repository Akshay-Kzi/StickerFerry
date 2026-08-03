# ProGuard rules for StickerBridge

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class com.stickerbridge.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.stickerbridge.domain.model.**$$serializer {
    *** INSTANCE;
}

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Keep WhatsApp sticker provider
-keep class com.stickerbridge.data.whatsapp.StickerContentProvider { *; }

# General Android
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile