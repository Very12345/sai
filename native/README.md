# Native PRoot artifact

Android 10+ does not permit executing arbitrary dynamic binaries copied into the app data directory. PhoneAgent therefore packages the PRoot launcher and its Android dependencies as ABI-specific `lib*.so` APK native libraries and invokes PRoot through `/system/bin/linker64`.

PhoneAgent uses PRoot as a standalone launcher rather than exposing a Termux prefix. `stage-termux-runtime.ps1` downloads pinned official Termux packages, verifies SHA-256, extracts only PRoot and its two dependencies, changes the `libtalloc.so.2` dependency to the APK-safe `libtalloc.so` name, and stages the files as native libraries. Hardcoded loader and temporary paths are not used: `PROOT_LOADER`, `PROOT_TMP_DIR`, and `LD_LIBRARY_PATH` are set explicitly at runtime.

Expected layout:

```text
core/runtime/src/main/jniLibs/arm64-v8a/libproot.so
core/runtime/src/main/jniLibs/arm64-v8a/libproot-loader.so
core/runtime/src/main/jniLibs/arm64-v8a/libtalloc.so
core/runtime/src/main/jniLibs/arm64-v8a/libandroid-shmem.so
core/runtime/src/main/jniLibs/x86_64/libproot.so
...
```

The source tree contains the verified staged ELF files so debug APKs exercise the real runtime. Re-running `stage-termux-runtime.ps1` from an empty cache must reproduce their checksums. The GPL-2.0 Termux PRoot source, package recipes and exact revision are represented by `build-termux-runtime.sh` and the manual `Native PRoot runtime` workflow. A release must publish source, staging/build logs, licenses and checksums alongside the APK.

The `phoneagent_pty` JNI library is built from `core/runtime/src/main/cpp` with the pinned NDK. It uses Android's `forkpty`, supports input, output, resize and signals, and connects the interactive Debian shell to the Compose terminal screen.

ARM64 on-device validation remains mandatory before publishing a release.
