import com.microsoft.playwright.*
import manager.Session

fun main() {
    Playwright.create().use { pr ->
        Session(pr, "https://moodle.scnu.edu.cn/").use { session ->

        }
    }
}