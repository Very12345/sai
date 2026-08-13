# PhoneAgent 1.1.0 test build

This test build focuses on the Android portions of the repair and expansion
plan: compact input fields, the combined send/voice control, attachment source
selection, ZIP project and extension imports, a functional extension center,
multiple provider profiles and models, stable harness contracts, structured
context compression, Steer input, Agent browser tools, local streaming speech,
voice-call `speak`, and the end-to-end Agent diagnostic.

## Validation

- `:app:assembleDebug` completed successfully with Gradle caches under
  `D:\Code`.
- Debug unit-test tasks for app, agent, provider, runtime and extensions passed.
- The ARM64 APK was signed with the same test key as 1.0.3, installed with
  `adb install -r`, and reported version `1.1.0 (11000)` on the connected vivo
  device.
- Startup showed no fatal exception or Room migration failure. Existing app
  data was preserved.

## Developer preview boundary

Superseded by 1.1.1 for desktop connectivity. The 1.1.0 build did not contain the
encrypted WSS transport or Android scanner/client.
