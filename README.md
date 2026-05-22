# Android Starter

通用 Android 项目起始框架，基于 **Jetpack Compose + Material Design 3 蓝色主题 + 卡片化设计**，开箱即用，可直接作为新项目基础。

[![Android CI](https://github.com/dac114514/android-starter/actions/workflows/android.yml/badge.svg)](https://github.com/dac114514/android-starter/actions/workflows/android.yml)

---

## 特性

- **Material 3 蓝色主题** — primary `#1976D2`，深色模式 primary `#9ECAFF`，完整 light/dark color scheme
- **卡片化设计** — `ItemCard` 组件：12dp 圆角、2dp elevation、4dp 左侧强调色条
- **底部导航** — 3 个 Tab（首页 / 列表 / 设置），Navigation Compose 管理路由
- **ViewModel + StateFlow** — 每个页面独立 ViewModel，Compose 生命周期友好
- **DataStore 主题持久化** — 支持跟随系统 / 强制浅色 / 强制深色，重启保留
- **GitHub Actions CI** — 推送自动触发 `assembleDebug`，产出 APK artifact
- **ARM64 兼容** — 内置 Termux/Proot 环境下的 AAPT2 替换脚本，CI 与本地构建均可用

---

## 技术栈

| 项目 | 版本 |
|---|---|
| Kotlin | 2.3.10 |
| Android Gradle Plugin | 9.0.0 |
| Compose BOM | 2026.01.01 |
| Navigation Compose | 2.8.5 |
| Lifecycle ViewModel Compose | 2.8.7 |
| Material Icons Extended | 1.7.8 |
| DataStore Preferences | 1.1.1 |
| minSdk / targetSdk | 24 / 35 |

---

## 项目结构

```
app/src/main/java/com/example/androidstarter/
├── MainActivity.kt                  # 入口：底部导航 + 主题收集
├── data/
│   ├── local/
│   │   ├── ThemeMode.kt             # 枚举：SYSTEM / LIGHT / DARK
│   │   └── PreferencesRepository.kt # DataStore 读写封装
│   ├── model/
│   │   └── SampleItem.kt            # 示例数据模型
│   └── repository/
│       └── SampleRepository.kt      # 示例数据 Flow
└── ui/
    ├── theme/
    │   ├── Color.kt                 # 蓝色调色板（含深色变体）
    │   ├── Theme.kt                 # AndroidStarterTheme + 动态色
    │   └── Type.kt                  # Material 3 字体比例
    ├── navigation/
    │   ├── Routes.kt                # 路由常量
    │   └── AppNavHost.kt            # NavHost 配置
    ├── components/
    │   ├── ItemCard.kt              # 通用卡片组件
    │   └── SectionHeader.kt         # 分组标题
    ├── home/
    │   ├── HomeScreen.kt            # 首页：欢迎卡 + 功能介绍
    │   └── HomeViewModel.kt
    ├── list/
    │   ├── ListScreen.kt            # 列表页：LazyColumn + ItemCard
    │   └── ListViewModel.kt
    └── settings/
        ├── SettingsScreen.kt        # 设置页：主题切换 + 应用信息
        └── SettingsViewModel.kt
```

---

## 快速开始

### 基于本模板新建项目

1. 替换包名：全局搜索 `com.example.androidstarter` → 你的包名
2. 修改应用名：`app/src/main/res/values/strings.xml`
3. 修改 `app/build.gradle.kts` 中的 `namespace` 和 `applicationId`
4. 重命名 `java/com/example/androidstarter/` 目录
5. 替换启动器图标（`res/drawable/ic_launcher_*.xml` + `res/mipmap-*/`）

### CI 构建（推荐）

推送到 `main` 或 `master` 分支后，GitHub Actions 自动构建。查看产出：

> **Actions → 最新 workflow run → Artifacts → app-debug**

### ARM64 本地构建（Termux/Proot）

```bash
chmod +x ./setup_android_env.sh
./setup_android_env.sh      # 安装 JDK、Android SDK、替换 ARM64 AAPT2
./gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

---

## 颜色方案

| 角色 | 浅色 | 深色 |
|---|---|---|
| primary | `#1976D2` | `#9ECAFF` |
| primaryContainer | `#D1E4FF` | `#00497D` |
| background / surface | `#FDFCFF` | `#1A1C1E` |
| secondary | `#535F70` | `#BAC8DC` |
| error | `#BA1A1A` | `#FFB4AB` |

Android 12+ 设备默认启用 Material You 动态取色；如需固定蓝色品牌色，在 `AndroidStarterTheme(dynamicColor = false)` 处传入 `false`。

---

## ARM64 AAPT2 说明

`tools/aapt2/aapt2-arm64-v8a` 来源：
- Release: https://github.com/ReVanced/aapt2/releases/tag/v1.0.0
- SHA-256: `e5b5ff7f0d4f6ecd7fa5d05d77fed3f09f6f1bf80f078b8aada82bc578848561`

`app/build.gradle.kts` 中的 `resolutionStrategy` 仅在 **Linux aarch64** 主机上激活，GitHub Actions 的 `ubuntu-latest`（x86_64）不受影响。

---

## 相关资源

- [Jetpack Compose 文档](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
- [Navigation Compose](https://developer.android.com/guide/navigation/design)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
