# fBrowser R8 rules. The default optimize rules from AGP are applied first.

# Readable stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# GeckoView is driven from native code (JNI); keep its public API untouched.
-keep class org.mozilla.geckoview.** { *; }
-keep class org.mozilla.gecko.** { *; }
-dontwarn org.mozilla.**
