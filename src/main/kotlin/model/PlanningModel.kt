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
    val url: String,
    var isFinished: Boolean = false,
    var currentPlaying: Int = 0  // 当前播放到的视频
)

@Serializable
data class Video(
    val name: String,
    val url: String,
    val watchSeconds: Int? =null,
    val totalSeconds: Int? = null,
    val isFinished: Boolean = false
)

/**
 * 课程规划流
 * @param session 全局session
 */
class PlanningModel(private val session: Session) {
    // moodle主页
    private val myPage: Page = session.newPage("https://moodle.scnu.edu.cn/my/")

    lateinit var chosenCourses: Map<String, Course>

    private val today: LocalDate? = LocalDate.now()
    val planDir = Path("data/plans/$today")
    val coursesDir: Path = planDir.resolve("courses.json")  // 要与CourseManager同步更改

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
                url = link.getAttribute("href") ?: "",  // 潜在风险，后续用Result处理
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
            val selectedCourses = chooseCoursesIO(allCourses).map { allCourses[it] }
            chosenCourses = selectedCourses.associateBy { it.name }
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

    /**
     * 关闭首页，当获取完课程应该手动调用
     */
    fun closeMyPage() {
        myPage.close()
    }

    /**
     * 获取所有视频基础信息
     */
    fun planVideos() {
        chosenCourses.values.forEach { course ->
            val result = planVideosForCourse(course)
            result.onFailure { throw it }
        }
    }

    /**
     * 决策该课程是否还需要获取视频基础信息
     * @param course 课程
     */
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

    /**
     * 获取一个课程的视频基础信息
     * @param course 课程
     */
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

    /**
     * 存储视频基础信息
     * @param course 课程
     * @param videos 这个课程的所有视频
     */
    private fun pickleVideos(course: Course, videos: List<Video>) {
        val json = Json.encodeToString(videos)
        val dir = planDir.resolve("${course.name}_videos.json")  // 要与CourseManager同步更改
        dir.writeText(json)
        logger.info { "videos plan pickled to $dir." }
    }

    /**
     * 获取所有视频的详细信息
     */
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

    /**
     * 获取一个视频的详细信息
     */
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
                finish = isFinishedSchedule(videoPage)
            }

            val totalSeconds = totalTimeText?.let { parseDuration(it) }
                ?: throw Exception("Unable to parse total time")
            val watchSeconds = watchSecondsText?.toIntOrNull()
                ?: throw Exception("Unable to parse watch seconds")

            video.copy(totalSeconds = totalSeconds, watchSeconds = watchSeconds, isFinished = finish)
        }.onFailure { e ->
            logger.error(e) { "failed to gather statics of ${video.name}: ${e.message}" }
        }
    }

    companion object {
        /**
         * 将 时:分:秒 格式转化为秒数
         */
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