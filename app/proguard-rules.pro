# ProGuard rules for Cline Agent Mobile (minifyEnabled=false, kept for completeness)
-keep class com.clineagentmobile.model.** { *; }
-keep class com.clineagentmobile.bridge.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
