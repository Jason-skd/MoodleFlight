package manager

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.Course
import model.Video
import mu.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.*

/**
 * 核心解决需要查找本门课程下一个视频的问题
 * @param course 当前课程
 * @param planDir 计划目录
 */
class CourseManager(var course: Course, planDir: Path) {
    val courseName = course.name
    val id = course.id
    val coursesFile: Path = planDir.resolve("courses.json")
    val videosFile: Path = planDir.resolve("${courseName}_videos.json")
    val videos: List<Video>
        get() = Json.decodeFromString(videosFile.readText())

    /**
     * 判断是否刷完
     * 存储并返回结果
     */
    fun refreshFinish(): Boolean {
        // 预留给前端的接口，理论上后端的顺序执行Finish能正确更新
        val actualFinished = videos.all(Video::isFinished)
        updateAndPersist(course.copy(isFinished = actualFinished))
        logger.info { "$courseName actualFinished=$actualFinished" }
        return actualFinished
    }

    /**
     * 获取下一个要刷的Video
     * @return 刷完则返回null
     */
    fun nextVideo(): Video? {
        // 先依赖预存索引快速查找
        val tryNext = videos.slice(course.currentPlaying..<videos.size).find { !it.isFinished }
        if (tryNext != null) {
            return updateNextVideo(tryNext)
        }

        // 一次额外的检查（从头开始找）
        logger.trace { "Rechecking videos for course $courseName" }
        val fallback = videos.find { !it.isFinished }
        if (fallback != null) {
            return updateNextVideo(fallback)
        }

        logger.info { "No video left for course $courseName" }
        updateAndPersist(course.copy(isFinished = true))
        return null
    }

    /**
     * 当找到下一个Video后，更新信息
     * @return 并返回这个Video
     */
    private fun updateNextVideo(video: Video): Video {
        val videoIndex = videos.indexOf(video)
        updateAndPersist(course.copy(currentPlaying = videoIndex))
        logger.info { "Next Video: ${video.name}"}
        return video
    }

    /**
     * 更新持有的Course并写入文件
     */
    private fun updateAndPersist(courseCopy: Course) {
        course = courseCopy

        val courses: Map<String, Course> = Json.decodeFromString(coursesFile.readText())
        val updatedCourses = courses + (id to course)
        coursesFile.writeText(Json.encodeToString(updatedCourses))
        logger.info { "Course $courseName updated successfully" }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}