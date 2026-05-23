# Android Starter Template — 控件展示模板设计

## 概述

基于现有 Android Starter 项目，重写为纯净的 Android 初始模板，以亮蓝色为主题，使用 Jetpack Compose + Material 3，全部 UI 控件采用笔记 App 设置页（SettingsScreen）的满宽卡片设计风格。

## 架构

### 页面结构

底部三 Tab 导航（NavigationBar + Navigation Compose）：

| Tab | 路由 | 内容 |
|-----|------|------|
| 组件 | `widget` | 交互式控件展示（Buttons, Inputs, Selection, Sliders, Chips, Progress） |
| 数据展示 | `display` | 展示型控件（Typography, Colors, Cards, Lists, Dividers, Icons） |
| 设置 | `settings` | 深色模式、主题色、版本信息 |

### 包结构

```
com.example.androidstarter
├── MainActivity.kt              # 入口，底部导航，主题切换
├── ui/
│   ├── theme/
│   │   ├── Color.kt             # 亮蓝色系色板
│   │   ├── Theme.kt             # Material 3 主题（Light/Dark）
│   │   └── Type.kt              # 字体排版
│   ├── navigation/
│   │   ├── Routes.kt            # 路由常量
│   │   └── AppNavHost.kt        # NavHost 定义
│   ├── components/
│   │   ├── SettingsCard.kt      # 通用卡片（图标 + 标题 + 内容区域）
│   │   └── ExpandableSettingsCard.kt  # 可展开卡片
│   ├── widget/
│   │   ├── WidgetScreen.kt      # 组件 Tab
│   │   └── WidgetViewModel.kt
│   ├── display/
│   │   ├── DisplayScreen.kt     # 数据展示 Tab
│   │   └── DisplayViewModel.kt
│   └── settings/
│       ├── SettingsScreen.kt    # 设置 Tab
│       └── SettingsViewModel.kt
└── data/
    └── local/
        ├── PreferencesRepository.kt  # DataStore 封装
        └── ThemeMode.kt              # 主题模式枚举
```

## 卡片设计（核心视觉模式）

参考 `SettingsScreen.kt`（笔记 App）的满宽卡片样式：

```
┌─────────────────────────────────┐
│ 🔵  标题                   {控件} │  ← Card(elevation=2dp)
└─────────────────────────────────┘
```

- `Card(elevation = CardDefaults.cardElevation(2.dp))`，满宽
- 内部 Row：Icon(primary tint) + 16dp spacing + title + 交互控件
- 底部 12dp 间距分隔卡片
- 可展开的卡片：点击头部展开/收起，AnimatedVisibility 动画
- 卡片内列表项使用 HorizontalDivider 分隔

### 可复用组件

```kotlin
// 基本卡片：图标 + 标题 + 自定义内容
@Composable
fun SettingsCard(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit)

// 可展开卡片
@Composable
fun ExpandableSettingsCard(
    icon: ImageVector, title: String,
    expanded: Boolean, onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
)

// 行条目：图标 + 标签 + > 箭头（用于导航）
@Composable
fun SettingsRowItem(icon: ImageVector, title: String, onClick: () -> Unit)

// 切换行：图标 + 标签 + Switch
@Composable
fun SwitchSettingsRow(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit)
```

## 亮蓝色主题

### Light Mode
| Token | Color |
|-------|-------|
| Primary | `#2979FF` (bright blue) |
| On Primary | `#FFFFFF` |
| Primary Container | `#D6E3FF` |
| On Primary Container | `#001B3E` |
| Secondary | `#535F70` |
| Surface | `#FDFBFF` |
| Background | `#FDFBFF` |
| Error | `#BA1A1A` |

### Dark Mode
| Token | Color |
|-------|-------|
| Primary | `#B0C5FF` |
| On Primary | `#002D6E` |
| Primary Container | `#004296` |
| On Primary Container | `#D6E3FF` |
| Secondary | `#BAC8DC` |
| Surface | `#1A1C1E` |
| Background | `#1A1C1E` |
| Error | `#FFB4AB` |

## Tab 详解

### 组件 Tab（WidgetScreen）

用 `verticalScroll` Column + SettingsCard 排列，每个卡片展示一个控件类型：

1. **按钮** — FilledButton, OutlinedButton, TextButton, IconButton, FAB（一行排列）
2. **文本输入** — OutlinedTextField（带 label），可输入/清除演示
3. **开关** — Switch（带状态切换）
4. **复选框** — Checkbox（选中/未选中/不确定）
5. **单选按钮** — RadioButton 组（3 个选项）
6. **滑块** — Slider（连续 + 离散值显示）
7. **芯片** — AssistChip, FilterChip, InputChip, SuggestionChip
8. **进度指示器** — LinearProgressIndicator, CircularProgressIndicator（带动画切换）

### 数据展示 Tab（DisplayScreen）

1. **Typography** — 所有 text style 预览（displayLarge → labelSmall）
2. **颜色色板** — Primary, Secondary, Tertiary, Error, Surface 的色块展示
3. **卡片示例** — 不同样式的 Card 展示
4. **列表项** — Leading/trailing content 的 ListItem 示例
5. **分割线** — HorizontalDivider 样式

### 设置 Tab（SettingsScreen）

保持现有功能：
1. **外观** — 一个卡片内放三个 RadioButton（跟随系统/浅色/深色）
2. **关于** — 展开式卡片，显示包名、版本、最低 SDK 等信息

## 移除的内容

- `SampleItem`, `SampleRepository` — 无业务逻辑
- `ItemCard.kt`, `SectionHeader.kt` — 旧卡片样式
- `HomeViewModel`, `HomeScreen`, `ListViewModel`, `ListScreen` — 改为新的 Tab 结构
- `Home` Tab 路由 → 改为 `Widget` Tab

## 保留的内容

- `PreferencesRepository`, `ThemeMode` — 主题持久化
- `AndroidStarterTheme`, `Color`, `Type` — 但重写色板
- `Routes`, `AppNavHost` — 但更新路由
- `MainActivity` — 但简化、更新 Tab 定义
- `SettingsViewModel` — 保持 DataStore 主题切换

## 非目标

- 不引入数据库（Room）
- 不引入网络请求（Retrofit/Ktor）
- 不引入 Hilt/Dagger（ViewModel 用 `viewModel()` factory 默认构造）
- 不写业务逻辑
