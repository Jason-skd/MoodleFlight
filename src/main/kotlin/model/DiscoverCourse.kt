package model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import manager.Session
import mu.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * 课程发现模块
 * 1. 获取全部课程
 * 2. 将要选择的课程持久化
 */
class DiscoverCourse(val session: Session, val planDir: Path) {
    val myPage = session.newPage("https://moodle.scnu.edu.cn/my/")
    val allCourses = fetchCourses()

    /**
     * 获取全部课程名字和链接
     * @return 列表
     */
    private fun fetchCourses(): List<Course> {
        myPage.click("li.mycourse")
        myPage.waitForSelector(".mycourse .dropdown-menu a[href*='/course/view.php']")

        val coursesInQuery = myPage.querySelectorAll("li.mycourse .dropdown-menu a[href*='/course/view.php']")
        return coursesInQuery.map { link ->
            Course(
                name = link.textContent().trim(),
                url = link.getAttribute("href") ?: "",  // 潜在风险?后续用Result处理
            )
        }.also {
            logger.info { "fetched ${it.size} courses from moodle" }
        }
    }

    /**
     * 1. 解析chosenCourses
     * 2. 处理数据
     * 3. 调用持久化
     */
    fun chosenCourses(ids: List<String>) {
        val chosen = allCourses.filter { it.id in ids }.associateBy { it.id }
        myPage.close()
        persistChosenCourses(chosen)
    }

    /**
     * 持久化用户选择的课程
     */
    private fun persistChosenCourses(chosen: Map<String, Course>) {
        val json = Json.encodeToString(chosen)
        val coursesDir = planDir.resolve("courses.json")
        coursesDir.writeText(json)
        logger.info { "courses plan persisted to $coursesDir" }
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}