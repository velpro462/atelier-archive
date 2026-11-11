# ✦ ATELIER ARCHIVE · 典藏纪

> **Haute Couture & Old Money Aesthetic · 小红书 & 抖音双平台无水印原画原生 Android 提取工具**  
> 采用纯本地解析架构（Pure On-Device Parsing），无中转服务器，依托设备原生能力直接解析、提取小红书和抖音的无水印高清原图集与 1080P 原画视频。

---

## 一、双平台支持矩阵 (Support Matrix)

| 平台 | 图文/图集支持 | 视频支持 | 解析原理与水印去除方案 |
| :--- | :---: | :---: | :--- |
| **小红书 (RED)** | ✅ 支持 | ✅ 1080P | 跟踪 302 短链重定向，从 SSR `window.__INITIAL_STATE__` 提取无水印 `imageList` 与 `h264 masterUrl`。 |
| **抖音 (Douyin)** | ✅ 支持 | ✅ 1080P | 跟踪 `v.douyin.com` 短链，利用移动端 User-Agent 获取直链并自动将视频流地址中的 `playwm` 替换为 `play` 滤除官方水印；图集直接提取 `url_list[0]` 无水印高清大图。 |

---

## 二、设计美学与视觉理念 (Old Money & Editorial Aesthetic)

本项目摒弃了常规工具软件千篇一律的粗糙扁平配色，深度重构了一套**法式高级时装杂志与老钱风（Quiet Luxury / Haute Couture Editorial）**的视觉设计系统：

* **羊皮纸基色（Parchment Base `#F9F7F2`）**：采用低饱和暖奶霜纸质感，传递厚重典藏画册质感。
* **复古深墨黑（Ink Black `#191716`）**：作为主要字重与核心行动按键，纯粹深沉。
* **沉淀香槟金（Champagne Gold `#C5A880`）**：微量用于刻印线、平台标识（`✦ RED ARCHIVE` / `✦ DOUYIN ARCHIVE`）与核心微光强调。
* **经典画报版式（Editorial Masthead & Double-Rule）**：杂志刊头、期号刻印（`N° 08 · EDITION PRIVÉE`）、双线装订裁线。
* **工坊锐角微倒角**：告别大圆角胶囊感，采用 2dp-3dp 极简硬朗几何线条。

---

## 三、核心原生能力与体验

1. **智能平台自动分流（ArchiveDispatcher）**：
   - 无论粘贴的是小红书的分享口令还是抖音的分享文案，引擎自动检测域名并分流至对应解析器。
2. **系统级原生分享直达（Android Intent Filter）**：
   - 在小红书或抖音 App 点击任意作品的「分享」→ 选择「ATELIER ARCHIVE」，应用自动唤起并直接开始解析，免去手动复制粘贴。
3. **剪贴板智能感应（双域侦测）**：
   - 切回应用时，自动识别剪贴板中的 `xhslink.com`、`xiaohongshu.com`、`v.douyin.com`、`douyin.com` 短链接，优雅滑出横幅一键解析。
4. **Android 10+ 分区存储（Scoped Storage & MediaStore API）**：
   - 适配 Android 10/11/12/13/14+ 最新存储规范，免去传统危险的外部存储读写权限申请。
   - 图片保存在系统相册 `Pictures/AtelierArchive/`，视频保存在 `Movies/AtelierArchive/`。
5. **智能绕过 CDN 防盗链（Referer 动态适配）**：
   - 下载时根据来源平台自动注入 `Referer: xiaohongshu.com` 或 `Referer: douyin.com`，彻底规避 CDN `403 Forbidden` 拦截。
6. **无水印高清大图检视器 & 原画视频播放器**：
   - 集成 Media3 (ExoPlayer) 支持原画视频本地即时播放预览。
   - 支持全画幅大图全屏检视。
7. **凭证备用配置（Cookie Preferences）**：
   - 支持自定义设置 Cookie，轻松应对平台临时风控。

---

## 四、工程结构说明

```text
AtelierArchive/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # 包含网络权限与系统级分享 Intent Filter
│   │   ├── java/com/atelier/archive/
│   │   │   ├── MainActivity.kt          # 核心 Activity，剪贴板侦听与 Intent 路由
│   │   │   ├── model/
│   │   │   │   └── NoteDetail.kt        # 笔记、图集、视频、平台及 UI 状态模型
│   │   │   ├── network/
│   │   │   │   ├── ArchiveDispatcher.kt # 双平台智能识别与分流引擎
│   │   │   │   ├── XhsParser.kt         # 小红书纯本地 SSR 解析
│   │   │   │   ├── DouyinParser.kt      # 抖音短链重定向、playwm 剥离与无水印解析
│   │   │   │   └── MediaDownloader.kt   # 动态 Referer 流式下载与 MediaStore 相册写入
│   │   │   └── ui/
│   │   │       ├── MainScreen.kt        # 双平台杂志主界面与画报预览流
│   │   │       ├── MainViewModel.kt     # 解析状态、下载并发进度与剪贴板控制
│   │   │       ├── theme/
│   │   │       │   ├── Color.kt         # 老钱风与高级时装调色板
│   │   │       │   ├── Type.kt          # 典藏杂志衬线与大字距排版
│   │   │       │   └── Theme.kt         # Material3 主题映射
│   │   │       └── components/
│   │   │           ├── EditorialHeader.kt       # 杂志刊头
│   │   │           ├── ParchmentInputSection.kt # 纸张质感输入卡片
│   │   │           ├── MagazineNoteCard.kt      # 双平台专属画报卡片与滑动预览
│   │   │           ├── MediaInspectDialog.kt    # 全屏高清图与原画视频播放模态
│   │   │           ├── ClipboardNoticeBanner.kt # 优雅剪贴板识别悬浮条
│   │   │           └── CookieConfigDialog.kt    # 凭证设置对话框
│   │   └── res/
│   │       ├── values/ (strings.xml, themes.xml)
│   │       └── drawable/ (高定金色暗纹 Monogram 图标)
│   └── build.gradle.kts                 # Compose, Coil, OkHttp, Media3 ExoPlayer 依赖
├── build.gradle.kts                     # 根工程 Gradle 配置
├── settings.gradle.kts
└── gradle.properties
```

---

## 五、如何在 Android Studio 中打开与构建

1. 打开 **Android Studio**。
2. 选择 **File -> Open...**，选中当前项目的根目录 `d:\Programming\fanzhongli\AtelierArchive`。
3. 等待 Gradle 同步依赖完成。
4. 连接 Android 手机或启动模拟器，点击顶部绿色的 **Run 'app'** 按钮即可安装体验。
