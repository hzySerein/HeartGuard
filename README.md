# HeartGuard · 心守护

一款面向老年人的综合健康守护 Android 应用，融合 **AI 陪伴聊天、反诈演练、智能用药提醒、一键紧急呼叫** 四大功能，以简洁友好的交互和语音优先的设计理念，降低老年用户的使用门槛。

---

## 功能概览

| 模块 | 能力 |
|------|------|
| 🤖 AI 陪伴 | 多角色语音聊天（老虎 / 护士 / 孙女 / 志愿者），支持按住说话、文字输入、语音回复 |
| 🛡️ 反诈演练 | 4 大常见诈骗场景模拟（银行 / 公安 / AI 换脸 / 冒充亲友），含语音电话与视频演练 |
| 💊 用药提醒 | 手动 / 拍照 / 语音三种录入方式，支持每日 / 隔日 / 自定义周期，定时推送提醒通知 |
| 🚨 紧急呼叫 | 滑动手势一键拨打 120，同时发送含定位的短信至紧急联系人 |
| ⚙️ 个人设置 | 头像昵称、字体缩放（4 档）、语音偏好、紧急联系人管理、帮助与隐私说明 |
| 📱 桌面小组件 | 实时显示今日待服药数，一键直达紧急呼叫页 |

---

## 技术架构

```
┌─────────────────────────────────────────────┐
│              UI 层 (Jetpack Compose)          │
│  MainScreen → NavHost → 4 个底部 Tab         │
└───────────────────────┬─────────────────────┘
                        ▼
┌─────────────────────────────────────────────┐
│          ViewModel 层 (HiltViewModel)         │
│  ChatVM / MedicationVM / AntiFraudVM / …      │
└───────────────────────┬─────────────────────┘
                        ▼
┌─────────────────────────────────────────────┐
│        DI 层 (Dagger Hilt)                    │
│  AppModule · DatabaseModule · AiModule        │
└───────────────────────┬─────────────────────┘
                        ▼
┌──────────────┬──────────────────┬────────────┐
│   Remote     │      Local       │   工具层    │
│  AiGateway   │   AppDatabase    │ Settings    │
│  VivoAiRepo  │   Room · 3 DAO   │ AudioEngine │
│              │                  │ NativeOCR   │
└──────────────┴──────────────────┴────────────┘
```

### 技术栈

| 类别 | 技术选型 |
|------|----------|
| 语言 / 构建 | Kotlin 2.0.21 · Gradle 8.7.3 (KTS) |
| UI | Jetpack Compose (Material 3) · Navigation Compose |
| 架构 | MVVM · Hilt 依赖注入 |
| 本地存储 | Room 数据库（版本 7，含完整迁移）· SharedPreferences |
| 网络 | OkHttp 4 · Retrofit 2 · Kotlinx Serialization |
| AI 服务 | vivo AI 开放平台（WebSocket ASR / HTTP Chat / WebSocket TTS） |
| OCR | Google ML Kit 中文文字识别 |
| 录音 / 播放 | 自研 `AudioEngine`（PCM 16k/16bit WAV）· MediaPlayer |
| 闹钟提醒 | AlarmManager（精确闹钟 + 降级兜底） |
| 桌面临时 | Glance AppWidget |
| 定位 | FusedLocationProviderClient |
| 音视频 | CameraX · Media3 ExoPlayer |

---

## 模块详解

### 🤖 AI 陪伴聊天

- **角色系统**：点击头像可切换 4 种陪伴角色，每种角色对应独立的 System Prompt，切换即时生效。
- **语音输入**：长按说话按钮开始录音，松开发送；手指滑出按钮区域或按下取消键可中断。
- **语音回复**：AI 回复文本通过 TTS 合成语音自动播放，播放状态实时反馈。
- **身份保护**：内置敏感词过滤，当用户询问模型身份或 AI 回复中出现特定厂商关键词时，自动替换为预置话术。
- **会话持久化**：最近 50 条对话存入 Room，应用重启后自动恢复。

### 🛡️ 反诈演练

| 场景 | 类型 | 练习方式 |
|------|------|----------|
| 银行异常交易诈骗 | 高风险 | 语音电话 |
| 公安涉嫌案件诈骗 | 高风险 | 语音电话 |
| AI 换脸熟人视频 | 高风险 | 视频通话 |
| 冒充亲友急用钱 | 中风险 | 语音电话 |

- 每个场景包含完整话术脚本、风险关键词、正确操作建议。
- 语音电话演练：AI 实时生成诈骗语音，用户挂断即判定"通过"，完成演练后进入结果页展示知识点。
- 视频演练：播放预录诈骗视频 + TTS 配音，12 秒无操作自动挂断并记录"未通过"。
- 演练统计数据（总次数 / 通过次数 / 最近类型）持久化存储，主屏实时显示。

### 💊 智能用药提醒

- **录入方式**：
  - **手动添加**：支持名称、剂量步进器、时段多选、重复频率（每日 / 隔日 / 自定义 N 天）、样式选择
  - **拍照识别**：调用 ML Kit OCR 提取药盒文字 → AI 解析结构化信息 → 用户确认
  - **语音描述**：按住说话描述药品 → ASR 识别 → AI 解析 → 用户确认
- **重复规则**：每日 / 隔日 / 自定义间隔（2–30 天）/ 单次，均支持生效日管理。
- **提醒机制**：
  - `AlarmManager` 提前最多 370 天预排所有提醒闹钟
  - Android 13+ 需授权 `POST_NOTIFICATIONS` 权限
  - 支持服药 / 稍后提醒 / 编辑操作
  - 桌面 widget 实时展示今日待服药数
