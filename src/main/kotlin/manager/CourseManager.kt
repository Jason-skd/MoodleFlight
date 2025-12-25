package manager

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.Course
import model.Video
import java.nio.file.Path
import kotlin.io.path.*

/**
 * 管理Course
 */
class CourseManager(var course: Course, planDir: Path) {
    val courseName = course.name
    val coursesFile: Path = planDir.resolve("courses.json")
    val videosFile: Path = planDir.resolve("${courseName}_videos.json")
    val videos: List<Video>
        get() = Json.decodeFromString(videosFile.readText())

    /**
     * 判断是否刷完
     * 存储并返回结果
     */
    fun refreshFinish(): Boolean {
        val actualFinished = videos.all(Video::isFinished)
        course.isFinished = actualFinished
        updateToCourseFile()
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
        val fallback = videos.find { !it.isFinished }
        if (fallback != null) {
            return updateNextVideo(fallback)
        }

        refreshFinish()
        return null
    }

    /**
     * 当找到下一个Video后，更新信息
     * @return 并返回这个Video
     */
    private fun updateNextVideo(video: Video): Video {
        course.currentPlaying = videos.indexOf(video)
        updateToCourseFile()
        return video
    }

    /**
     * 将目前持有的Course写入文件
     */
    private fun updateToCourseFile() {
        val courses: Map<String, Course> = Json.decodeFromString(coursesFile.readText())
        val updatedCourses = courses + (courseName to course)
        coursesFile.writeText(Json.encodeToString(updatedCourses))
    }
}