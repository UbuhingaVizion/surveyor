# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/android/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keep class io.realm.annotations.RealmModule
-keep @io.realm.annotations.RealmModule class *
-dontwarn javax.**
-dontwarn io.realm.**

# avoid crashes for 4.2.2 TECNO devices
-keep class !android.support.v7.internal.view.menu.**,android.support.** {*;}

# ---------------------------------------------------------------------------
# Surveyor / R8 keeps
# ---------------------------------------------------------------------------

# keep annotations so Gson can read @SerializedName
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Gson TypeToken relies on the generic superclass signature (e.g. new TypeToken<List<Org>>() {})
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keepclassmembers class * extends com.google.gson.reflect.TypeToken { <init>(); }
-keep class com.google.gson.stream.** { *; }
-dontwarn com.google.gson.**

# Gson (reflection based) model classes
-keep class io.rapidpro.surveyor.data.** { *; }
-keep class io.rapidpro.surveyor.net.requests.** { *; }
-keep class io.rapidpro.surveyor.net.responses.** { *; }
-keep class io.rapidpro.surveyor.utils.RawJson { *; }
-keep class io.rapidpro.surveyor.utils.RawJson$Adapter { *; }
-keep class io.rapidpro.surveyor.utils.JsonUtils { *; }

# goflow mobile bindings (gobind/JNI)
-keep class com.nyaruka.goflow.mobile.** { *; }
-dontwarn com.nyaruka.goflow.mobile.**

# Retrofit API interface
-keep interface io.rapidpro.surveyor.net.TembaAPI { *; }
-keep,allowobfuscation interface io.rapidpro.surveyor.net.TembaAPI
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# WorkManager instantiates workers reflectively
-keep class io.rapidpro.surveyor.work.** { *; }

# WorkManager reflectively instantiates input mergers
-keep class androidx.work.OverwritingInputMerger { *; }
-keep class * extends androidx.work.InputMerger { *; }

# Menu item / view onClick handlers referenced from XML are resolved by reflection
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.MenuItem);
    public void *(android.view.View);
}
-keepclassmembers class * extends android.content.Context {
    public void *(android.view.View);
}

# Room generates a <Database>_Impl class looked up by reflection (WorkManager uses Room)
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.**
-dontwarn androidx.work.**