# 翼智视联

端侧实时目标检测 Android 应用 · 基于 ncnn + Vulkan，逐帧在本机 C++ 中完成 NDK 相机采集 → ncnn 推理 → OpenCV 绘制 → SurfaceView 渲染，**全程不联网**，模型随 APK 打包。

> 中国电信江苏公司 AI 青年特训营 · 12组-1 OPC 大赛作品
> Fork 自 [nihui/ncnn-android-yolov8](https://github.com/nihui/ncnn-android-yolov8)

---

## 功能

### 检测场景（顶部「场景」下拉）

| 场景 | 模型 | 识别类别 |
|------|------|----------|
| **交通** | COCO 检测模型 `yolov8<尺寸>` | person、bicycle、car、motorcycle、bus、traffic light |
| **机场** | 同一份 COCO 权重 | suitcase、laptop、cell phone |
| **姿态** | `yolov8<尺寸>_pose` | person + 关键点骨架 |
| **安监** | 专用安全帽模型 `helmet-detect/yolov8<尺寸>` | helmet（戴帽，绿框）、head（未戴，红框） |

> 交通与机场共用同一份 COCO 权重，只是在推理阶段按各自的**类别白名单**过滤；白名单在检测的 argmax 环节生效——只在白名单类别里取最高分，因此某类只要自身得分过阈值就上报，不会被画面里其它高分类别“吃掉”。

### 模型尺寸（「尺寸」下拉）

- 交通 / 机场 / 姿态：`n / s / m` × `320 / 480 / 640`，共 9 档（同一份权重换输入分辨率，越大越准越慢）。
- 安监：`n / s` × `320 / 480 / 640`，共 6 档（安全帽模型只提供 n、s，无 m），默认 **n-640**。

### 推理后端（「推理后端」下拉）

- **CPU** — ncnn 跑在 CPU。
- **GPU** — Vulkan，走设备厂商自带驱动。
- **turnip** — Vulkan，走 Mesa 开源 turnip 驱动（`libvulkan_freedreno.so`，仅高通 Adreno + arm64；需手动放置驱动，见下）。

### 界面与设置

- 顶部标题栏：电信「天翼」白色 Logo + 「翼智视联」+ 实时 FPS（原生计算，经 JNI 回传，每 0.5s 刷新）。
- 蓝色 Material 风格、与图标同色系；底部为磨砂玻璃控制面板。
- 启动默认**后置摄像头**，按钮可切换前/后置。
- **点击标题栏 Logo 进入「设置」**：
  - **全屏显示**：隐藏底部控制面板（保留标题栏）。
  - **视频比例**：填充 / 16:9 / 4:3（约束预览矩形，居中、上下留黑边）。
  - **云中台地址**：可填 IP 与端口（当前仅本地存储于 SharedPreferences，应用尚无联网逻辑，留作后续上报对接）。
  - **项目说明**（三级页）：人员分工、技术栈、出品信息。

---

## 构建前置（缺一不可）

三个原生依赖**不随仓库提供**，版本在 `app/src/main/jni/CMakeLists.txt` 中固定，需匹配或自行改路径：

1. **ncnn** → `app/src/main/jni/ncnn-20260526-android-vulkan/`
2. **opencv-mobile** → `app/src/main/jni/opencv-mobile-4.13.0-android/`
3. **mesa turnip 驱动**（可选，仅「turnip」后端用）→ 把 `libvulkan_freedreno.so` 放进 `app/src/main/jniLibs/arm64-v8a/`（目录默认不存在，需新建）。不放也能编译，只是 turnip 后端会加载失败。

**快捷方式**：仓库根目录执行 `scripts/fetch-deps.sh` 自动下载并解压 ncnn + opencv-mobile（**不含** turnip 驱动，turnip 仍需手动放）。

> 若构建报 `find_package(ncnn)` / `find_package(OpenCV)` 失败，基本都是上面目录缺失或版本不符，而非代码问题。

### JDK 要求

AGP 8.7.3 需要 **JDK 17+**。若默认是 JDK 11 会报错，请用 17/21 构建，例如：

```sh
JAVA_HOME=/path/to/jdk-21 ./gradlew assembleDebug
```

---

## 构建与运行

- **需真机**：使用 NDK Camera2(HAL3) 与 Vulkan，多数模拟器无法运行；`arm64-v8a` 是唯一有意义的 ABI。
- 工具链：AGP 8.7.3、Gradle 8.12、compileSdk 33、targetSdk 35、minSdk 24、NDK 29.0.14206865、CMake（CMakeLists 要求 ≥3.10）。
- 无单元 / 仪器测试，“测试”=在真机上观察检测框与 FPS。
- 包名 / applicationId：`com.chinatelecom.yizhishilian`。

下面脚本里的 `JAVA_HOME` 换成你本机的 JDK 17+ 路径；macOS + Homebrew 装的 openjdk@21 一般是
`/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`。`apksigner` / `zipalign` 在
`$ANDROID_HOME/build-tools/<版本>/`（本项目用 36.0.0 / 37.0.0 均可）。

### Debug：构建 + 推送到设备

**一键：`./scripts/build-debug.sh`**（自动定位 JDK 17+，构建、安装并启动）。下面是其等价的手动步骤。

调试自测用。debug 包用自带 debug key 签名，可直接安装。

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# 构建并安装到已连接的真机（= assembleDebug + adb install）
./gradlew installDebug

# 启动 App
adb shell am start -n com.chinatelecom.yizhishilian/.MainActivity

# 如需看实时日志（FPS 计算、loadModel、相机）
adb logcat -s ncnn
```

### Release：构建 + 签名 + 测试

**一键：`./scripts/build-release.sh`**（构建 + 生成/复用 keystore + 签名 + 校验，产出 `yizhishilian-release.apk`）。
加 `--install` 会额外卸载 debug 并安装 release 做冒烟测试。可用环境变量覆盖：`KS` / `KEY_ALIAS` / `KS_PASS` / `OUT`。

对外分享用。release 默认**不签名**，必须自签后才能安装。**首次生成 keystore 后务必保存**，
后续所有版本都要用**同一个** keystore 签名，否则用户无法覆盖升级（keystore 已被 `.gitignore` 忽略，切勿提交）。下面是脚本等价的手动步骤。

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
BT=$ANDROID_HOME/build-tools/37.0.0          # zipalign / apksigner 所在目录
KS=yizhishilian-release.keystore
UNSIGNED=app/build/outputs/apk/release/com.chinatelecom.yizhishilian-release-unsigned.apk
OUT=yizhishilian-release.apk

# 1) 构建未签名 release
./gradlew assembleRelease

# 2) 首次生成签名密钥（已有则跳过；alias/口令按需修改，但之后要一直沿用）
[ -f "$KS" ] || "$JAVA_HOME/bin/keytool" -genkeypair -noprompt -alias yzsl \
  -dname "CN=YiZhiShiLian, OU=ChinaTelecom JS, O=ChinaTelecom, L=Nanjing, S=Jiangsu, C=CN" \
  -keystore "$KS" -storepass yizhishilian -keypass yizhishilian \
  -keyalg RSA -keysize 2048 -validity 10000

# 3) 对齐 + 签名
"$BT/zipalign" -f 4 "$UNSIGNED" /tmp/yzsl-aligned.apk
"$BT/apksigner" sign --ks "$KS" --ks-key-alias yzsl \
  --ks-pass pass:yizhishilian --key-pass pass:yizhishilian \
  --out "$OUT" /tmp/yzsl-aligned.apk

# 4) 校验签名 + 真机冒烟测试（release 与 debug 签名不同，安装前先卸载）
"$BT/apksigner" verify --print-certs "$OUT"
adb uninstall com.chinatelecom.yizhishilian
adb install "$OUT"
adb shell am start -n com.chinatelecom.yizhishilian/.MainActivity
```

产物 `yizhishilian-release.apk` 即可直接分享（对方需允许“安装未知来源应用”，首次启动授权相机）。

---

## 模型

`app/src/main/assets/` 内（随 APK 打包）：

```
assets/
├─ yolov8{n,s,m}.ncnn.{param,bin}        # COCO 检测（交通 / 机场）
├─ yolov8{n,s,m}_pose.ncnn.{param,bin}   # 姿态
└─ helmet-detect/
   └─ yolov8{n,s}.ncnn.{param,bin}       # 安监（安全帽，2 类）
```

### 模型约定（新增 / 替换模型务必遵守）

所有模型都必须是 **动态输入 shape**、且 **去掉后处理**（导出时剥离 NMS / 框解码 / sigmoid，后处理在各任务的 C++ 里实现）。检测输出为原始张量，每个 anchor 排布为 `[16×4 DFL 回归, num_class 类别 logit]`。

- 动态 shape 的关键：pnnx 最后一步给**两个**输入尺寸，reshape 才会生成动态 `0=-1`（而非写死 `0=6400`）。固定 shape 模型喂非正方形输入会满屏噪声框。

```sh
# 安监安全帽模型示例（2 类，输出通道 = 64 + 2 = 66）
pnnx yolov8n_pnnx.py.pt inputshape=[1,3,640,640] inputshape2=[1,3,320,320]
```

- **安监类别顺序**：index `0 = helmet`，`1 = head`（顺序须与训练时一致，否则标签会反）。
- 自检：`.param` 结尾的 Reshape 应为 `0=-1`（动态），而不是 `0=6400`（固定）。

COCO / 姿态模型的详细转换步骤参见上游 [nihui/ncnn-android-yolov8](https://github.com/nihui/ncnn-android-yolov8) 的转换指南。

---

## 架构速览

### Java ↔ 原生
`YOLOv8Ncnn.java` 声明 5 个 native 方法（`loadModel` / `openCamera` / `closeCamera` / `setOutputWindow` / `getFps`），实现于 `app/src/main/jni/yolov8ncnn.cpp`——它是控制中枢，持有全局模型 `g_yolov8` 与相机 `g_camera`，由单个 `ncnn::Mutex` 保护。

`MainActivity.java` 是薄 UI：三个下拉（场景 / 尺寸 / 推理后端）的选择直接传给 `loadModel(assets, taskid, modelid, cpugpu)`。

### loadModel 索引契约
- `taskid`：`0=交通 1=机场 2=姿态 3=安监`。安监从 `helmet-detect/` 子目录加载，其余从 assets 根目录。
- `modelid` 0–8：`modelid%3` 选尺寸 `{n,s,m}`，`modelid≥3 / ≥6` 把输入分辨率提到 `480 / 640`。
  - 安监「尺寸」下拉位置经 `MainActivity` 的映射表翻成 modelid（n/s × 320/480/640），跳过 m。
- `cpugpu`：`0=CPU 1=GPU 2=turnip`。

### 任务类层次（`yolov8.h` / `yolov8_*.cpp`）
- 抽象基类 `YOLOv8`，`YOLOv8_det` 实现共享的 `detect()`（含类别白名单与可调阈值）。
- `YOLOv8_det_coco` 提供 COCO 80 类绘制；`YOLOv8_det_traffic` / `YOLOv8_det_airport` 仅在构造函数里设各自白名单。
- `YOLOv8_det_helmet` 提供 helmet/head 两类绘制；`YOLOv8_pose` 为姿态。
- 后处理（NMS、框解码、关键点）都在各任务文件内；新增 `.cpp` 须登记进 `CMakeLists.txt`。

### 相机管线
`ndkcamera.cpp` 封装 Camera2 NDK；`on_image_render(cv::Mat& rgb)` 为每帧回调，`MyNdkCamera` 在锁内执行 `detect()` → `draw()`，并按 SurfaceView 宽高比对相机帧做中心裁剪（视频比例即由此实现）。

---

## 团队

| 成员 | 分工 |
|------|------|
| 徐霖涛 | 软件研发工程师 |
| 李广通 | AI 算法工程师 |
| 尤馨影 | 产品经理 |
| 刘阳 | 解决方案经理 |

**技术栈**
- 端侧模型部署：ncnn · pnnx · OpenCV · Java · Python · adb
- 硬件相关的模型适应：PyTorch · docker · ultralytics · onnx · timm
- 翼智视联云中台：（待补充）

---

## 致谢与许可

基于以下开源项目：

- [Tencent/ncnn](https://github.com/Tencent/ncnn)
- [nihui/opencv-mobile](https://github.com/nihui/opencv-mobile)
- [nihui/mesa-turnip-android-driver](https://github.com/nihui/mesa-turnip-android-driver)
- [nihui/ncnn-android-yolov8](https://github.com/nihui/ncnn-android-yolov8)（本项目 Fork 来源）

上游示例代码以 BSD-3-Clause 许可证发布，源码中保留了原始版权声明。
