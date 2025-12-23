package manager

import com.microsoft.playwright.*
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * 每次会话都需要一个AuthManager来创建context
 *
 * 验证AuthToken有效性会创建entryPage
 * @param browser 浏览器对象
 * @param entry 本次会话入口url
 */
class AuthManager(private val browser: Browser, private val entry: String) {
    val authStoragePath = Path("data/authToken.json")

    // 根据是否有authToken创建context
    val context: BrowserContext = if (authStoragePath.exists()) {
        browser.newContext(Browser.NewContextOptions()
                .setStorageStatePath(authStoragePath)
        )
    } else {
        browser.newContext()
    }

    private lateinit var entryPage: Page

    /**
     * context创建即验证AuthToken有效性
     */
    init {
        verifyOrWait()
    }

    /**
     * 导航到入口页面，验证用户登录，或等待直到用户登录
     * 登录成功就刷新AuthToken
     */
    fun verifyOrWait() {
        entryPage = context.newPage()
        entryPage.navigate(entry)
        entryPage.waitForSelector(
            "li.mycourse",
            Page.WaitForSelectorOptions().setTimeout(300_000.0)
        )
        saveToken()
    }

    /**
     * 保存AuthToken
     */
    fun saveToken() {
        context.storageState(BrowserContext.StorageStateOptions()
            .setPath(authStoragePath)
        )
    }
}