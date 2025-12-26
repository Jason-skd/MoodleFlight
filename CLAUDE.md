# CLAUDE.md

本文件为 Claude Code 提供项目上下文，请在每次对话开始时阅读。
用户是 **Freshman** ，希望从项目中学习到开发经验，应尽可能给予指导而不是帮他完成代码。

## 项目概述

**MoodleFlight 砺儒云飞行** - 华南师范大学 Moodle (砺儒云课堂) 视频自动观看工具

### 解决的痛点
1. 视频播放完成自动切换下一个视频
2. 断点续连功能（JSON 持久化进度）
3. 用户友好的交互体验
4. 系统化的日志与异常处理

## 技术栈

- **语言**: Kotlin
- **自动化框架**: Playwright
- **构建工具**: Gradle
- **日志框架**: kotlin-logging + Logback

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

## 当前架构（Kotlin 版）

```
src/main/kotlin/
├── main.kt                     # 入口，串联各阶段流程
├── manager/
│   └── Session.kt              # 浏览器会话管理 + Cookie 认证
└── model/
    └── PlanningModel.kt        # 计划阶段：课程选择、视频提取、统计收集

src/main/resources/
└── logback.xml                 # 日志配置：控制台 + 文件双输出

data/
├── plans/<date>/
│   ├── courses.json            # 用户选择的课程
│   └── <课程名>_videos.json     # 各课程的视频列表及统计信息
└── logs/
    └── moodleflight.log        # 日志文件（按日滚动，保留7天）
```

## 核心功能清单

### 计划阶段 (PlanningModel) ✅
- [x] 课程列表获取与用户选择 (chooseCourses)
- [x] 视频链接提取 (fetchVideosFromCourse)
- [x] 视频统计收集 (gatherVideosStatistics - watchSeconds/totalSeconds/finish)
- [x] 断点续连（JSON 持久化 - courses.json, *_videos.json）

### 会话管理 (Session) ✅
- [x] 浏览器启动与反检测
- [x] Cookie 登录与会话管理 (verifyAuth, saveCookies)

### 基础设施 ✅
- [x] 日志系统 (kotlin-logging + Logback，控制台+文件双输出)

### 执行阶段 (待实现)
- [ ] 视频自动播放与进度监控
- [ ] 95% 进度自动切换
- [ ] 异常截屏
- [ ] 进度条显示

## 关键选择器

| 元素 | 选择器 | 用途 |
|------|--------|------|
| 视频总时长 | `.vjs-duration-display` | 格式 "3:03"，需等待加载 |
| 已观看秒数 | `.num-gksc > span` | 整数秒 |
| 观看百分比 | `.num-bfjd > span` | 百分比 |
| 完成状态 | `.tips-completion` | "已完成" 或其他 |

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