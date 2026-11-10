# Keep Room entities
-keep class com.miku.player.data.model.** { *; }

# Media3 ya publica sus propias reglas de consumidor (se quitó el keep global,
# que impedía reducir y ofuscar toda la librería).

# JAudioTagger: usa reflexión para instanciar lectores/escritores de etiquetas por
# formato; sin esto R8 puede romper la edición de títulos/carátulas en builds release.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
# Clases de escritorio que la librería referencia pero Android no incluye:
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

# Keep Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
