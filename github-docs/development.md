# Development and testing

Use the root [README](../README.md) for Windows prerequisites, Git LFS setup, and the current Gradle commands. It is the single source for those commands so they do not drift between guides.

The project has three useful checks:

- `:app:assembleDebug` verifies that the debug app compiles and packages.
- `:app:testDebugUnitTest` runs local JVM tests.
- `:app:connectedDebugAndroidTest` runs Android instrumentation tests on a connected device or emulator.

A successful JVM test run does not verify Android permissions, Room behavior on a device, audio routing, codec support, model performance, or visual parity. Report those only after the matching runtime or device check.
