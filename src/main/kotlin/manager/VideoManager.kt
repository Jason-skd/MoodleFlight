package manager

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.AriaRole
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.Video
import model.isFinishedSchedule
import mu.KotlinLogging
import java.lang.AutoCloseable
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * 核心解决：
 * 1. 导航到视频
 * 2. 播放视频
 * 3. 监控播放进度
 */
class VideoManager(session: Session, private var video: Video, private val videosFile: Path)
    : AutoCloseable {
    private val videoName = video.name
    private val page = session.newPage(video.url)

    // 缓存字段，避免重复访问页面元素
    private var watchedPercent: Double
    private var watchedSeconds: Int
    private var isFinishedCache: Boolean = false

    init {
        val (per, sec) = getWatchedProcess()
        watchedPercent = per
        watchedSeconds = sec
    }

    /**
     * 播放视频直到完成
     * 成功返回更新后的Video，失败抛异常
     * 循环中实时更新内存状态，结束时写入文件
     */
    fun playUntilFinished(): Result<Video> = runCatching {
        handleSessionExtension()
        play()

        while (true) {
            handleSessionExtension()

            if (checkFinished()) {
                break
            }

            getWatchedProcess()
            displayProcess(videoName, watchedPercent)

            updateVideo()  // 每轮更新内存状态（轻量级）

            Thread.sleep(15000)
        }

        updateVideo()
        video
    }.onFailure { e ->
        logger.error(e) { "$videoName: Playback failed" }

        // 失败时截屏
        try {
            val now = LocalDateTime.now()
            page.screenshot(Page.ScreenshotOptions()
                .setPath(Path("data/screenshots/${videoName}_$now.png")))
        } catch (screenshotError: Exception) {
            logger.warn { "Failed to take screenshot: $screenshotError" }
        }
    }.also {
        updateVideo()
        // 无论成功失败，只写一次文件
        persistVideo()
    }

    /**
     * 点击播放按钮
     * 失败抛异常（关键操作）
     */
    private fun play() {
        try {
            page.waitForSelector(".vjs-big-play-button")
            page.click("button.vjs-big-play-button")
            logger.info { "$videoName: Play button clicked" }
        } catch (e: Exception) {
            logger.error(e) { "$videoName: Failed to click play button" }
            throw e
        }
    }

    /**
     * 获取当前播放进度并更新缓存
     * 失败返回默认值（非关键操作）
     * @return Pair(观看百分比, 已播放秒数)
     */
    private fun getWatchedProcess(): Pair<Double, Int> {
        val percent = page.querySelector(".num-bfjd > span")
            ?.textContent()
            ?.removeSuffix("%")
            ?.toDoubleOrNull()

        if (percent == null) {
            logger.warn { "$videoName: No watched percent found, using 0" }
            watchedPercent = 0.0
        } else {
            watchedPercent = percent
        }

        val seconds = page.querySelector(".num-gksc > span")
            ?.textContent()?.toIntOrNull()

        if (seconds == null) {
            logger.warn { "$videoName: No watched seconds found, using 0" }
            watchedSeconds = 0
        } else {
            watchedSeconds = seconds
        }

        logger.trace { "Getting $videoName progress: $watchedPercent%, ${watchedSeconds}s" }
        return watchedPercent to watchedSeconds
    }

    /**
     * 检查视频是否播放完成并更新缓存
     * 失败抛异常（关键操作）
     */
    private fun checkFinished(): Boolean {
        try {
            isFinishedCache = isFinishedSchedule(page)
            when (isFinishedCache) {
                true -> logger.info { "$videoName: Finished" }
                false -> logger.trace { "$videoName: Not finished" }
            }
            return isFinishedCache
        } catch (e: Exception) {
            logger.error(e) { "$videoName: Failed to check finished status" }
            throw e
        }
    }

    /**
     * 更新内存中的视频状态（轻量级，使用缓存字段）
     */
    private fun updateVideo() {
        try {
            video = video.copy(
                watchSeconds = watchedSeconds,
                isFinished = isFinishedCache
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to update video $videoName" }
        }
    }

    /**
     * 持久化到文件（重量级，只在必要时调用）
     */
    private fun persistVideo() {
        try {
            val videos: List<Video> = Json.decodeFromString(videosFile.readText())
            val updatedVideos = videos.map { if (it == video) video else it }
            videosFile.writeText(Json.encodeToString(updatedVideos))
            logger.trace { "$videoName persisted" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to persist $videoName" }
        }
    }

    /**
     * 处理会话延长（消化所有错误）
     */
    private fun handleSessionExtension() {
        try {
            val button = page.getByRole(
                AriaRole.BUTTON,
                Page.GetByRoleOptions().setName("延长会话")
            )
            if (button.count() > 0) {
                button.click()
                logger.info { "Session extended" }
            }
        } catch (_: Exception) {
            // 静默忽略，会话延长失败不影响播放
            logger.trace { "No session extension button" }
        }
    }

    override fun close() {
        logger.info { "$videoName: Page closed" }
        page.close()
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun displayProcess(videoName: String, percent: Double) {
    // 后端用，前端换接口
    println("$videoName watching percent: $percent %")
}