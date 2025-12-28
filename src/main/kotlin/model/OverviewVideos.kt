package model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import manager.Session
import mu.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

// 实际上，无需传入courses，解耦合
/**
 * 概览视频模块
 * 1. 遍历选择的课程
 * 2. 跳转课程 -> 记录视频概览
 */
class OverviewVideos private constructor
    (val session: Session,val planDir: Path, val courses: Map<String, Course>) {
    /**
     * 遍历所有course，每个course调用overviewACourse
     */
    fun overviewAllCourses()  {
        courses.values.forEach { course ->
            overviewACourse(course)
        }
    }

    /**
     * 遍历一个course的视频，调用持久化
     * 失败截屏
     */
    fun overviewACourse(course: Course) {
        if (planDir.resolve("${course.name}_videos.json").exists()) {
            logger.info { "course ${course.name} already exists, skip"}
            return
        }
        val videos: List<Video>
        val coursePage = session.newPage(course.url)

        try {
            // 检查侧边栏是否已展开（内容是否可见）
            val sidebarContent = coursePage.querySelector("#courseindex")
            if (sidebarContent == null || !sidebarContent.isVisible) {
                // 侧边栏未展开，点击打开
                coursePage.click("button[title='打开课程索引']")
            }

            // 等待内容加载
            coursePage.waitForSelector("#courseindex a.courseindex-link")

            // 只从侧边栏获取链接（#courseindex 限定范围），主页面的链接可能被折叠
            val allLinks = coursePage.querySelectorAll("#courseindex a.courseindex-link")
            videos = allLinks
                .filter { it.textContent().trim().endsWith(".mp4") }
                .map { link ->
                    Video(
                        name = link.textContent().trim(),
                        url = link.getAttribute("href") ?: ""
                    )
                }
        } catch (e: Exception) {
            logger.error(e) { "Error while fetching videos for course $course.name" }
            screenShotUtil(coursePage)
            throw e
        } finally {
            coursePage.close()
        }
        try {
            persistVideos(videos, course)
        } catch (e: Exception) {
            logger.error(e) { "Error while persisting videos for course $course.name" }
            throw e
        }
    }

    /**
     * 持久化视频
     */
    private fun persistVideos(videos: List<Video>, course: Course) {
        val json = Json.encodeToString(videos)
        val videoDir = planDir.resolve("${course.name}_videos.json")  // 要与CourseManager同步更改
        videoDir.writeText(json)
        logger.info { "videos plan persisted to $videoDir." }
    }

    companion object {
        val logger = KotlinLogging.logger {}
        /**
         * 创建OverviewVideos实例
         */
        fun create(session: Session, planDir: Path): Result<OverviewVideos> = runCatching {
            val coursesFile = planDir.resolve("courses.json")
            val coursesMap: Map<String, Course> = Json.decodeFromString(coursesFile.readText())
            OverviewVideos(session, planDir, coursesMap)
        }.onFailure { logger.error(it) { "courses file read or parse error" } }
    }
}