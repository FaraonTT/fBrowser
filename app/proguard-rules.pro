# fBrowser R8 rules. The default optimize rules from AGP are applied first.
# No reflection and no JavaScript interfaces are used, so nothing special must be kept.

# Keep readable stack traces for crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
