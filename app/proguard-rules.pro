# ONNX Runtime's JNI layer (libonnxruntime4j_jni.so) resolves Java classes,
# fields, and helper methods by exact name from native code (FindClass,
# GetFieldID, GetMethodID) and constructs info objects (NodeInfo, MapInfo,
# TensorInfo) from C. The AAR's bundled consumer rules only cover the
# telemetry classes, so the whole API surface must be kept.
-keep class ai.onnxruntime.** { *; }
