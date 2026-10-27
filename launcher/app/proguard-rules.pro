# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# WebHomeActivity's JS bridge is called by name from assets/home.html.
-keepclassmembers class com.gathin.tvhome.WebHomeActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}

# PlayerActivity's JS bridge is called by name from assets/player.html.
-keepclassmembers class com.gathin.tvhome.PlayerActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
