# Add project specific ProGuard rules here.
-keepattributes *Annotation*

# --- Gson: reflection on DTO fields ---
-keep class com.tengwear.tengstore.data.remote.dto.** { *; }
-keep class com.tengwear.tengstore.domain.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Retrofit ---
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- Coroutines ---
-dontwarn kotlinx.coroutines.**

# --- JavaMail（应用内 SMTP 发邮件） ---
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.naming.**
-dontwarn org.junit.**
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class gnu.mail.** { *; }
-keep class com.sun.activation.** { *; }
-keep class javax.activation.** { *; }

# --- Coil（图片加载） ---
-dontwarn coil.**
-keep class coil.** { *; }
-keep interface coil.** { *; }
