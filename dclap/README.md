# DCLAP v1 reference gate

This folder is the Phase 0 desktop parity workspace for Wavv. It is intentionally separate from the Android app until the audio artifact and preprocessing contract are reproducible.

## Setup

```powershell
cd C:\Users\Bristo\Wavv\dclap
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

The model files belong in `models\v1` and must remain side-by-side:

- `model_epoch_36.onnx`
- `model_epoch_36.onnx.data`

They are release artifacts and are intentionally ignored by this folder's `.gitignore`.

## Run the gate

```powershell
.\.venv\Scripts\python.exe dclap_reference.py verify
.\.venv\Scripts\python.exe dclap_reference.py fixtures
```

`verify` checks the artifact sizes/hashes and prints the ONNX input/output contract. `fixtures` can regenerate the temporary WAV inputs and writes `fixtures\reference.json` with mel checksums, segment embeddings, the averaged embedding, and the final normalized 512-D vector. The generated WAVs and downloaded GTZAN audio are not kept in the cleaned workspace; the dataset manifest remains.

The preprocessing follows the [DCLAP v1 README](https://raw.githubusercontent.com/NeptuneHub/AudioMuse-AI-DCLAP/main/README.md#L64-L114): 48 kHz mono, int16 round-trip, 10-second windows with 50% overlap, 128-bin Hann log-mel, and per-song average plus L2 normalization.

The paired CLAP text model is staged as `models\v1\clap_text_model.onnx`; the candidate Android artifact is generated as `models\v1\clap_text_model.int8.dynamic.per_channel.onnx`. The tokenizer files in `models\v1\tokenizer` are the matching byte-level Roberta vocabulary and merges.

## Selected model configuration

The selected production pair is FP32 audio + dynamic per-channel INT8 text:

```powershell
.\.venv\Scripts\python.exe dclap_evaluation.py self-test
.\.venv\Scripts\python.exe dclap_evaluation.py quantize-text
.\.venv\Scripts\python.exe dclap_evaluation.py prepare-dataset --samples-per-genre 100
```

The workstation comparison evidence remains in [`evaluations\20260921T-workstation-fp16-audio-int8-text`](evaluations/20260921T-workstation-fp16-audio-int8-text). Candidate FP16-audio and INT8-audio binaries were removed after comparison; their measured results remain in the stored reports.

The chosen pairing is FP32 audio + INT8 text because it retained the strongest retrieval result among the converted configurations while reducing text-model memory and latency.

## Standalone Android model benchmark

The application-independent benchmark lives in `dclap-benchmark`; it does not depend on Wavv classes or UI. The checked-in device report is retained; raw fixture binaries were removed after the run. To reproduce it, download the dataset again with `prepare-dataset`, prepare the small 30-track/30-query fixture, build the benchmark APKs, then run the selected INT8-text configuration:

```powershell
.\.venv\Scripts\python.exe prepare_device_fixture.py --samples-per-genre 3 --output evaluations\android-device-small
cd ..
.\gradlew.bat :dclap-benchmark:assembleDebug :dclap-benchmark:assembleDebugAndroidTest
adb shell am instrument -w -r -e dclap.root /data/local/tmp/wavv-dclap-20260921 -e class com.wavv.dclap.benchmark.DclapModelBenchmarkTest#runInt8AudioInt8Text com.wavv.dclap.benchmark.test/androidx.test.runner.AndroidJUnitRunner
```

The connected I2018 device exposed ARM `asimd`/NEON and `asimddp`/DotProd. The benchmark used ONNX Runtime's CPU execution provider with 8 intra-op threads, CPU arena, and graph optimizations. XNNPACK was available but crashed during DCLAP session creation on this device, so it was not used. The stored result is `evaluations\android-device-small\device-report.json`.
