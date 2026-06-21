# Finishing a Development Branch (TiBot 定制版)

> **完全重写**: TiBot 不开 worktree, 不开 PR, 直接 push main, 触发 rolling release。
> 原版 superpowers/finishing-a-development-branch 不适用。

## TiBot 项目背景
- 仓库: github.com/dac114514/TiBot
- CLAUDE.md 硬性规则:
  1. **禁止本地 gradle 构建**
  2. **开发直接在 main 分支** (不开 worktree)
  3. **完成后直接推送到 main** (不开 PR)
  4. **每次修复递增 versionCode + 更新 versionName**
- GitHub Actions 自动: CI + 上传 APK + 创建/更新 rolling release (tag: latest)
- 当前版本: code=37, name="2.2.2"

## TiBot 化流程（完全替换原版）

### 1. 前置条件
- [ ] 实施完成 (android-coder 写完所有代码)
- [ ] android-review 已通过
- [ ] invoke `superpowers/verification-before-completion` (TiBot 化版本) 全过
- [ ] version bump 已做 (见下)

### 2. version bump（必做, CLAUDE.md 规则）

编辑 `app/build.gradle.kts`:
- `versionCode`: 当前 +1
- `versionName`: 语义化
  - 小修: 2.2.2 → 2.2.3
  - 功能: 2.2.x → 2.3.0
  - 大改: 2.x.x → 3.0.0

示例 commit:
```bash
git add app/build.gradle.kts
git commit -m "bump: versionCode 37→38, versionName 2.2.2→2.2.3"
```

### 3. 推送 main（必做, CLAUDE.md 规则）

```bash
git push origin main
```

**不要**开 PR, **不要**merge branch, **不要**用 gh pr create。

### 4. GitHub Actions 自动（无需手动）

push main 后, `.github/workflows/android.yml` 自动跑:
- `./gradlew assembleDebug --no-daemon --stacktrace`
- 上传 APK 到 workflow artifacts
- 自动更新 rolling release (tag: `latest`)

### 5. 验证 CI 绿

```bash
gh run list --repo dac114514/TiBot --limit 3
```

Expected: 最新 run 是 ✅ success

### 6. 报告用户

```
## ✅ Done & 推送

**commit**: <SHA>
**version**: code 37→38, name 2.2.2→2.2.3
**CI**: ✅ run #<id>
**APK 下载**: https://github.com/dac114514/TiBot/releases/tag/latest

**改动文件**:
- <文件 1>: <说明>
- ...

**注意事项**: <如有, e.g., 已知问题、后续待办>
```

## 与项目 agent 互调

- **整体收尾流程由 `orchestrator` 协调**: orchestrator 调度 android-coder (实施) → android-review (审查) → android-build (CI 监控), 本 skill 由 orchestrator 触发
- **version bump 检查** → invoke `superpowers/verification-before-completion` (TiBot 化版本) 强制
- **CI 监控** → dispatch `android-build` subagent
- **不需要**: 任何 worktree / PR 相关 agent 调度
- **最终审查** → dispatch `android-review` 确认改动质量
