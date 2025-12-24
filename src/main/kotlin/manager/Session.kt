package manager

import com.microsoft.playwright.*
import java.lang.AutoCloseable
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * 管理浏览器会话，统一context
 * @param pr Playwright
 */
class Session(private val pr: Playwright) : AutoCloseable {
    val authStoragePath = Path("data/authToken.json")

    lateinit var authToken: String

    private val authOptions = BrowserType.LaunchOptions()
        .setHeadless(false)      // 不能改！！登录配置必须关闭无头模式
        .setChannel("chrome")
        .setArgs(listOf("--disable-blink-features=AutomationControlled"))


    private val runningOptions = BrowserType.LaunchOptions()
        .setHeadless(true)
        .setChannel("chrome")
        .setArgs(listOf("--disable-blink-features=AutomationControlled"))


    private val browser: Browser = pr.chromium().launch(runningOptions)

    private val context: BrowserContext

    /**
     * Session创建即验证AuthToken有效性
     */
    init {
        authenticationVerify()
        context = browser.newContext(Browser.NewContextOptions()
            .setStorageState(authToken)
        )
    }

    /**
     * 验证AuthToken有效性
     */
    private fun authenticationVerify() {
        if (authStoragePath.exists()) {
            val ctx = browser.newContext(Browser.NewContextOptions()
                .setStorageStatePath(authStoragePath)
            )
            val verifyPage = ctx.newPage()
            verifyPage.navigate("https://moodle.scnu.edu.cn/my/")

            val element = verifyPage.querySelector("li.mycourse")
            if (element != null) {
                authToken = ctx.storageState()  // 保存 token
                ctx.close()
                println("登录验证成功")
                return
            }
            ctx.close()
            println("登录失效")
        } else { println("没有登录Token") }
        loginRequired().onSuccess { authToken = it }
            .onFailure { throw it }  // 登录失败抛出异常
    }

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

                        println("请登录")
                        authPage.waitForSelector(
                            "li.mycourse",
                            Page.WaitForSelectorOptions().setTimeout(300_000.0)
                        )

                        println("登录成功")

                        val newAuthToken = authContext.storageState()
                        authContext.storageState(
                            BrowserContext.StorageStateOptions()
                                .setPath(authStoragePath)
                        )
                        println("登录token保存成功")
                        return Result.success(newAuthToken)
                    }
                }
            }
        }
        return result
    }

    fun newPage(url: String): Page = context.newPage().apply {
        navigate(url)
    }

    override fun close() {
        context.close()
        browser.close()
    }
}