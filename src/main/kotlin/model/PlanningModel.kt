package model

import com.microsoft.playwright.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import kotlin.io.path.*

import manager.Session
import mu.KotlinLogging
import java.nio.file.Path

/**
 * fetch的课程
 * @param name 课程名称
 * @param url 课程链接
 */
@Serializable
data class Course(
    val name: String,
    val url: String
)

@Serializable
data class Video(
    val name: String,
    val url: String,
    val watchSeconds: Int? =null,
    val totalSeconds: Int? = null,
    val finish: Boolean = false
)

/**
 * 课程规划流
 * @param session 全局session
 */
class PlanningModel(private val session: Session) {
    // moodle主页
    private val myPage: Page = session.newPage("https://moodle.scnu.edu.cn/my/")

    lateinit var chosenCourses: List<Course>

    private val today: LocalDate? = LocalDate.now()
    val planDir = Path("data/plans/$today")
    val coursesDir: Path = planDir.resolve("courses.json")

    init {
        planDir.createDirectories()
    }

    /**
     * 获取课程所有列表
     * 将courses字段初始化
     */
    private fun fetchCourses(): List<Course> {
        myPage.click("li.mycourse")
        myPage.waitForSelector(".mycourse .dropdown-menu a[href*='/course/view.php']")

        val coursesInQuery = myPage.querySelectorAll("li.mycourse .dropdown-menu a[href*='/course/view.php']")
        return coursesInQuery.map { link ->
            Course(
                name = link.textContent().trim(),
                url = link.getAttribute("href") ?: ""
            )
        }
    }

    /**
     * 用用户选择的课程初始化chosenCourses字段
     */
    fun chooseCourses() {
        if (coursesDir.exists()) {
            logger.info { "courses plan exists, reloading..."}
            val json = coursesDir.readText()
            chosenCourses = Json.decodeFromString(json)
        } else {
            val allCourses = fetchCourses()
            chosenCourses = chooseCoursesIO(allCourses).map { allCourses[it] }
            pickleChosenCourses()
        }
    }

    /**
     * 将chosenCourses序列化为json并保存到data/plans/<today>/courses.json
     */
    private fun pickleChosenCourses() {
        val json = Json.encodeToString(chosenCourses)
        coursesDir.writeText(json)
        logger.info { "courses plan pickled to $coursesDir" }
    }

    fun closeMyPage() {
        myPage.close()
    }

    fun planVideos() {
        chosenCourses.forEach { course ->
            val result = planVideosForCourse(course)
            result.onFailure { throw it }
        }
    }

    private fun planVideosForCourse(course: Course): Result<String> {
        if (planDir.resolve("${course.name}_videos.json").exists()) {
            logger.info { "course ${course.name} already exists, skip"}
            return Result.success(course.name)
        }
        val result = runCatching {
            val videos = fetchVideosFromCourse(course)
            pickleVideos(course, videos)
            course.name
        }
        return result
    }

    private fun fetchVideosFromCourse(course: Course): List<Video> {
        val videos: List<Video>
        session.newPage(course.url).use { coursePage ->
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
        }
        return videos
    }

    private fun pickleVideos(course: Course, videos: List<Video>) {
        val json = Json.encodeToString(videos)
        val dir = planDir.resolve("${course.name}_videos.json")
        dir.writeText(json)
        logger.info { "videos plan pickled to $dir." }
    }

    fun gatherVideosStatistics() {
        planDir.listDirectoryEntries("*_videos.json").forEach { file ->
            val videos: List<Video> = Json.decodeFromString(file.readText())
            val results = videos.map { video -> video to gatherAVideo(video) }

            val successCount = results.count { it.second.isSuccess }
            val failCount = results.count { it.second.isFailure }
            logger.info { "video statics in ${file.name} have all been gathered. success: $successCount fail: $failCount." }

            val updatedVideos = results.map { (original, result) ->
                result.getOrElse { original }
            }

            file.writeText(Json.encodeToString(updatedVideos))
            logger.info { "video statics in ${file.name} have been pickled" }
        }
    }

    private fun gatherAVideo(video: Video): Result<Video> {
        if (video.watchSeconds != null && video.totalSeconds != null) {
            logger.info { "statics of ${video.name} already exists, skip" }
            return Result.success(video)
        }

        return runCatching {
            val totalTimeText: String?
            val watchSecondsText: String?
            val finish: Boolean

            session.newPage(video.url).use { videoPage ->
                // 等待统计信息加载
                videoPage.waitForSelector(".num-gksc > span")

                // 等待视频时长加载完成（不为 "0:00"）
                videoPage.waitForFunction("""
                    () => {
                        const el = document.querySelector('.vjs-duration-display');
                        return el && el.textContent && el.textContent !== '0:00';
                    }
                """)

                totalTimeText = videoPage.querySelector(".vjs-duration-display")?.textContent()
                watchSecondsText = videoPage.querySelector(".num-gksc > span")?.textContent()
                val finishText = videoPage.querySelector(".tips-completion")?.textContent()
                finish = finishText == "已完成"

            }

            val totalSeconds = totalTimeText?.let { parseDuration(it) }
                ?: throw Exception("Unable to parse total time")
            val watchSeconds = watchSecondsText?.toIntOrNull()
                ?: throw Exception("Unable to parse watch seconds")

            video.copy(totalSeconds = totalSeconds, watchSeconds = watchSeconds, finish = finish)
        }.onFailure { e ->
            logger.error(e) { "failed to gather statics of ${video.name}: ${e.message}" }
        }
    }

    companion object {
        private fun parseDuration(text: String): Int {
            val parts = text.split(":")
            return when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                else -> 0
            }
        }
        private val logger = KotlinLogging.logger {}
    }
}

/**
 * 交互，获取用户想选择的课程
 * @return 课程在courses中的索引列表
 */
private fun chooseCoursesIO(allCourses: List<Course>): List<Int> {
    // O
    allCourses.forEachIndexed { index, course ->
        println("${index + 1}. ${course.name}")
    }

    // I
    println("请输入要纳入计划的课程编号（用一个空格分隔，e.g. 1 3 11）：")
    val inputIndex = readln().trim().split(' ').map(String::toInt)
    return inputIndex.map { it - 1 }
}