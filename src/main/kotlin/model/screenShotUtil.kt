package model

import com.microsoft.playwright.Page
import mu.KotlinLogging
import java.time.LocalDateTime
import kotlin.io.path.Path

val logger = KotlinLogging.logger {}

fun screenShotUtil(page: Page) {
    try {
        val now = LocalDateTime.now()
        page.screenshot(Page.ScreenshotOptions()
            .setPath(Path("data/screenshots/$now.png")))
    } catch (screenshotError: Exception) {
        logger.warn { "Failed to take screenshot: $screenshotError" }
    }
}