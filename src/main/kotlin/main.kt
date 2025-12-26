import com.microsoft.playwright.*
import manager.Session
import model.PlanningModel
import model.executeCourse

fun main() {
    Playwright.create().use { pr ->
        val session = Session.create(pr).getOrElse { return }
        session.use { session ->
            val planning = PlanningModel(session)
            planning.chooseCourses()
            planning.closeMyPage()
            planning.planVideos()
            planning.gatherVideosStatistics()

            planning.chosenCourses.values.forEach { course ->
                if (!course.isFinished) {
                    executeCourse(session, course, planning.planDir)
                }
            }
        }
    }
}