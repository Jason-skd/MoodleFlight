import com.microsoft.playwright.*
import kotlinx.serialization.json.Json
import manager.PlanManager
import manager.Session
import manager.choosePlanIO
import model.*
import kotlin.io.path.readText

fun main() {
    Playwright.create().use { pr ->
        val session = Session.create(pr).getOrElse { return }
        session.use { session ->
            val planManager = PlanManager()
            val courses: Map<String, Course>
            val (planDir, planWithName) = planManager.choosePlanIO()
            if (planWithName == null) {
                // 1. 课程发现 & 用户选择
                val discovery = DiscoverCourse(session, planDir)
                val chosenIds = chooseCoursesIO(discovery.allCourses)
                discovery.chosenCourses(chosenIds)

                // 2. 概览视频（提取视频链接）
                OverviewVideos.create(session, planDir)
                    .onSuccess { it.overviewAllCourses() }
                    .onFailure {
                        println("Failed to create OverviewVideos: ${it.message}")
                        return
                    }

                // 3. 收集视频统计信息
                val coursesFile = planDir.resolve("courses.json")
                courses = Json.decodeFromString(coursesFile.readText())

                courses.values.forEach { course ->
                    if (!course.isFinished) {
                        GatherVideoStatics.create(session, course, planDir)
                            .onFailure {
                                println("Failed to gather statistics for ${course.name}: ${it.message}")
                            }
                    }
                }
            } else {
                println("该计划包含以下课程: ")
                planWithName.values.forEach { course ->
                    val name = course.name
                    println(name)
                }
                courses = planWithName
            }

            // 4. 执行播放
            courses.values.forEach { course ->
                if (!course.isFinished) {
                    executeCourse(session, course, planDir)
                }
            }
        }
    }
}

/**
 * 交互：用户选择课程
 * @return 课程 ID 列表
 */
private fun chooseCoursesIO(allCourses: List<Course>): List<String> {
    // 显示课程列表
    allCourses.forEachIndexed { index, course ->
        println("${index + 1}. ${course.name} [${course.id}]")
    }

    // 读取用户输入
    println("请输入要纳入计划的课程编号（用一个空格分隔，e.g. 1 3 11）：")
    val input = readln().trim()
    if (input.isEmpty()) return emptyList()

    val indices = input.split(' ').map(String::toInt)
    return indices.map { allCourses[it - 1].id }
}