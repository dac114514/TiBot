# Verification Before Completion (TiBot 定制版)

> 在原版 superpowers/verification-before-completion 基础上, 叠加 TiBot 项目规则

## TiBot 项目背景
- 仓库: github.com/dac114514/TiBot
- 当前版本: code=37, name="2.2.2"
- P1.5 失败教训: opus 报告 done 时没真实验证修复是否生效, 用户实测"几乎都没修好"
- → 本 skill 强制: "报告 done" 之前, 必须走完 Android 专属验证清单

## TiBot 化改动点 vs 原版
1. **Android 验证清单** (新增, 替换通用验证步骤)
2. **version bump 检查** (CLAUDE.md 硬性规则, 必查)
3. **不放过残留** (新增检查项: 上一轮 plan 的所有 issue 是否都解决了)
4. **强引用 android-review subagent** 做最终审查

---

---
name: verification-before-completion
description: Use when about to claim work is complete, fixed, or passing, before committing or creating PRs - requires running verification commands and confirming output before making any success claims; evidence before assertions always
---

# Verification Before Completion

## Overview

Claiming work is complete without verification is dishonesty, not efficiency.

**Core principle:** Evidence before claims, always.

**Violating the letter of this rule is violating the spirit of this rule.**

## The Iron Law

```
NO COMPLETION CLAIMS WITHOUT FRESH VERIFICATION EVIDENCE
```

If you haven't run the verification command in this message, you cannot claim it passes.

## The Gate Function

```
BEFORE claiming any status or expressing satisfaction:

1. IDENTIFY: What command proves this claim?
2. RUN: Execute the FULL command (fresh, complete)
3. READ: Full output, check exit code, count failures
4. VERIFY: Does output confirm the claim?
   - If NO: State actual status with evidence
   - If YES: State claim WITH evidence
5. ONLY THEN: Make the claim

Skip any step = lying, not verifying
```

## Common Failures

| Claim | Requires | Not Sufficient |
|-------|----------|----------------|
| Tests pass | Test command output: 0 failures | Previous run, "should pass" |
| Linter clean | Linter output: 0 errors | Partial check, extrapolation |
| Build succeeds | Build command: exit 0 | Linter passing, logs look good |
| Bug fixed | Test original symptom: passes | Code changed, assumed fixed |
| Regression test works | Red-green cycle verified | Test passes once |
| Agent completed | VCS diff shows changes | Agent reports "success" |
| Requirements met | Line-by-line checklist | Tests passing |

## Red Flags - STOP

- Using "should", "probably", "seems to"
- Expressing satisfaction before verification ("Great!", "Perfect!", "Done!", etc.)
- About to commit/push/PR without verification
- Trusting agent success reports
- Relying on partial verification
- Thinking "just this once"
- Tired and wanting work over
- **ANY wording implying success without having run verification**

## Rationalization Prevention

| Excuse | Reality |
|--------|---------|
| "Should work now" | RUN the verification |
| "I'm confident" | Confidence ≠ evidence |
| "Just this once" | No exceptions |
| "Linter passed" | Linter ≠ compiler |
| "Agent said success" | Verify independently |
| "I'm tired" | Exhaustion ≠ excuse |
| "Partial check is enough" | Partial proves nothing |
| "Different words so rule doesn't apply" | Spirit over letter |

## Key Patterns

**Tests:**
```
✅ [Run test command] [See: 34/34 pass] "All tests pass"
❌ "Should pass now" / "Looks correct"
```

**Regression tests (TDD Red-Green):**
```
✅ Write → Run (pass) → Revert fix → Run (MUST FAIL) → Restore → Run (pass)
❌ "I've written a regression test" (without red-green verification)
```

**Build:**
```
✅ [Run build] [See: exit 0] "Build passes"
❌ "Linter passed" (linter doesn't check compilation)
```

**Requirements:**
```
✅ Re-read plan → Create checklist → Verify each → Report gaps or completion
❌ "Tests pass, phase complete"
```

**Agent delegation:**
```
✅ Agent reports success → Check VCS diff → Verify changes → Report actual state
❌ Trust agent report
```

## Why This Matters

From 24 failure memories:
- your human partner said "I don't believe you" - trust broken
- Undefined functions shipped - would crash
- Missing requirements shipped - incomplete features
- Time wasted on false completion → redirect → rework
- Violates: "Honesty is a core value. If you lie, you'll be replaced."

## When To Apply

**ALWAYS before:**
- ANY variation of success/completion claims
- ANY expression of satisfaction
- ANY positive statement about work state
- Committing, PR creation, task completion
- Moving to next task
- Delegating to agents

**Rule applies to:**
- Exact phrases
- Paraphrases and synonyms
- Implications of success
- ANY communication suggesting completion/correctness

## The Bottom Line

**No shortcuts for verification.**

Run the command. Read the output. THEN claim the result.

This is non-negotiable.

---

## Android 验证清单（TiBot 强制）

报告 "done" 之前, 逐项勾选:

- [ ] **Lint**: `./gradlew lint` 跑过 (本地不跑, 确认 GitHub Actions lint step 绿)
- [ ] **单元测试**: `./gradlew test` 跑过 (同上, 看 CI status)
- [ ] **Compose UI test** (如有 UI 改动): `./gradlew connectedAndroidTest` 跑过
- [ ] **CI 状态绿**: GitHub Actions 最新 run 是 ✅
- [ ] **Version bump**: app/build.gradle.kts 的 versionCode 已递增, versionName 已更新
- [ ] **回归测试**: 加了 test 防 bug 复发 (针对修过的 bug)
- [ ] **没有残留 issue**: 上一轮 plan 列的所有 issue, 都有对应的 commit 修复
- [ ] **android-review 已通过**: subagent 复查报告是 "可合并"

## 报告 done 模板

```
## ✅ Done

**改动**:
- <文件 1>: <说明>
- <文件 2>: <说明>

**验证**:
- [x] Lint: CI run #xxxx ✅
- [x] Unit test: CI run #xxxx ✅
- [x] CI status: ✅
- [x] Version bump: code 37→38, name 2.2.2→2.2.3
- [x] android-review: 通过

**commit**: <SHA>
**APK**: <下载链接 (rolling release)>

**残留 issue**: 无 / <列出仍未解决的, 说明原因>
```

## 与项目 agent 互调

- **本 skill 由 `orchestrator` agent 收尾阶段触发** (或 android-coder 自检时触发)
- **最终审查** → dispatch `android-review` subagent (必走, 不可省)
- **CI 状态查询** → dispatch `android-build` subagent
- **报告 done 后** → 由 `orchestrator` 汇总报告给用户
