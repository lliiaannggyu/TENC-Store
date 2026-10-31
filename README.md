# GitHub Store for Wear OS

一个运行在 **Wear OS（手表）** 上的 GitHub 项目下载商店。它只下载 **Release 产物（APK 等二进制文件）**，并且完全采用 **Google 官方 Material Design for Wear OS** 设计语言（Jetpack Compose for Wear OS）。

> 适合用来在手表上浏览 GitHub 开源项目的发布页，挑出 APK 直接下载并安装（例如 F-Droid、各类 Wear OS / 手表应用）。

---

## 功能

- 🔍 **搜索仓库**：支持手表端语音 / 键盘输入（Wear OS 官方 `RemoteInput` 输入方案），并提供常用快捷词。
- 📦 **Release 列表**：自动跳过草稿，标注是否为预发布。
- 📲 **仅 APK 产物**：过滤出 `.apk`（或 `application/vnd.android.package-archive`）工件，忽略源码、校验和等其它产物。
- ⬇️ **下载与安装**：通过系统 `DownloadManager` 下载到 App 私有目录，完成后用 `FileProvider` + 系统安装器安装。
- 📂 **我的下载**：查看 / 重新安装 / 删除已下载的 APK。
- 🔑 **可选 Token**：在设置中填入 GitHub Personal Access Token 以提高 API 速率限制（匿名仅 60 次/小时）。

---

## 技术栈与设计语言

| 类别 | 选型 |
| --- | --- |
| UI | **Jetpack Compose for Wear OS**（`androidx.wear.compose`） |
| 设计语言 | **Material Design for Wear OS**（`compose-material` 官方组件：`Scaffold` / `TimeText` / `Vignette` / `PositionIndicator` / `Chip` / `CompactChip` / `ScalingLazyColumn` / `CircularProgressIndicator` / `Dialog`） |
| 导航 | `androidx.wear.compose.navigation` 的滑动返回 `SwipeDismissableNavHost` |
| 输入 | `androidx.wear:wear-input` 的 `RemoteInputIntentHelper`（手表端文本输入官方方案） |
| 网络 | Retrofit 2 + OkHttp 3 + Gson |
| 下载 | 系统 `DownloadManager` |
| 架构 | Repository + ViewModel（StateFlow）+ 轻量 `AppContainer`（无额外 DI 框架） |

> 想升级到最新的 **Material 3 for Wear OS**？把 `ui/theme/Theme.kt` 里的 `MaterialTheme` 换成 `androidx.wear.compose.material3.MaterialTheme` 即可，组件名基本一致。

---

## 项目结构

```
WearOSGitHubStore/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/weargithubstore/
│       │   ├── MainActivity.kt
│       │   ├── WearGitHubStoreApplication.kt
│       │   ├── data/
│       │   │   ├── AppContainer.kt        # 轻量依赖容器
│       │   │   ├── AppSettings.kt         # Token 本地存储
│       │   │   ├── remote/                # Retrofit 服务 / DTO / GitHubClient
│       │   │   ├── repository/            # GitHubRepository
│       │   │   └── download/ApkDownloader.kt
│       │   ├── domain/model/Models.kt    # Repo / Release / Asset
│       │   └── ui/
│       │       ├── WearApp.kt             # 导航图
│       │       ├── theme/Theme.kt
│       │       ├── util/{InputHelper,Format}.kt
│       │       ├── search/                # 搜索页 + ViewModel
│       │       ├── releases/              # 发布页 + ViewModel
│       │       ├── assets/                # 产物页 + 下载 ViewModel
│       │       ├── downloads/             # 我的下载
│       │       └── settings/              # Token 设置
│       └── res/                           # 字符串/颜色/主题/图标/file_paths
├── build.gradle.kts / settings.gradle.kts / gradle.properties
└── gradlew / gradlew.bat / gradle/wrapper # 已生成 Gradle 8.9 包装
```

---

## 如何运行

1. 用 **Android Studio（Hedgehog 或更新版本）** 打开本项目根目录。
2. 配置 Android SDK（在 `local.properties` 写入 `sdk.dir=/path/to/sdk`，或在 IDE 中设置）。
3. 连接 **Wear OS 模拟器（API 30+）** 或真实手表（需开启开发者选项 + 无线调试）。
4. 运行 `app` 模块（或命令行 `./gradlew :app:installDebug`）。

> 最低要求 `minSdk 25`（Wear OS 2 / Android 7.1）。`compileSdk 34`，Kotlin 1.9.22，Compose 编译器 1.5.14。

### 机密配置（不入仓库）

应用内置的**密码本**（日志口令 / 开发者邮箱加解密）与**发布签名**不随仓库分发。构建前需在根目录创建 `secrets.properties`（已被 `.gitignore` 排除）：

```properties
LOG_CODEBOOK=你的密码本
KEYSTORE_FILE=release.keystore
KEYSTORE_PASSWORD=你的签名密码
KEY_ALIAS=你的别名
KEY_PASSWORD=你的签名密码
```

- `LOG_CODEBOOK` 会经 `build.gradle.kts` 编译期注入 `BuildConfig`，运行时由 `data/Secrets.kt` 读取；收发双方应用内含同一把才能互通。
- 缺少 `secrets.properties` 时项目仍可编译，release 构建回退 debug 签名（仅用于本地调试）。

---

## 权限说明

- `INTERNET`：访问 GitHub API 与下载产物。
- `REQUEST_INSTALL_PACKAGES`：下载完成后调起系统安装器安装 APK。
- `POST_NOTIFICATIONS`：在 API 33+ 上显示下载通知。

> 注意：在部分 Wear OS 系统上，手表安装未知来源 APK 可能受限制。若安装器未弹出，可改用「我的下载」页用手机配套 App 传输，或确认设备已允许「未知来源」。

---

## 已知限制

- 未登录 GitHub 时 API 速率限制为 **60 次/小时**；在设置中填入 Token 可提升。
- 手表输入体验较弱，复杂搜索建议先在手机端构思好关键词。
- 仓库详情仅展示 Release 与 APK 产物，不做源码浏览。
