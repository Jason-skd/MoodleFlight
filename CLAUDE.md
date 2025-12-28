# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Note:** The user is a **Freshman** developer who wants to learn from this project. When asked to make code changes, prioritize **teaching and guidance** over simply completing the task for them.

## Project Overview

**MoodleFlight** is a Kotlin-based automation tool for watching videos on South China Normal University's Moodle platform (砺儒云课堂). It automatically plays videos sequentially, supports resume functionality via JSON persistence, handles session management, and provides comprehensive logging.

### Key Pain Points Solved
1. Automatic video switching when current video completes
2. Resume capability (progress persisted to JSON files)
3. User-friendly CLI interaction
4. Structured logging and exception handling with screenshots

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 2.2.21 |
| Build Tool | Gradle | (via gradlew) |
| Automation | Playwright | 1.49.0 |
| Coroutines | kotlinx-coroutines-core | 1.9.0 |
| Serialization | kotlinx-serialization-json | 1.7.3 |
| Logging | kotlin-logging + Logback | 3.0.5 / 1.5.13 |
| JVM Target | Java | 21 |

## Build Commands

```bash
# Build the project
./gradlew build

# Run the application
./gradlew run

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean

# Create JAR
./gradlew jar
```

## Architecture: Four-Phase Workflow

The application follows a **planning-then-execution** model with distinct phases:

```
┌─────────────────────────────────────────────────────────────┐
│                    MAIN ENTRY POINT (main.kt)               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  SESSION SETUP (Session.kt)                                 │
│  ├─ Browser detection (Chrome/Edge/Firefox)                   │
│  ├─ Auth token validation/renewal                            │
│  └─ Context creation with storage state                      │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  PHASE 1: COURSE DISCOVERY (DiscoverCourse.kt)              │
│  ├─ Navigate to moodle.scnu.edu.cn/my/                       │
│  ├─ Click "我的课程" (My Courses)                            │
│  ├─ Extract all course names and URLs                        │
│  └─ User selects courses → Persist to courses.json           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  PHASE 2: VIDEO OVERVIEW (OverviewVideos.kt)                │
│  ├─ For each selected course:                                │
│  ├─ Navigate to course page                                  │
│  ├─ Extract .mp4 links from sidebar                          │
│  └─ Persist to <course>_videos.json (basic info)             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  PHASE 3: STATISTICS GATHERING (GatherVideoStatics.kt)      │
│  ├─ For each video:                                           │
│  ├─ Navigate to video page                                    │
│  ├─ Parse total duration (.vjs-duration-display)              │
│  ├─ Get watched seconds (.num-gksc > span)                    │
│  ├─ Check completion status (.tips-completion)                │
│  └─ Update <course>_videos.json with statistics               │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│  PHASE 4: EXECUTION (executeCourse.kt)                       │
│  ├─ For each unfinished course:                               │
│  ├─ While next video exists:                                  │
│  │   ├─ VideoManager.playUntilFinished()                      │
│  │   ├─ Click play button                                     │
│  │   ├─ Monitor progress every 15s                            │
│  │   ├─ Handle "延长会话" (session extension)                 │
│  │   └─ Check completion status                               │
│  └─ Mark course finished when all videos done                 │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
src/main/kotlin/
├── main.kt                         # Entry point
├── manager/
│   ├── Session.kt                  # Browser session & auth management
│   ├── Config.kt                   # Browser detection & configuration
│   ├── CourseManager.kt            # Course progress tracking
│   └── VideoManager.kt             # Video playback control
└── model/
    ├── PlanningModel.kt            # Data classes (Course, Video)
    ├── DiscoverCourse.kt           # Course discovery worker
    ├── OverviewVideos.kt           # Video link extraction
    ├── GatherVideoStatics.kt       # Video statistics collection
    ├── executeCourse.kt            # Execution entry point
    ├── isFinishedSchedule.kt       # Completion detection utility
    └── screenShotUtil.kt           # Screenshot utility

src/main/resources/
└── logback.xml                     # Logging configuration

data/
├── authToken.json                  # Saved authentication token
├── config.json                     # Browser configuration
├── plans/<date>/
│   ├── courses.json                # Selected courses
│   └── <course>_videos.json        # Video lists with statistics
├── logs/
│   └── moodleflight.log           # Application logs (7-day retention)
└── screenshots/                    # Error screenshots
```

