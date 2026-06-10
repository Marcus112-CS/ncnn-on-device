# 安全帽告警 App — TODO

按阶段推进，每阶段可单独验证。详见 [requirement.md](./requirement.md)。

## ⛳ 开发前置（阻塞，需用户给参数）
- [ ] 确认模型**类别表**：类别名 + 顺序，哪个 index = 无帽/head
- [ ] 确认**服务器接口**：公网 IP / 端口 / 路径、字段名、http/https
- [ ] 确认**阈值**：告警置信度、冷却时长(默认30s)、track max_age(默认~15帧)

## Phase 0 — 接入两个安全帽模型（先保证检测正确）
- [ ] `assets/` 删除冗余模型文件（seg/pose/cls/obb/oiv7/各尺寸 ~28 个）
- [ ] 放入 `helmet_full.ncnn.{param,bin}` / `helmet_pruned.ncnn.{param,bin}`
- [ ] `yolov8_det.cpp`：COCO 80 类名表 → 安全帽类名表（顺序对齐训练 index）
- [ ] `CMakeLists.txt`：`add_library` 移除 `yolov8_seg/pose/cls/obb.cpp`
- [ ] ✅验证：跑起来能正确框出戴/未戴帽（不通过则停，勿继续）

## Phase 1 — UI 与 loadModel 瘦身
- [ ] 删除 Task spinner；Model spinner 改两项 `全量 / 裁剪`
- [ ] 保留 CPU/GPU/Turnip spinner
- [ ] `yolov8ncnn.cpp::loadModel`：移除 taskid / modelid%3 / target_size 索引契约，仅留 模型0/1 + 后端
- [ ] ✅验证：切换两个模型生效，FPS/框/置信度正常显示

## Phase 2 — 告警状态机 + 轻量跟踪（native）
- [ ] `yolov8ncnn.cpp` 加 file-static `std::vector<Track>`（受现有 `lock` 保护）
- [ ] 每帧 IoU(~0.3) 贪心匹配 → 更新 `no_helmet_accum_ms` → `max_age` 老化清理
- [ ] 累计 ≥5s 且未告警 → 触发；加冷却 / 再武装
- [ ] 预留"画面存在即触发"开关
- [ ] ✅验证：无帽目标持续 ~5s 后日志打出触发；冷却生效不刷屏

## Phase 3 — native→Java 回调 + 上传
- [ ] `JNI_OnLoad` 缓存 `JavaVM*` + 监听器 global ref
- [ ] 触发时 draw 之前 `rgb.clone()` → `cv::imencode(".jpg")` → `AttachCurrentThread` 回调
- [ ] Java 加 `onHelmetAlarm(byte[] jpeg, float conf, long ts)` 监听
- [ ] Java 单线程 `ExecutorService` + `HttpURLConnection` multipart POST
- [ ] 失败有限重试 / 丢弃，队列不堆爆
- [ ] ✅验证：服务器收到原始帧 JPEG + 元数据

## Phase 4 — 权限与明文网络配置
- [ ] `AndroidManifest.xml` 加 `<uses-permission android:name="android.permission.INTERNET"/>`
- [ ] 配 `usesCleartextTraffic="true"` 或 network-security-config 白名单（公网 IP http）
- [ ] ✅验证：真机端到端打通，告警截图成功落到服务器

## 收尾
- [ ] APK 体积复核（删冗余资产后应大幅下降）
- [ ] 两个模型 CPU vs GPU FPS 实测对比记录
