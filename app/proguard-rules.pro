# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in $ANDROID_HOME/tools/proguard/proguard-android.txt

# Keep source file names and line numbers for better stack traces
-keepattributes SourceFile,LineNumberTable

# Keep custom exceptions
-keep public class * extends java.lang.Exception

# Keep service classes
-keep public class * extends android.app.Service

# Keep activity classes
-keep public class * extends android.app.Activity
