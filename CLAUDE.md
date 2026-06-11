# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A real-time, on-device Android camera app running YOLOv8 inference via ncnn + Vulkan.
Forked from `nihui/ncnn-android-yolov8`. The camera feed is processed frame-by-frame in
native C++ (NDK camera → ncnn inference → OpenCV draw) and rendered back to a `SurfaceView`.
There is no networking; all models ship as APK assets.

## Build prerequisites (REQUIRED before any build will succeed)

Three native dependencies are **not vendored** in the repo. Versions are pinned in
`app/src/main/jni/CMakeLists.txt` — match them or edit the paths there.

**Fastest path:** run `scripts/fetch-deps.sh` from the repo root. It downloads and extracts
ncnn + opencv-mobile into `app/src/main/jni/` at the versions CMakeLists expects (skips dirs
that already exist). It does **not** fetch the turnip driver — that stays manual (step 3).

The three dependencies:

1. **ncnn** → extract `ncnn-YYYYMMDD-android-vulkan.zip` into `app/src/main/jni/`.
   CMake currently expects `app/src/main/jni/ncnn-20260526-android-vulkan/`.
2. **opencv-mobile** → extract into `app/src/main/jni/`.
   CMake currently expects `app/src/main/jni/opencv-mobile-4.13.0-android/`.
3. **mesa turnip driver** → place `libvulkan_freedreno.so` into
   `app/src/main/jniLibs/arm64-v8a/` (create the dir; it is empty/absent by default).
   Only needed for the "GPU (turnip)" runtime option; the app builds without it but that
   code path will fail to load the driver.

If a build complains about `find_package(ncnn)` / `find_package(OpenCV)`, the cause is almost
always a missing or version-mismatched directory above — not a code bug.

The dependency versions live in **three places that must stay in sync**: `CMakeLists.txt`
(the path strings), `scripts/fetch-deps.sh` (the `*_VERSION` vars), and
`.github/workflows/release-apk.yml` (the `env:` block). Bumping a version means editing all three.

## Build & run

```sh
./gradlew assembleDebug          # build APK
./gradlew installDebug           # build + install to connected device
```

- Real device required: uses the NDK Camera2 (HAL3) API and Vulkan. Will not work on most emulators.
- Toolchain (in `app/build.gradle`): AGP 8.7.3, compileSdk 33, targetSdk 35, minSdk 24,
  NDK 29.0.14206865, CMake 3.31.5. `arm64-v8a` is the only meaningful ABI (turnip is arm64-only).
- There are **no unit/instrumentation tests** in this project. "Testing" means running on a device
  and observing the on-screen detections + FPS overlay.

### Release CI
`.github/workflows/release-apk.yml` is **manual-only** (`workflow_dispatch`). It fetches all three
deps (including turnip), rewrites the version strings in `CMakeLists.txt` via `sed`, runs
`./gradlew assembleRelease`, then self-signs with a throwaway keystore generated in-job. There is no
CI on push/PR — nothing checks a normal commit, so verify builds locally.

## Architecture

### Java ↔ native boundary
`YOLOv8Ncnn.java` declares 4 native methods (`loadModel`, `openCamera`, `closeCamera`,
`setOutputWindow`) implemented in `app/src/main/jni/yolov8ncnn.cpp`. This JNI file is the
control center — it owns the global `g_yolov8` model instance and `g_camera`, both guarded
by a single `ncnn::Mutex lock`.

`MainActivity.java` is a thin UI: three spinners (Task / Model / CPU-GPU) whose selected
positions are passed straight through `loadModel(assets, taskid, modelid, cpugpu)`. Any spinner
change calls `reload()`. The Surface lifecycle drives `setOutputWindow` / `openCamera` / `closeCamera`.

### The loadModel index contract (read this before touching model loading)
The integer args from the spinners map to asset filenames and behavior entirely inside
`Java_..._loadModel` in `yolov8ncnn.cpp`. Getting these mappings wrong silently loads the
wrong model:
- `taskid` 0–5 → suffix `{"", "_oiv7", "_seg", "_pose", "_cls", "_obb"}` and concrete subclass.
- `modelid` 0–8 → size letter `{n,s,m}` repeated 3×; `modelid % 3` picks the size, while
  `modelid >= 3` / `>= 6` bumps `det_target_size` to 480 / 640 (the higher bands re-run the
  same n/s/m weights at a larger input resolution).
- `cpugpu` 0/1/2 → CPU / Vulkan GPU / turnip. `cpugpu==2` calls
  `create_gpu_instance("libvulkan_freedreno.so")`; the GPU instance is destroyed and recreated
  on every reload.
- Asset names are built as `yolov8<size><tasksuffix>.ncnn.{param,bin}` and loaded from APK assets.

### Inference task hierarchy
`yolov8.h` defines an abstract `YOLOv8` base (holds `ncnn::Net`, `det_target_size`) with
`detect()` + `draw()` virtuals. One `.cpp` per task implements them:
`yolov8_det.cpp` (COCO + OIV7 draw variants), `yolov8_seg.cpp`, `yolov8_pose.cpp`,
`yolov8_cls.cpp`, `yolov8_obb.cpp`. `yolov8.cpp` holds the shared base/loader. Post-processing
(NMS, box decode, mask/keypoint/obb handling) lives in each task file — the exported ncnn models
deliberately have post-processing stripped out (see README "guidelines for converting").

### Camera pipeline
`ndkcamera.cpp` / `.h` wrap Camera2 NDK. `NdkCameraWindow::on_image_render(cv::Mat& rgb)` is the
per-frame hook; `MyNdkCamera` (in `yolov8ncnn.cpp`) overrides it to run
`g_yolov8->detect()` → `draw()` under the lock, then overlays a 10-frame moving-average FPS.
When no model is loaded it draws "unsupported".

## Models
~30 prebuilt `.ncnn.param`/`.ncnn.bin` files live in `app/src/main/assets/` (n/s/m sizes across
det/seg/pose/cls/obb/oiv7), totaling several hundred MB and bundled into the APK. All are modified
to accept dynamic input shapes. To add/replace a model, drop the asset files and extend the
suffix/size tables in `loadModel` — keep the filename convention exact.

## Conventions
- Package is `com.tencent.yolov8ncnn` (upstream Tencent naming); keep it consistent across Java,
  JNI symbol names (`Java_com_tencent_yolov8ncnn_*`), and `namespace` in `app/build.gradle`.
- Native source is added file-by-file in `CMakeLists.txt`'s `add_library(...)` — new `.cpp` files
  must be listed there.
- `JNI_OnLoad`/`JNI_OnUnload` create/destroy the ncnn GPU instance and the camera singleton;
  lifecycle ordering here matters for Vulkan stability.
