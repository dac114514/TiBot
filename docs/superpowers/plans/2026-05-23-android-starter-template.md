# Android Starter Template 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有 Android Starter 重写为控件展示模板，亮蓝色主题 + 笔记 App 卡片设计风格

**Architecture:** 底部三 Tab（组件/数据展示/设置），各 Tab 用 verticalScroll 布局 + SettingsCard 排列控件，DataStore 持久化主题偏好，ViewModel + StateFlow 管理状态

**Tech Stack:** Jetpack Compose + Material 3 + Navigation Compose + DataStore Preferences

---

## 文件变更清单

### 修改的文件
| 文件 | 操作 |
|------|------|
| `app/src/main/java/com/example/androidstarter/ui/theme/Color.kt` | 重写为亮蓝色色板 |
| `app/src/main/java/com/example/androidstarter/ui/theme/Theme.kt` | 更新颜色引用 |
| `app/src/main/java/com/example/androidstarter/MainActivity.kt` | 更新 Tab 定义 |
| `app/src/main/java/com/example/androidstarter/ui/navigation/Routes.kt` | 更新路由 |
| `app/src/main/java/com/example/androidstarter/ui/navigation/AppNavHost.kt` | 更新 NavHost |
| `app/src/main/java/com/example/androidstarter/ui/settings/SettingsScreen.kt` | 重写为新卡片风格 |
| `app/src/main/java/com/example/androidstarter/ui/settings/SettingsViewModel.kt` | 保持不变 |

### 新建的文件
| 文件 | 职责 |
|------|------|
| `ui/components/SettingsCard.kt` | 通用卡片组件（图标+标题+内容） |
| `ui/components/ExpandableSettingsCard.kt` | 可展开卡片组件 |
| `ui/widget/WidgetScreen.kt` | 组件 Tab - 控件展示 |
| `ui/display/DisplayScreen.kt` | 数据展示 Tab |

### 删除的文件
| 文件 | 理由 |
|------|------|
| `ui/home/HomeScreen.kt` | 替换为 WidgetScreen |
| `ui/home/HomeViewModel.kt` | 无业务逻辑 |
| `ui/list/ListScreen.kt` | 替换为 DisplayScreen |
| `ui/list/ListViewModel.kt` | 无业务逻辑 |
| `ui/components/ItemCard.kt` | 旧卡片样式废弃 |
| `ui/components/SectionHeader.kt` | 不再使用 |
| `data/model/SampleItem.kt` | 无业务逻辑 |
| `data/repository/SampleRepository.kt` | 无业务逻辑 |

---

### Task 1: 亮蓝色主题色板

**Files:**
- Modify: `app/src/main/java/com/example/androidstarter/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/example/androidstarter/ui/theme/Theme.kt`

- [ ] **Step 1: 重写 Color.kt 为亮蓝色色板**

