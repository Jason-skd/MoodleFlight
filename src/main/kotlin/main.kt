import com.microsoft.playwright.*
import manager.Session
import model.PlanningModel

fun main() {
    Playwright.create().use { pr ->
        val session = Session.create(pr).getOrElse { return }
        session.use { session ->
            val planning = PlanningModel(session)
            planning.chooseCourses()
            planning.closeMyPage()
            planning.planVideos()
            planning.gatherVideosStatistics()
        }
    }
}