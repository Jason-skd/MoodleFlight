import com.microsoft.playwright.*
import manager.getAuthToken
import kotlin.io.path.Path
import kotlin.io.path.exists

fun main() {
    Playwright.create().use { pr ->
        val authStorage = Path("data/authToken.json")
        val signSuccessUrl = "https://moodle.scnu.edu.cn/my/"

        val options = BrowserType.LaunchOptions()
            .setHeadless(false)
            .setChannel("chrome")

        val browser = pr.chromium().launch(options)

        var context = if (authStorage.exists()) {
            browser.newContext(Browser.NewContextOptions().setStorageStatePath(authStorage))
        } else {
            // 先创建空context，后续处理会发现登录失效，并通过AuthManager更新context
            browser.newContext()
        }

        val page = context.newPage()
        page.navigate(signSuccessUrl)

        val signVerify = page.url()
        if (!signVerify.startsWith(signSuccessUrl)) {
            println("登录失效，请在此窗口重新登录")
            context = getAuthToken(context, authStorage)
        }
        println("登录成功")

        context.close()
        browser.close()
    }
}