```kotlin
package com.example.androidstarter.ui.theme

import androidx.compose.ui.graphics.Color

// ===== LIGHT MODE =====
val BluePrimary = Color(0xFF2962FF)        // 亮蓝色 primary
val OnBluePrimary = Color(0xFFFFFFFF)
val BluePrimaryContainer = Color(0xFFD6E3FF)
val OnBluePrimaryContainer = Color(0xFF001B3E)
val BlueSecondary = Color(0xFF535F70)
val OnBlueSecondary = Color(0xFFFFFFFF)
val BlueSecondaryContainer = Color(0xFFD7E3F7)
val OnBlueSecondaryContainer = Color(0xFF101C2B)
val BlueBackground = Color(0xFFFDFBFF)
val OnBlueBackground = Color(0xFF1A1C1E)
val BlueSurface = Color(0xFFFDFBFF)
val OnBlueSurface = Color(0xFF1A1C1E)
val BlueSurfaceVariant = Color(0xFFE0E2EC)
val OnBlueSurfaceVariant = Color(0xFF44474E)
val BlueOutline = Color(0xFF74777F)
val BlueError = Color(0xFFBA1A1A)
val OnBlueError = Color(0xFFFFFFFF)

// ===== DARK MODE =====
val BluePrimaryDark = Color(0xFFB0C5FF)
val OnBluePrimaryDark = Color(0xFF002D6E)
val BluePrimaryContainerDark = Color(0xFF004296)
val OnBluePrimaryContainerDark = Color(0xFFD6E3FF)
val BlueSecondaryDark = Color(0xFFBAC8DC)
val OnBlueSecondaryDark = Color(0xFF253140)
val BlueSecondaryContainerDark = Color(0xFF3B4858)
val OnBlueSecondaryContainerDark = Color(0xFFD7E3F7)
val BlueBackgroundDark = Color(0xFF1A1C1E)
val OnBlueBackgroundDark = Color(0xFFE2E2E6)
val BlueSurfaceDark = Color(0xFF1A1C1E)
val OnBlueSurfaceDark = Color(0xFFE2E2E6)
val BlueSurfaceVariantDark = Color(0xFF44474E)
val OnBlueSurfaceVariantDark = Color(0xFFC4C6D0)
val BlueOutlineDark = Color(0xFF8E9099)
val BlueErrorDark = Color(0xFFFFB4AB)
val OnBlueErrorDark = Color(0xFF690005)
```

- [ ] **Step 2: 更新 Theme.kt 使用新色板**

```kotlin
package com.example.androidstarter.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = OnBluePrimary,
    primaryContainer = BluePrimaryContainer,
    onPrimaryContainer = OnBluePrimaryContainer,
    secondary = BlueSecondary,
    onSecondary = OnBlueSecondary,
    secondaryContainer = BlueSecondaryContainer,
    onSecondaryContainer = OnBlueSecondaryContainer,
    background = BlueBackground,
    onBackground = OnBlueBackground,
    surface = BlueSurface,
    onSurface = OnBlueSurface,
    surfaceVariant = BlueSurfaceVariant,
    onSurfaceVariant = OnBlueSurfaceVariant,
    outline = BlueOutline,
    error = BlueError,
    onError = OnBlueError,
)

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = OnBluePrimaryDark,
    primaryContainer = BluePrimaryContainerDark,
    onPrimaryContainer = OnBluePrimaryContainerDark,
    secondary = BlueSecondaryDark,
    onSecondary = OnBlueSecondaryDark,
    secondaryContainer = BlueSecondaryContainerDark,
    onSecondaryContainer = OnBlueSecondaryContainerDark,
    background = BlueBackgroundDark,
    onBackground = OnBlueBackgroundDark,
    surface = BlueSurfaceDark,
    onSurface = OnBlueSurfaceDark,
    surfaceVariant = BlueSurfaceVariantDark,
    onSurfaceVariant = OnBlueSurfaceVariantDark,
    outline = BlueOutlineDark,
    error = BlueErrorDark,
    onError = OnBlueErrorDark,
)

@Composable
fun AndroidStarterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
```

---

### Task 2: 卡片组件

**Files:**
- Create: `app/src/main/java/com/example/androidstarter/ui/components/SettingsCard.kt`
- Create: `app/src/main/java/com/example/androidstarter/ui/components/ExpandableSettingsCard.kt`

- [ ] **Step 1: 创建 SettingsCard.kt**

```kotlin
package com.example.androidstarter.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable Column.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
```

- [ ] **Step 2: 创建 ExpandableSettingsCard.kt**

```kotlin
package com.example.androidstarter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ExpandableSettingsCard(
    icon: ImageVector,
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable Column.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider()
                    content()
                }
            }
        }
    }
}
```

---

### Task 3: 更新导航结构

**Files:**
- Modify: `app/src/main/java/com/example/androidstarter/ui/navigation/Routes.kt`
- Modify: `app/src/main/java/com/example/androidstarter/ui/navigation/AppNavHost.kt`

- [ ] **Step 1: 更新 Routes.kt**

