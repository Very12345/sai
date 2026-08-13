-keepattributes *Annotation*
-keep class com.phoneagent.** { *; }

# Apache Commons Compress probes its optional Zstd adapter reflectively. sai's
# runtime archives are XZ/Tar only, so zstd-jni is intentionally not packaged.
-dontwarn com.github.luben.zstd.ZstdInputStream
