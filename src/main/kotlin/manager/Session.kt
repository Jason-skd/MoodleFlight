package manager

import com.microsoft.playwright.*
import kotlinx.coroutines.runBlocking
import java.lang.AutoCloseable
import kotlin.io.path.Path
import kotlin.io.path.exists
import mu.KotlinLogging

/**
 * 管理浏览器会话，统一context
 * @param pr Playwright
 */
class Session private constructor(private val pr: Playwright, authToken: String) : AutoCloseable {

    private val browser: Browser = pr.chromium().launch(runningOptions)

    private val context = browser.newContext(Browser.NewContextOptions().setStorageState(authToken))

    fun newPage(url: String): Page = context.newPage().apply {
        navigate(url)
    }

    override fun close() {
        context.close()
        browser.close()
        logger.info { "Session closed" }
    }

    companion object {
        val authStoragePath = Path("data/authToken.json")

        /**
         * 检测可用浏览器并决定使用哪个
         */
        private fun decideChannel() = runBlocking {
            val availableBrowsers = detectAvailableBrowsers()
            val chosenChannel = chooseChannel(availableBrowsers)
            logger.info { "chosen browser: ${chosenChannel.browserName}" }
            authOptions.setChannel(chosenChannel.browserName)
            runningOptions.setChannel(chosenChannel.browserName)
        }

        private val authOptions = BrowserType.LaunchOptions()
            .setHeadless(false)      // 不能改！！登录配置必须关闭无头模式
            .setArgs(listOf("--disable-blink-features=AutomationControlled"))


        private val runningOptions = BrowserType.LaunchOptions()
            .setHeadless(true)
            .setArgs(listOf("--disable-blink-features=AutomationControlled"))

        private val logger = KotlinLogging.logger {}

        fun create(pr: Playwright, alreadyFailed: Boolean = false): Result<Session> {
            /**
             * 进行登录，保存AuthToken
             * @return 是否登录成功
             */
            fun loginRequired(): Result<String> {
                val result: Result<String>
                pr.chromium().launch(authOptions).use { authBrowser ->
                    authBrowser.newContext().use { authContext ->
                        authContext.newPage().use { authPage ->
                            result =  runCatching {
                                authPage.navigate("https://moodle.scnu.edu.cn/my/")

                                logger.debug { "wait for login" }
                                authPage.waitForSelector(
                                    "li.mycourse",
                                    Page.WaitForSelectorOptions().setTimeout(300_000.0)
                                )

                                logger.info { "login succeed" }

                                val authToken = authContext.storageState()
                                authContext.storageState(
                                    BrowserContext.StorageStateOptions()
                                        .setPath(authStoragePath)
                                )
                                logger.info { "authToken saved"}
                                return Result.success(authToken)
                            }.onFailure { logger.error { "login failed" } }
                        }
                    }
                }
                return result
            }

            /**
             * 验证AuthToken有效性
             */
            fun authenticationVerify(): Result<String> {
                if (authStoragePath.exists()) {
                    val browser = pr.chromium().launch(runningOptions)
                    val ctx = browser.newContext(Browser.NewContextOptions()
                        .setStorageStatePath(authStoragePath)
                    )
                    val verifyPage = ctx.newPage()
                    verifyPage.navigate("https://moodle.scnu.edu.cn/my/")

                    val element = verifyPage.querySelector("li.mycourse")
                    if (element != null) {
                        val authToken = ctx.storageState()  // 使用该Token
                        ctx.close()
                        logger.info { "Auth token validated" }
                        return Result.success(authToken)
                    }
                    ctx.close()
                    logger.info { "Auth token invalidated" }
                } else { logger.info { "Auth token not found"} }
                return loginRequired()
            }

            // 选择浏览器
            decideChannel()

            val tokenResult = if (!alreadyFailed) authenticationVerify() else loginRequired()
            val authToken = tokenResult.getOrElse {
                return Result.failure(it)
            }
            return Result.success(Session(pr, authToken))
        }
    }
}

/**
 * 让用户选择浏览器
 */
private fun chooseChannel(available: Set<Browsers>): Browsers {
    val list = available.toList()
    println("请选择要使用的浏览器：")
    list.forEachIndexed { index, browser ->
        println("${index + 1}. ${browser.browserName}")
    }
    return list[readln().toInt() - 1]
}