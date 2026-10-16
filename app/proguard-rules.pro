# AndroidX, Hilt and Compose ship their own consumer rules.
# Components referenced from the manifest (services, receivers) are kept by AAPT2.

# Strip verbose/debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
