# MoodDiary｜心迹日记 📔

> 原生 Java Android 日记应用，**本地 Room 离线存储 + Supabase 云端双向同步双架构**，整套 UI 从零设计开发，集成火山引擎豆包大模型，实现 AI 治愈文案、AI 配图生成与 AI 情绪陪伴。

[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen?logo=android)](https://developer.android.com)
[![Java](https://img.shields.io/badge/Java-1.8-orange?logo=java)](https://www.java.com)
[![Room](https://img.shields.io/badge/Room-2.5.2-blue?logo=android)](https://developer.android.com/training/data-storage/room)
[![Supabase](https://img.shields.io/badge/Supabase-Backend-3ECF8E?logo=supabase)](https://supabase.com)

---

## ✨ 项目亮点

### 🔌 智能双存储模式
- **离线优先**：断网状态下数据仅存储在本地 Room 数据库，无需网络即可完整使用全部日记功能
- **云端同步**：联网后自动与 Supabase PostgreSQL 双向同步，同账号换机登录一键拉取全部历史日记
- **配置隔离**：`SupabaseConfig` 仅存占位参数，不填写密钥时默认纯本地运行，完全不会访问云端

### 📝 完整日记功能
- 用户注册 / 登录（支持 Supabase 云端认证 + Room 本地认证双通道）
- 日记新增 / 编辑 / 删除
- 6 种情绪标签绑定：开心 😊 · 难过 😢 · 生气 😡 · 平静 😐 · 焦虑 😰 · 疲惫 😩
- 支持从相册选择图片作配图，自动上传至 Supabase Storage 云端存储
- 日记收藏 / 取消收藏
- 浏览模式与编辑模式切换

### 📊 情绪数据统计模块
- **环形饼图**：自定义 `MoodPieChartView`，直观展示本月各类情绪占比与记录频次
- **柱状走势图**：自定义 `IntensityBarChartView`，本月每日情绪强度变化曲线
- **数据卡片**：本月记录天数、本周记录数、平均情绪强度、用户主导情绪四项量化统计
- AI 情绪洞察：基于周/月统计数据，自动生成情绪趋势分析与建议

### 🤖 火山引擎 AI 智能能力
接入**字节跳动火山引擎豆包大模型**，提供三项 AI 能力：
- **AI 治愈短句**（`AiHealingHelper`）：根据日记内容自动生成安抚、治愈类文案，每条日记带专属语录
- **AI 配图生成**（`AiIllustrationHelper`）：依据日记文字描述，一键生成对应插画配图；生成后自动上传至 Supabase Storage，换机不丢失
- **AI 情绪陪伴**（`AiChatActivity`）：多轮对话聊天机器人，系统提示词设定为温柔治愈的情绪陪伴助手

### 🔐 数据安全与隔离
- 不同账号数据相互独立，仅可查看本人日记
- Supabase RLS（Row Level Security）策略保障云端数据隔离
- Supabase Auth 提供 JWT 认证，本地 SharedPreferences 缓存登录态
- 敏感密钥通过 `local.properties` 注入，不提交 Git

### 🎨 主题与个性化
- 暖色主题（Warm）与深色主题（Dark）一键切换，全局即时生效
- 个人资料编辑：昵称、邮箱、个性签名、密码修改
- 头像更换：支持相册选择与拍照两种方式，自动裁剪圆形展示

### 🔔 通知提醒
- 每日提醒：按时提醒记录今日心情
- 每周报告：每周日记回顾通知
- 鼓励通知：随机推送正能量鼓励文案
- 自定义提醒时间

---

## 🏗️ 项目架构

```
mooddiary/
├── app/src/main/java/com/hearttrace/mooddiary/
│   ├── ui/                          # Activity 与 UI 组件（17 个页面 + RecordMoodDialog）
│   │   ├── LoginActivity.java       # 启动页 / 登录
│   │   ├── RegisterActivity.java    # 注册页
│   │   ├── HomeActivity.java        # 主页（日历视图 + 底部导航）
│   │   ├── DiaryListActivity.java   # 日记历史列表
│   │   ├── DiaryDetailActivity.java # 日记详情 / 编辑
│   │   ├── DetailActivity.java      # 日记新建 / 编辑
│   │   ├── StatisticsActivity.java  # 情绪统计图表
│   │   ├── CalendarActivity.java    # 独立日历筛选页
│   │   ├── ProfileActivity.java     # 个人中心
│   │   ├── ProfileDetailActivity.java   # 个人资料编辑
│   │   ├── MyFavoritesActivity.java     # 我的收藏
│   │   ├── AiChatActivity.java          # AI 情绪陪伴聊天
│   │   ├── NotificationSettingsActivity.java  # 通知设置
│   │   ├── ThemeSettingsActivity.java   # 主题切换
│   │   ├── AboutUsActivity.java         # 关于我们
│   │   ├── PrivacyPolicyActivity.java   # 隐私政策
│   │   └── RecordMoodDialog.java        # 首页"记录心情"弹窗
│   ├── model/                       # 数据模型
│   │   ├── User.java                # 用户实体（Room Entity）
│   │   └── MoodEntry.java           # 日记实体（含 remoteId / syncStatus 同步字段）
│   ├── dao/                         # Room DAO 数据访问层
│   │   ├── UserDao.java             # 用户 CRUD
│   │   └── MoodEntryDao.java        # 日记 CRUD + 统计查询 + 同步查询
│   ├── database/
│   │   └── AppDatabase.java         # Room 数据库（版本 7，含 2→7 完整迁移链）
│   ├── supabase/                    # Supabase 云端集成层
│   │   ├── SupabaseConfig.java      # 配置（从 BuildConfig 读取密钥）
│   │   ├── SupabaseAuthClient.java  # Auth 认证（注册 / 登录）
│   │   ├── SupabaseDataClient.java  # PostgreSQL 数据 CRUD
│   │   ├── SupabaseStorageClient.java  # Storage 图片上传
│   │   └── SyncManager.java         # 双向同步引擎（冲突解决 + 离线回写）
│   ├── utils/                       # 工具类
│   │   ├── DoubaoApiClient.java     # 豆包大模型 API 客户端（文生文 + 文生图）
│   │   ├── AiHealingHelper.java     # AI 治愈文案生成辅助
│   │   ├── AiIllustrationHelper.java   # AI 插画生成辅助
│   │   ├── AiChatHelper.java        # AI 聊天解析辅助
│   │   ├── MoodUiHelper.java        # 心情 → UI 映射（Emoji / 颜色 / 背景）
│   │   ├── ThemeHelper.java         # 主题切换管理
│   │   ├── PrefManager.java         # SharedPreferences 封装
│   │   └── CalendarHelper.java      # 日历工具
│   └── custom/                      # 自定义 View
│       ├── MoodPieChartView.java    # 情绪占比环形饼图
│       └── IntensityBarChartView.java  # 情绪强度柱状走势图
├── app/src/main/res/
│   ├── layout/                      # 布局文件（35+ XML 布局）
│   ├── drawable/                    # 矢量图标、圆角背景、心情图标
│   └── values/                      # 颜色、字符串、样式、主题
└── supabase_schema.sql              # 云端 PostgreSQL 建表脚本（含 RLS 策略）
```

### 同步策略

```
用户操作
  ├── 离线：写入 Room（syncStatus=1） → 联网后自动推送
  └── 在线：写入 Room → 后台异步推送 Supabase → 绑定 remoteId

首次启动
  ├── pushPendingEntries（推送本地未同步数据）
  └── pullFromCloud（拉取云端全部数据，按 updateTime 智能覆盖）

冲突解决
  ├── 本地有 remoteId → 云端时间更新则覆盖本地
  ├── 本地无 remoteId → timestamp+text 兜底匹配，绑定云端 remoteId
  └── 完全不存在 → 新建到本地
```

---

## 📱 编译与运行

### 环境要求

| 项目 | 版本要求 |
|------|----------|
| Android Studio | Ladybug 及以上（推荐） |
| JDK | 17+ |
| Gradle | 8.x（项目自带 wrapper） |
| Android SDK | compileSdk = 36, minSdk = 24（Android 7.0+） |
| 目标设备 | Android 7.0（API 24）及以上机型 |

### 快速开始

```bash
# 1. 克隆仓库
git clone https://github.com/ppptatoo/MoodDiary.git
cd MoodDiary

# 2. 复制密钥配置模板
cp local.properties.example local.properties

# 3. 编辑 local.properties，填入你的密钥（可选，留空则纯本地运行）
# supabase.url=https://YOUR_PROJECT_ID.supabase.co
# supabase.anonkey=YOUR_ANON_KEY
# ARK_API_KEY=YOUR_ARK_API_KEY
# ARK_CHAT_MODEL_ID=YOUR_CHAT_MODEL_ID
# ARK_IMAGE_MODEL_ID=YOUR_IMAGE_MODEL_ID

# 4. 用 Android Studio 打开项目，Sync Gradle，点击 Run
```

> **无需填写密钥即可运行**：不填 Supabase 和豆包密钥时，应用以纯本地模式运行，所有日记数据存储在本地 Room 数据库，AI 功能按钮不显示。

---

## 🔧 密钥配置说明

### Supabase（云端同步）

1. 前往 [Supabase](https://supabase.com) 创建项目
2. 在 **SQL Editor** 中执行 `supabase_schema.sql` 建表
3. 在 **Settings → API** 获取 `Project URL` 和 `anon public key`
4. 填入 `local.properties`：
   ```properties
   supabase.url=https://YOUR_PROJECT_ID.supabase.co
   supabase.anonkey=YOUR_ANON_KEY
   ```
5. 如需图片云端存储，在 **Storage** 中创建名为 `mood-images` 的公开 Bucket，并添加允许读取和上传的 Policy

### 豆包大模型（AI 功能）

1. 前往 [火山引擎-豆包大模型](https://console.volcengine.com/ark) 开通服务
2. 获取 API Key 和模型 ID（推荐 `doubao-pro-32k` 作对话模型）
3. 填入 `local.properties`：
   ```properties
   ARK_API_KEY=YOUR_ARK_API_KEY
   ARK_CHAT_MODEL_ID=YOUR_CHAT_MODEL_ID
   ARK_IMAGE_MODEL_ID=YOUR_IMAGE_MODEL_ID
   ```

---

## 📦 关键依赖

```groovy
// Room 本地数据库
implementation 'androidx.room:room-runtime:2.5.2'
annotationProcessor 'androidx.room:room-compiler:2.5.2'

// 网络通信
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
implementation 'com.google.code.gson:gson:2.10.1'

// 图片加载
implementation 'com.github.bumptech.glide:glide:4.16.0'

// Google UI 组件
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'

// 生命周期
implementation 'androidx.lifecycle:lifecycle-viewmodel:2.7.0'
implementation 'androidx.lifecycle:lifecycle-livedata:2.7.0'
```

---

## 🗄️ 数据库设计

### 本地 Room

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `users` | 用户表 | `id`, `username`, `password` |
| `mood_entries` | 日记表 | `id`, `userId`, `timestamp`, `text`, `userEmotionLabel`, `imagePath`, `isFavorite`, `aiImagePath`, `aiQuote`, `remoteId`, `syncStatus` |

### 云端 Supabase PostgreSQL

| 表名 | 说明 | RLS 策略 |
|------|------|----------|
| `mood_entries` | 日记表 | 用户只能读写自己的日记 |
| `user_profiles` | 用户资料表 | 用户只能读写自己的资料 |

---

## 📄 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 网络通信（Supabase 同步 + AI 接口调用） |
| `READ_EXTERNAL_STORAGE` | 从相册选择配图 |
| `WRITE_EXTERNAL_STORAGE` | 保存 AI 生成的图片到本地 |
| `ACCESS_MEDIA_LOCATION` | Android 10+ 媒体文件访问 |

---

## 🖼️ 心情图标映射

| 心情 | Emoji | 颜色 | 图标 |
|------|-------|------|------|
| 开心 | 😊 | `#FFD93D`（暖黄） | `ic_mood_happy` |
| 难过 | 😢 | `#6C9BCF`（淡蓝） | `ic_mood_sad` |
| 生气 | 😡 | `#E06469`（橙红） | `ic_mood_angry` |
| 平静 | 😐 | `#A8D8A8`（薄荷绿） | `ic_mood_calm` |
| 焦虑 | 😰 | `#C9B1FF`（淡紫） | `ic_mood_anxious` |
| 疲惫 | 😩 | `#B0BEC5`（灰蓝） | `ic_mood_tired` |

---

## 🚧 项目结构一览

| 模块 | 文件数 | 说明 |
|------|--------|------|
| 页面 Activity | 15 个 | 登录/注册/主页/日历/列表/详情/统计/收藏/AI聊天/个人中心/设置/关于/隐私 |
| 弹窗组件 | 1 个 | RecordMoodDialog |
| 数据模型 | 2 个 | User + MoodEntry |
| DAO 接口 | 2 个 | UserDao + MoodEntryDao |
| Supabase 集成 | 5 个 | Config + Auth + Data + Storage + SyncManager |
| 工具类 | 7 个 | AI 客户端 + AI 辅助类 + UI 映射 + 主题 + 偏好管理 + 日历 |
| 自定义 View | 2 个 | 情绪饼图 + 强度柱状图 |
| 布局文件 | 35+ 个 | 页面布局 + 列表项 + 弹窗 + 日历格子 |
| 数据库迁移 | 5 条 | 2→3→4→5→6→7 |

---

## 📝 License

MIT License

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

如有问题或建议，请联系：`2358299518@qq.com`
