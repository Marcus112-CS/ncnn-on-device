# 安全帽实时检测告警 App — 需求与设计

> 基于 `ncnn-android-yolov8` demo 二次开发。端侧实时检测，无后处理依赖云端。

## 1. 功能需求

### 1.1 模型管理
- 仅保留 **两个 YOLOv8 目标检测模型**（均为安全帽数据集训练，非 COCO）：
  - **全量模型**：原始未压缩，精度优先。
  - **裁剪模型**：剪枝/压缩版，体积更小、推理更快。
- App 内可**下拉切换**两个模型；保留 CPU / GPU(Vulkan) / Turnip 后端切换便于对比。
- 切换模型实时生效（复用现有 `loadModel` 重载机制）。

### 1.2 实时可视化
- 摄像头实时画面叠加：**检测框 + 类别 + 置信度**。
- 右上角实时 **FPS**（10 帧滑动平均，现有 `draw_fps` 复用）。
- 屏幕显示的是**带框**画面（给操作者看）。

### 1.3 无帽告警
- 检测目标：**未戴安全帽的人**（对应模型的 `head`/无帽类，置信度 ≥ 告警阈值）。
- 触发条件：**同一个人**累计无帽时长 **≥ 5s**（见 §2.3 跟踪语义）。
- 触发动作：抓取**当前原始帧（不带框）**，连同元数据 HTTP 上报到指定**公网 IP 服务器**。
- **冷却 / 再武装**：同一目标告警后进入冷却期，不重复刷屏；目标离场再回来重新计时。

### 1.4 网络上报
- 截图内容：**原始摄像头帧**（未标注框）。
- 传输：HTTP `multipart/form-data` POST。
- 上传在**后台线程**执行，绝不阻塞相机渲染线程。

## 2. 设计思路

### 2.1 整体数据流
```
摄像头(NDK Camera2) → cv::Mat rgb
  └ on_image_render (native, 每帧, 相机线程)
       1. detect() → objects[]
       2. 轻量 IoU 跟踪器：关联"无帽"框到 track，累计每条 track 无帽时长
       3. 任一 track 累计 ≥5s 且未告警 → 触发：
            cv::Mat raw = rgb.clone()          // draw 之前克隆 = 原始帧
            cv::imencode(".jpg", raw)
            AttachCurrentThread → 回调 Java onHelmetAlarm(jpeg, conf, ts)
            标记该 track 已告警（进入冷却）
       4. draw(objects)                        // 屏幕仍画框+置信度
       5. draw_fps()
  └ Java 监听器 → ExecutorService 后台线程 → HTTP multipart POST → 公网 IP
```
**关键**：原始帧克隆必须发生在 `draw()` 之前（要无框图）；屏幕显示又要带框 —— 靠"先克隆原始、再在原 rgb 上画框"分离。

### 2.2 Java ↔ native 边界
- `loadModel(assets, modelid, cpugpu)`：简化后只选 模型(0/1) + 后端。
- native `JNI_OnLoad` 缓存 `JavaVM*` 与监听器 global ref；告警时 `AttachCurrentThread` 回调 Java。
- Java 侧单线程 `ExecutorService` + `HttpURLConnection` 做 multipart 上传，失败有限重试 / 丢弃，不堆爆队列。

### 2.3 跟踪与告警语义（"必须同一个人"）
- 端侧轻量 **IoU 贪心跟踪**（非 ByteTrack）：每条 `Track{ id, bbox, no_helmet_accum_ms, last_seen_ms, alarmed }`。
- 每帧：当前无帽框按 IoU(~0.3) 贪心匹配已有 track → 更新累计时长；未匹配检测建新 track；超 `max_age` 老化清理。
- **兜底（降低漏报）**：
  - `max_age` 容忍短暂丢失若干帧不立即清零；
  - 计时用"该 track **累计**无帽时长"而非"严格连续帧"，避免遮挡一下就归零。
- 预留开关：可一键切回"画面存在无帽即触发"（更不易漏报）。

### 2.4 权限与网络配置
- `AndroidManifest.xml` 增加 `INTERNET` 权限（当前缺失）。
- targetSdk 35 默认禁明文：对公网 IP 配 `usesCleartextTraffic="true"` 或 network-security-config 白名单（裸 IP 走 http）。

## 3. 已知风险

| # | 风险 | 说明 / 对策 |
|---|------|-------------|
| 1 | 模型任务错配 | 必须是安全帽训练模型；COCO 剪枝无效。✅ 用户已备两个模型 |
| 2 | 类别顺序错位 | draw 类名表 / 告警判定 index 必须对齐训练，否则静默画错、判错 |
| 3 | 掉帧 | 网络 I/O 绝不放相机线程；native 只编码+回调 |
| 4 | 告警风暴 | 必须有冷却 / 再武装机制 |
| 5 | 明文被拦 + 缺权限 | 加 INTERNET + cleartext 配置 |
| 6 | 裁剪模型改了检测头 | 输出布局须与 `yolov8_det.cpp` 解析(3 stride/DFL 64)一致 |
| 7 | 同一人语义漏报 | track 断裂清零 → 真实违规者漏报；靠 §2.3 兜底缓解 |

## 4. 待定参数（开发前需用户确认）
- **类别表**：模型类别名与顺序，哪个 index = 无帽/head。
- **服务器接口**：公网 IP / 端口 / 路径、字段名（图片字段、是否带 timestamp/conf）、http 还是 https。
- **阈值**：告警置信度阈值、冷却时长（默认 30s）、track `max_age`（默认丢失 ~15 帧清理）。