```kotlin
package com.example.androidstarter.ui.navigation

object Routes {
    const val WIDGET = "widget"
    const val DISPLAY = "display"
    const val SETTINGS = "settings"
}
```

- [ ] **Step 2: 更新 AppNavHost.kt**

```kotlin
package com.example.androidstarter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.androidstarter.ui.display.DisplayScreen
import com.example.androidstarter.ui.settings.SettingsScreen
import com.example.androidstarter.ui.widget.WidgetScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.WIDGET,
        modifier = modifier,
    ) {
        composable(Routes.WIDGET) { WidgetScreen() }
        composable(Routes.DISPLAY) { DisplayScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
```

---

### Task 4: 组件 Tab — WidgetScreen

**Files:**
- Create: `app/src/main/java/com/example/androidstarter/ui/widget/WidgetScreen.kt`

- [ ] **Step 1: 创建 WidgetScreen.kt**

```kotlin
package com.example.androidstarter.ui.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidstarter.ui.components.SettingsCard

@Composable
fun WidgetScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // 按钮
        var fabVisible by remember { mutableStateOf(false) }
        SettingsCard(icon = Icons.Filled.TouchApp, title = "按钮") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { }) { Text("Filled") }
                FilledTonalButton(onClick = { }) { Text("Tonal") }
                OutlinedButton(onClick = { }) { Text("Outlined") }
                TextButton(onClick = { }) { Text("Text") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { fabVisible = !fabVisible }) {
                    androidx.compose.material.icons.Icons.Filled.Favorite
                }
                IconButton(onClick = { }) {
                    androidx.compose.material.icons.Icons.Filled.CheckCircle
                }
                IconButton(onClick = { }) {
                    androidx.compose.material.icons.Icons.Filled.AutoAwesome
                }
                Text(
                    text = if (fabVisible) "FAB 已激活" else "IconButton 示例",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 文本输入
        var text by remember { mutableStateOf("") }
        SettingsCard(icon = Icons.Filled.Input, title = "文本输入") {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("输入内容") },
                placeholder = { Text("在这里打字...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))

        // 开关
        var switchChecked by remember { mutableStateOf(false) }
        SettingsCard(icon = Icons.Filled.ToggleOn, title = "开关") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Wi-Fi", modifier = Modifier.weight(1f))
                Switch(checked = switchChecked, onCheckedChange = { switchChecked = it })
            }
        }

        Spacer(Modifier.height(12.dp))

        // 复选框
        var checkA by remember { mutableStateOf(false) }
        var checkB by remember { mutableStateOf(true) }
        var checkC by remember { mutableStateOf(false) }
        SettingsCard(icon = Icons.Filled.CheckCircle, title = "复选框") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checkA, onCheckedChange = { checkA = it })
                Text("选项 A", modifier = Modifier.padding(start = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checkB, onCheckedChange = { checkB = it })
                Text("选项 B", modifier = Modifier.padding(start = 4.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checkC, onCheckedChange = { checkC = it })
                Text("选项 C", modifier = Modifier.padding(start = 4.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        // 单选按钮
        var selectedRadio by remember { mutableStateOf(0) }
        SettingsCard(icon = Icons.Filled.RadioButtonChecked, title = "单选按钮") {
            listOf("小型", "中型", "大型").forEachIndexed { index, label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RadioButton(selected = selectedRadio == index, onClick = { selectedRadio = index })
                    Text(label, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 滑块
        var sliderValue by remember { mutableFloatStateOf(0.5f) }
        SettingsCard(icon = Icons.Filled.Dialpad, title = "滑块") {
            Text(
                text = "数值: ${(sliderValue * 100).toInt()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(value = sliderValue, onValueChange = { sliderValue = it })
        }

        Spacer(Modifier.height(12.dp))

        // 芯片
        var selectedChip by remember { mutableStateOf(false) }
        SettingsCard(icon = Icons.Filled.AutoAwesome, title = "芯片") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { }, label = { Text("辅助") })
                FilterChip(
                    selected = selectedChip,
                    onClick = { selectedChip = !selectedChip },
                    label = { Text("筛选") },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 进度指示器
        SettingsCard(icon = Icons.Filled.Favorite, title = "进度指示器") {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }

        Spacer(Modifier.height(24.dp))
    }
}
```