## Component Responsibilities

| Class | Responsibility | Input | Output |
|---|---|---|---|
| Session | Browser lifecycle, authentication | - | authToken.json |
| Config | Browser detection, user preference | - | config.json |
| DiscoverCourse | Get course list, user selection | - | courses.json |
| OverviewVideos | Extract video links from courses | courses.json | *_videos.json (basic info) |
| GatherVideoStatics | Collect video statistics | *_videos.json | *_videos.json (with stats) |
| CourseManager | Track course progress | courses.json | courses.json (currentPlaying) |
| VideoManager | Single video playback control | Video | *_videos.json (watchSeconds, isFinished) |
| screenShotUtil | Capture screenshots on errors | Page | PNG file |

## Data Flow: JSON Files as Contracts

The system uses JSON files as the primary data contract between components:

### 1. courses.json - Course Registry
```json
{
  "courseId": {
    "name": "Course Name",
    "url": "https://...",
    "id": "123",
    "isFinished": false,
    "currentPlaying": 0
  }
}
```

### 2. &lt;course&gt;_videos.json - Video Registry per Course
```json
[
  {
    "name": "Video Name",
    "url": "https://...mp4",
    "watchSeconds": 45,
    "totalSeconds": 183,
    "isFinished": false
  }
]
```

### 3. authToken.json - Session Persistence
- Playwright storage state (cookies, localStorage)
- Enables skipping login on subsequent runs

### Resume Capability

The system can resume from any point:
- Skip course discovery if `courses.json` exists
- Skip video overview if `<course>_videos.json` exists
- Skip video statistics if already gathered
- Resume playback from last `currentPlaying` index

## Key CSS Selectors (Moodle Platform)

| Element | CSS Selector | Purpose |
|---------|--------------|---------|
| My Courses button | `li.mycourse` | Navigate to course list |
| Course links | `a[href*='/course/view.php']` | Extract course URLs |
| Video links | `a.courseindex-link` (ends with .mp4) | Find videos in sidebar |
| Play button | `.vjs-big-play-button` | Start video playback |
| Duration | `.vjs-duration-display` | Total video length (format: "3:03") |
| Watched seconds | `.num-gksc > span` | Progress in seconds |
| Watched percent | `.num-bfjd > span` | Progress percentage |
| Completion status | `.tips-completion` | "已完成" text |
| Session extension | `button[aria-label="延长会话"]` | Extend login session |
| Error display | `.vjs-error-display` | Video playback errors (e.g., H.264 codec) |

## Running the Application

### First Run
1. Browser selection prompt (Chrome/Edge/Firefox)
2. Login via browser window (manual)
3. Course selection prompt (enter numbers, comma-separated)
4. Automatic video watching begins

### Subsequent Runs
- Uses saved auth token (auto-login)
- Resumes from last position
- Only processes unfinished videos

## Logging System

**Configuration:** `src/main/resources/logback.xml`

**Dual Output:**
- Console: Pattern `%d{HH:mm:ss} [%level] %logger{20} - %msg%n`
- File: `data/logs/moodleflight.log` with daily rolling, 7-day retention

**Log Levels:**
- TRACE: Detailed debugging (progress checks, state updates)
- INFO: Major operations (login, course discovery, video completion)
- WARN: Non-critical issues (missing elements, cookie issues)
- ERROR: Failures (playback errors, screenshot captures)

## Important: Reference HTML Files

**When encountering any exception or issue, prioritize reading HTML files in `TargetWebsite/` to understand the frontend logic and find solutions.**

```
TargetWebsite/
├── 个人主页 _ 砺儒云课堂.html          # Course list page - click "我的课程"
├── 课程： 大学生劳动教育...html         # Course detail page - video links end with .mp4
└── 1劳动价值观的内涵.mp4...html        # Video playback page - play control and progress
```

