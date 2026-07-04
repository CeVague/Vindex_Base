# ROOM & SQLITE
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-keep class com.cevague.vindex.data.database.**_Impl { *; }
-keepclassmembers class com.cevague.vindex.data.database.entity.** { *; }
-keepclassmembers class com.cevague.vindex.data.database.dao.** { *; }
-keep class androidx.room.paging.** { *; }

# GLIDE
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }

# METADATA EXTRACTOR
-keep class com.drew.** { *; }
-dontwarn com.drew.**

# KOTLIN & PARCELABLE
-keepattributes Signature,AnnotationDefault,EnclosingMethod,InnerClasses,
                SourceFile,LineNumberTable

-keep class com.cevague.vindex.ui.viewer.ViewerSource { *; }
-keep class com.cevague.vindex.ui.viewer.ViewerSource$* { *; }
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}