- **数据模型**：药物信息、服药计划、服药日志三表规范化设计，支持精准统计每日服药进度。

### 🚨 紧急呼叫

- **滑动手势**：自实现拖拽条，滑动至 85% 阈值自动触发，中途可回退。
- **多重保障**：
  1. 拨打紧急电话（120）
  2. 向所有紧急联系人发送含 GPS 经纬度坐标的短信
- **权限协调**：批量申请 `CALL_PHONE` / `SEND_SMS` / `ACCESS_FINE_LOCATION` 三项运行时权限；无通话权限时自动回退拨号盘界面。
- **联系人管理**：支持增删改查，JSON 序列化存储。

---

## 目录结构

```
app/src/main/java/com/heartguard/
├── MainActivity.kt                     # 应用入口，处理提醒 / 紧急 Intent
├── HeartGuardApp.kt                    # @HiltAndroidApp
├── di/
│   ├── AppModule.kt                    # 应用级依赖（Settings / OCR）
│   ├── DatabaseModule.kt               # Room 数据库 / DAO 注入
│   └── AiModule.kt                     # AI 网关注入
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt              # Room 数据库 + 7 个版本迁移
│   │   ├── AppDao.kt                   # 用药 & 反诈 & 聊天通用 DAO
│   │   ├── ChatDao.kt                  # 聊天消息 DAO
│   │   ├── FraudDao.kt                 # 反诈记录 DAO
│   │   └── *Entity.kt                  # 数据实体定义
│   └── remote/
│       ├── AiGateway.kt                # AI 服务接口定义
│       └── VivoAiRepository.kt         # vivo AI 实现（ASR / Chat / TTS）
├── domain/usecase/medication/          # 用药领域用例
├── reminder/
│   ├── ReminderScheduler.kt            # 闹钟调度核心逻辑
│   ├── ReminderAlarmReceiver.kt        # 闹钟触发广播接收
│   └── ReminderLaunchEvent.kt          # 提醒启动事件
├── ui/
│   ├── screens/
│   │   ├── MainScreen.kt               # 底部导航 + 全局导航图
│   │   ├── HomeScreen.kt               # AI 陪伴主页
│   │   ├── AntiFraudScreen.kt          # 反诈大厅 + 详情 + 结果
│   │   ├── EmergencyScreen.kt          # 紧急呼叫页
│   │   ├── SettingsScreen.kt           # 设置页（含子导航）
│   │   ├── MedicationListScreen.kt     # 用药列表页
│   │   ├── FakeCallScreen.kt           # 语音电话演练
│   │   ├── FakeVideoCallScreen.kt      # 视频通话演练
│   │   ├── SplashScreen.kt             # 启动页
│   │   └── UserAvatar.kt               # 用户头像组件
│   ├── theme/
│   │   ├── Color.kt                    # 调色板
│   │   ├── Theme.kt                    # 主题（含字体缩放）
│   │   └── Type.kt                     # 文字样式
│   └── widget/
│       └── HeartGuardWidget.kt         # 桌面小组件
├── utils/
│   ├── SettingsManager.kt              # 偏好设置管理
│   ├── AudioEngine.kt                  # 录音 / 播放引擎
│   ├── NativeOCRHelper.kt              # ML Kit OCR 封装
│   └── DebugLogger.kt                  # 调试日志工具
└── viewmodel/
    ├── ChatViewModel.kt                # 聊天逻辑
    ├── MedicationViewModel.kt          # 用药逻辑
    ├── AntiFraudViewModel.kt           # 反诈统计
    ├── FakeCallViewModel.kt            # 语音演练逻辑
    └── FakeVideoCallViewModel.kt       # 视频演练逻辑
```

---

## 配置说明

### 必填环境变量（vivo AI 服务）

在 `local.properties` 或 Gradle 项目属性中配置：

| 属性名 | 说明 |
|--------|------|
| `VIVO_APP_ID` | vivo AI 开放平台应用 ID |
| `VIVO_APP_KEY` | vivo AI 开放平台应用密钥 |
| `VIVO_CHAT_MODEL` | 聊天模型名称（默认 `Doubao-Seed-2.0-mini`） |

### 权限清单

应用声明以下运行时权限：

| 权限 | 用途 |
|------|------|
| `RECORD_AUDIO` | 语音聊天 / 语音录入药品 |
| `CAMERA` | 拍照识别药品包装 |
| `CALL_PHONE` | 紧急呼叫 / 拨打联系人 |
| `SEND_SMS` | 发送含定位的紧急短信 |
| `ACCESS_FINE_LOCATION` | 获取 GPS 坐标附在短信中 |
| `SCHEDULE_EXACT_ALARM` | 精准用药提醒闹钟 |
| `POST_NOTIFICATIONS` | 用药提醒通知（Android 13+） |

---

## 运行

```bash
# 1. 配置 vivo AI 凭证（local.properties）
VIVO_APP_ID=your_app_id
VIVO_APP_KEY=your_app_key

# 2. 同步并构建
./gradlew assembleDebug

# 3. 安装
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 设计亮点

- **无障碍优先**：大字体（4 档可选）、大按钮、高对比度配色、语音优先交互
- **权限安全**：运行时逐项申请，缺失时提供优雅降级（如无通话权限则打开拨号盘）
- **数据可靠**：Room 数据库含 7 个版本的完整迁移链，支持从早期单表结构平滑升级
- **优雅容错**：AI 服务不可用时使用本地兜底话术；录音异常自动清理临时文件；闹钟权限缺失时降级为非精确闹钟
