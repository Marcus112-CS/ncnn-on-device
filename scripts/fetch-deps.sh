#!/usr/bin/env bash
set -euo pipefail

# 下载并解压 ncnn + opencv-mobile 到 app/src/main/jni
# 版本号与 .github/workflows/release-apk.yml 保持一致
NCNN_VERSION=20260526
OPENCV_VERSION=4.13.0
OPENCV_MOBILE_TAG=v35

JNI_DIR="app/src/main/jni"

# 必须在仓库根目录运行
if [ ! -d "$JNI_DIR" ]; then
  echo "✗ 找不到 $JNI_DIR ，请在仓库根目录执行此脚本" >&2
  exit 1
fi

# ---- ncnn ----
NCNN_ZIP="ncnn-${NCNN_VERSION}-android-vulkan.zip"
if [ ! -d "$JNI_DIR/ncnn-${NCNN_VERSION}-android-vulkan" ]; then
  echo "==> 下载 ncnn ${NCNN_VERSION}"
  curl -L -o "$NCNN_ZIP" \
    "https://github.com/Tencent/ncnn/releases/download/${NCNN_VERSION}/ncnn-${NCNN_VERSION}-android-vulkan.zip"
  echo "==> 解压 ncnn 到 $JNI_DIR"
  unzip -q "$NCNN_ZIP" -d "$JNI_DIR"
  rm -f "$NCNN_ZIP"
else
  echo "==> ncnn 已存在，跳过"
fi

# ---- opencv-mobile ----
OPENCV_ZIP="opencv-mobile-${OPENCV_VERSION}-android.zip"
if [ ! -d "$JNI_DIR/opencv-mobile-${OPENCV_VERSION}-android" ]; then
  echo "==> 下载 opencv-mobile ${OPENCV_VERSION} (${OPENCV_MOBILE_TAG})"
  curl -L -o "$OPENCV_ZIP" \
    "https://github.com/nihui/opencv-mobile/releases/download/${OPENCV_MOBILE_TAG}/opencv-mobile-${OPENCV_VERSION}-android.zip"
  echo "==> 解压 opencv-mobile 到 $JNI_DIR"
  unzip -q "$OPENCV_ZIP" -d "$JNI_DIR"
  rm -f "$OPENCV_ZIP"
else
  echo "==> opencv-mobile 已存在，跳过"
fi

echo ""
echo "✓ 完成。校验目录："
ls -d "$JNI_DIR/ncnn-${NCNN_VERSION}-android-vulkan" "$JNI_DIR/opencv-mobile-${OPENCV_VERSION}-android"
echo ""
echo "下一步：Android Studio 里 Sync + build；CMakeLists 路径已匹配，无需改动。"