### Page Function Reference
| Page | Purpose | Key Operations |
|------|---------|----------------|
| Personal Home | Get course list | Click "我的课程" button |
| Course Page | Extract video links | Match links ending with `.mp4` |
| Video Page | Playback control | Play video, monitor progress, switch at 95% |

## Python Reference Implementation

Located in `fly_vedio_assignment_away/`

**Architecture:**
- `BrowserManager` - Browser lifecycle
- `AuthManager` - Cookie-based authentication
- `VideoManager` - Video extraction and playback
- Uses `playwright.async_api` with async/await

**Key Differences from Kotlin Version:**
- No planning phase (direct execution)
- Single-threaded async vs multi-worker design
- Cookie file format (requires conversion via `cookie_fix.py`)
- No persistent progress tracking

## Debugging Tips

| Issue | Action |
|-------|--------|
| **Page element location fails** | Check DOM structure in corresponding HTML file in `TargetWebsite/` |
| **Login state abnormal** | Check Cookie format and validity in `authToken.json` |
| **Video progress retrieval fails** | Check progress element selectors in video page HTML |
| **Session timeout** | Verify "延长会话" button detection logic |
| **H.264 codec error (Error Code 4)** | Check `.vjs-error-display` element in `GatherVideoStatics` |

## Known Issues

1. **Error Code 4 (H.264 codec)**: Chromium may lack H.264 codec; needs `.vjs-error-display` detection in `GatherVideoStatics`
2. **Browser compatibility**: May need to configure Edge manually on some systems via `setChannel("msedge")`
3. **No frontend**: Currently CLI-only; Ktor/Compose UI planned for future
4. **No tests**: Test directory exists but no test files implemented

## Future Roadmap

### Phase 1: Class Refactoring ✅ COMPLETED
- [x] Split `PlanningModel` into three worker classes:
  - `DiscoverCourse` - Get course list, user selection
  - `OverviewVideos` - Extract video links
  - `GatherVideoStatics` - Collect video statistics
- [x] Use JSON files as contracts between classes

### Phase 2: Frontend/Backend Separation (Ktor Exercise)
- [ ] Introduce Ktor Server as backend
- [ ] Design REST API:
  - `GET /courses` - Get course list
  - `POST /plan` - Create playback plan
  - `GET /progress` - Get playback progress
  - `WebSocket /live-progress` - Real-time progress push
- [ ] Introduce KMP Compose as desktop frontend (target: Windows/macOS)

### Phase 3: Python Concurrency Optimization
- [ ] Rewrite `GatherVideoStatics` in Python with asyncio
- [ ] Package Python as binary with PyInstaller
- [ ] Ktor backend calls Python subprocess via `ProcessBuilder`

### Phase 4: Distribution Packaging
- [ ] Gradle multi-module structure:
  ```
  MoodleFlight/
  ├── core/                # Kotlin core logic (shared)
  ├── server/              # Ktor backend
  ├── desktop/             # KMP Compose frontend
  └── python-gatherer/     # Python video statistics gatherer
  ```
- [ ] Package backend as Shadow JAR
- [ ] Frontend auto-starts backend subprocess
- [ ] Final package as single installer (.dmg / .msi)
- [ ] GitHub Actions CI/CD cross-platform packaging

### Planned Architecture

```
┌─────────────────────────────────────────────┐
│         Installer (single .dmg / .exe)      │
│  ┌─────────────────────────────────────────┐ │
│  │        Frontend (KMP Compose)           │ │
│  │                  │                      │ │
│  │            Start subprocess             │ │
│  │                  ▼                      │ │
│  │  ┌─────────────────────────────────────┐│ │
│  │  │    Backend (Ktor Server JAR)        ││ │
│  │  │         localhost:8080              ││ │
│  │  │              │                      ││ │
│  │  │        ProcessBuilder               ││ │
│  │  │              ▼                      ││ │
│  │  │     Python binary (concurrent)      ││ │
│  │  └─────────────────────────────────────┘│ │
│  └─────────────────────────────────────────┘ │
└─────────────────────────────────────────────┘
```

### Communication Flow

```
User → KMP Frontend → HTTP → Ktor Backend → ProcessBuilder → Python
                                ↑                           │
                                └─────── JSON files ←───────┘
```