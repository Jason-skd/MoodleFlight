package manager

import kotlinx.serialization.Serializable
import kotlinx.coroutines.*
import mu.KotlinLogging
import kotlin.io.path.Path
import kotlin.io.path.exists

val logger = KotlinLogging.logger {}

enum class Browsers(val browserName: String, val macPath: String, val winPath: String) {
    CHROME("chrome",
        "/Applications/Google Chrome.app",
        "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"
        ),
    MSEDGE("msedge",
        "/Applications/Microsoft Edge.app",
        "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
    ),
    FIREFOX("firefox",
        "/Applications/Firefox.app",
        "C:\\Program Files\\Mozilla Firefox\\firefox.exe"
    )
}

@Serializable
data class Config(
    val browserChannel: Browsers
) {
}

suspend fun detectAvailableBrowsers(): Set<Browsers> {
    val os = System.getProperty("os.name").lowercase()
    logger.info { "identified os: $os" }

    return when {
        os.contains("win") -> { detectBrowserForWindows() }
        os.contains("mac") -> { detectBrowserForMac() }
        else -> emptySet()
    }
}

private suspend fun detectBrowserForMac(): Set<Browsers> = coroutineScope {
    Browsers.entries.map { browser ->
        async {
            val path = Path(browser.macPath)
            if (path.exists()) browser else null
        }
    }.awaitAll()
        .filterNotNull()
        .toSet()
        .also { logger.info { "browser detected: ${it.size}" } }
}

private suspend fun detectBrowserForWindows(): Set<Browsers> = coroutineScope {
    Browsers.entries.map { browser ->
        async {
            val path = Path(browser.winPath)
            if (path.exists()) browser else null
        }
    }.awaitAll()
        .filterNotNull()
        .toSet()
        .also { logger.info { "browser detected: ${it.size}" } }
}