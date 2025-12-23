# CLAUDE.md

本文件为 Claude Code 提供项目上下文，请在每次对话开始时阅读。

## 项目概述

**MoodleFlight 砺儒云飞行** - 华南师范大学 Moodle (砺儒云课堂) 视频自动观看工具

### 解决的痛点
1. 视频播放到 95% 时自动切换下一个视频
2. 断点续连功能（JSON 持久化进度）
3. 用户友好的交互体验
4. 系统化的日志与异常处理

## 技术栈

- **语言**: Kotlin
- **自动化框架**: Playwright
- **构建工具**: Gradle

## 重要：参考网站

**遇到任何异常或疑难时，请优先阅读 `TargetWebsite/` 目录中的 HTML 文件，从前端逻辑角度寻找解决方案。**

```
TargetWebsite/
├── 个人主页 _ 砺儒云课堂.html          # 课程列表页，点击「我的课程」获取所有课程
├── 课程： 大学生劳动教育...html         # 课程详情页样例，视频链接以 .mp4 结尾
└── 1劳动价值观的内涵.mp4...html        # 视频播放页样例，需实现播放控制和进度判断
```

### 页面功能说明
| 页面 | 用途 | 关键操作 |
|------|------|----------|
| 个人主页 | 获取课程列表 | 点击「我的课程」按钮 |
| 课程页面 | 提取视频链接 | 匹配 `.mp4` 结尾的链接 |
| 视频页面 | 播放控制 | 播放视频、监控进度、95%时切换 |

## 参考脚本

`fly_vedio_assignment_away/` 目录包含 Python 版参考实现：

- **架构**: BrowserManager / AuthManager / VideoManager 三层设计
- **认证**: Cookie 方式登录
- **视频提取**: URL 模式匹配 `a[href*="{pattern}"]`
- **进度检测**: `.num-gksc > span` 元素获取已观看时长
- **会话保持**: 检测并点击「延长会话」按钮

## 计划架构（Kotlin 版）

```
src/main/kotlin/
├── Main.kt                 # 入口
├── manager/
│   ├── BrowserManager.kt   # 浏览器生命周期
│   ├── AuthManager.kt      # Cookie 认证
│   └── VideoManager.kt     # 视频操作
├── model/
│   └── WatchPlan.kt        # 观看计划数据模型
├── util/
│   ├── Logger.kt           # 日志系统
│   └── Screenshot.kt       # 异常截屏
└── data/
    └── progress.json       # 进度持久化
```

## 核心功能清单

- [ ] 浏览器启动与反检测
- [ ] Cookie 登录与会话管理
- [ ] 课程列表获取与用户选择
- [ ] 视频链接提取
- [ ] 视频自动播放与进度监控
- [ ] 95% 进度自动切换
- [ ] 断点续连（JSON 持久化）
- [ ] 日志系统与异常截屏
- [ ] 进度条显示

## 开发命令

```bash
# 构建项目
./gradlew build

# 运行
./gradlew run

# 测试
./gradlew test
```

## 调试提示

1. **页面元素定位失败** → 查看 TargetWebsite 中对应 HTML 的 DOM 结构
2. **登录状态异常** → 检查 Cookie 格式和有效性
3. **视频进度获取失败** → 查看视频页面 HTML 中的进度元素选择器
4. **会话超时** → 确认「延长会话」按钮的检测逻辑