---

### Task 5: 数据展示 Tab — DisplayScreen

**Files:**
- Create: `app/src/main/java/com/example/androidstarter/ui/display/DisplayScreen.kt`

- [ ] **Step 1: 创建 DisplayScreen.kt**

```kotlin
package com.example.androidstarter.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.androidstarter.ui.components.SettingsCard

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // Typography
        SettingsCard(icon = Icons.Filled.FormatSize, title = "字体排版") {
            val styles = listOf(
                "Display Large" to MaterialTheme.typography.displayLarge,
                "Headline Large" to MaterialTheme.typography.headlineLarge,
                "Title Large" to MaterialTheme.typography.titleLarge,
                "Title Medium" to MaterialTheme.typography.titleMedium,
                "Body Large" to MaterialTheme.typography.bodyLarge,
                "Body Medium" to MaterialTheme.typography.bodyMedium,
                "Label Large" to MaterialTheme.typography.labelLarge,
                "Label Small" to MaterialTheme.typography.labelSmall,
            )
            styles.forEach { (name, style) ->
                Text(
                    text = name,
                    style = style,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 色板
        SettingsCard(icon = Icons.Filled.Brush, title = "颜色色板") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val colors = listOf(
                    "Primary" to MaterialTheme.colorScheme.primary,
                    "Secondary" to MaterialTheme.colorScheme.secondary,
                    "Tertiary" to MaterialTheme.colorScheme.tertiary,
                    "Error" to MaterialTheme.colorScheme.error,
                    "Surface" to MaterialTheme.colorScheme.surface,
                    "Background" to MaterialTheme.colorScheme.background,
                    "Surface\nVariant" to MaterialTheme.colorScheme.surfaceVariant,
                    "Outline" to MaterialTheme.colorScheme.outline,
                )
                colors.forEach { (name, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(color),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 卡片示例
        SettingsCard(icon = Icons.Filled.Dashboard, title = "卡片示例") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Text(
                    text = "这是 primaryContainer 颜色的卡片",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Text(
                    text = "这是 secondaryContainer 颜色的卡片",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 分割线示例
        SettingsCard(icon = Icons.Filled.ListAlt, title = "分割线") {
            Text("上方有分割线的文字", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("下方有分割线的文字", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("再次分割", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(12.dp))

        // 图标网格
        SettingsCard(icon = Icons.Filled.GridView, title = "图标网格") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(
                    Icons.Filled.Favorite to "收藏",
                    Icons.Filled.Home to "首页",
                    Icons.Filled.Settings to "设置",
                    Icons.Filled.ListAlt to "列表",
                    Icons.Filled.Info to "信息",
                    Icons.Filled.Star to "星标",
                ).forEach { (icon, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Icon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier,
) {
    androidx.compose.material3.Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}
```

---

### Task 6: 设置 Tab

