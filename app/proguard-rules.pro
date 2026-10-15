# Shizuku instantiates the user service by class name in its own process.
-keep class de.emil.shizubox.shizuku.ShellService { <init>(...); }

# youtubedl-android maps yt-dlp's JSON with Jackson reflection.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# NewPipe Extractor: Rhino evaluates YouTube's player JS and uses reflection.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.jsoup.**
-dontwarn com.google.re2j.**
