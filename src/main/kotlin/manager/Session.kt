package manager

import com.microsoft.playwright.*
import java.lang.AutoCloseable
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * 管理浏览器会话，统一context
 * @param pr Playwright
 * @param entry 本次会话入口url
 */
class Session(private val pr: Playwright, entry: String) : AutoCloseable {
    private val options = BrowserType.LaunchOptions()
        .setHeadless(false)
        .setChannel("chrome")

    private val browser: Browser = pr.chromium().launch(options
    )
    // 创建context并从entry页面认证authToken
    val authManager: AuthManager = AuthManager(browser, entry)

    override fun close() {
        authManager.context.close()
        browser.close()
    }
}