**Files:**
- Modify: `app/src/main/java/com/example/androidstarter/ui/settings/SettingsScreen.kt`
- Keep: `app/src/main/java/com/example/androidstarter/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: 重写 SettingsScreen.kt（新卡片风格）**

```kotlin
package com.example.androidstarter.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidstarter.data.local.ThemeMode
import com.example.androidstarter.ui.components.ExpandableSettingsCard
import com.example.androidstarter.ui.components.SettingsCard

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val themeMode by vm.themeMode.collectAsState()
    var aboutExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // 外观
        SettingsCard(icon = Icons.Filled.Palette, title = "外观") {
            ThemeModeOption(
                label = "跟随系统",
                selected = themeMode == ThemeMode.SYSTEM,
                onClick = { vm.setThemeMode(ThemeMode.SYSTEM) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ThemeModeOption(
                label = "浅色",
                selected = themeMode == ThemeMode.LIGHT,
                onClick = { vm.setThemeMode(ThemeMode.LIGHT) },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ThemeModeOption(
                label = "深色",
                selected = themeMode == ThemeMode.DARK,
                onClick = { vm.setThemeMode(ThemeMode.DARK) },
            )
        }

        Spacer(Modifier.height(12.dp))

        // 关于
        ExpandableSettingsCard(
            icon = Icons.Filled.Info,
            title = "关于",
            expanded = aboutExpanded,
            onToggle = { aboutExpanded = !aboutExpanded },
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow("包名", "com.example.androidstarter")
                InfoRow("版本", "1.0 (1)")
                InfoRow("最低 SDK", "Android 7.0 (API 24)")
                InfoRow("目标 SDK", "Android 15 (API 35)")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ThemeModeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
```

---

### Task 7: 更新 MainActivity

**Files:**
- Modify: `app/src/main/java/com/example/androidstarter/MainActivity.kt`

- [ ] **Step 1: 重写 MainActivity.kt**

```kotlin
package com.example.androidstarter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.androidstarter.data.local.ThemeMode
import com.example.androidstarter.ui.navigation.AppNavHost
import com.example.androidstarter.ui.navigation.Routes
import com.example.androidstarter.ui.settings.SettingsViewModel
import com.example.androidstarter.ui.theme.AndroidStarterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}

@Composable
private fun AppRoot(settingsVm: SettingsViewModel = viewModel()) {
    val themeMode by settingsVm.themeMode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    AndroidStarterTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        val tabs = listOf(
            TabItem(Routes.WIDGET, "组件", Icons.Outlined.Widgets, Icons.Filled.Widgets),
            TabItem(Routes.DISPLAY, "数据展示", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
            TabItem(Routes.SETTINGS, "设置", Icons.Outlined.Settings, Icons.Filled.Settings),
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.filled else tab.outlined,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            AppNavHost(
                navController = navController,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

private data class TabItem(
    val route: String,
    val label: String,
    val outlined: ImageVector,
    val filled: ImageVector,
)
```

---

### Task 8: 清理旧文件

**Files:**
- Delete: `app/src/main/java/com/example/androidstarter/ui/home/HomeScreen.kt`
- Delete: `app/src/main/java/com/example/androidstarter/ui/home/HomeViewModel.kt`
- Delete: `app/src/main/java/com/example/androidstarter/ui/list/ListScreen.kt`
- Delete: `app/src/main/java/com/example/androidstarter/ui/list/ListViewModel.kt`
- Delete: `app/src/main/java/com/example/androidstarter/ui/components/ItemCard.kt`
- Delete: `app/src/main/java/com/example/androidstarter/ui/components/SectionHeader.kt`
- Delete: `app/src/main/java/com/example/androidstarter/data/model/SampleItem.kt`
- Delete: `app/src/main/java/com/example/androidstarter/data/repository/SampleRepository.kt`

- [ ] **Step 1: 删除 8 个旧文件**

```bash
rm app/src/main/java/com/example/androidstarter/ui/home/HomeScreen.kt
rm app/src/main/java/com/example/androidstarter/ui/home/HomeViewModel.kt
rm app/src/main/java/com/example/androidstarter/ui/list/ListScreen.kt
rm app/src/main/java/com/example/androidstarter/ui/list/ListViewModel.kt
rm app/src/main/java/com/example/androidstarter/ui/components/ItemCard.kt
rm app/src/main/java/com/example/androidstarter/ui/components/SectionHeader.kt
rm app/src/main/java/com/example/androidstarter/data/model/SampleItem.kt
rm app/src/main/java/com/example/androidstarter/data/repository/SampleRepository.kt
```

---

### 验证步骤

在各 Task 之间，可运行以下命令验证编译：

```bash
cd D:/开发/Android开发/标准安卓样本/
./gradlew assembleDebug 2>&1 | tail -30
```

期望结果：`BUILD SUCCESSFUL`
