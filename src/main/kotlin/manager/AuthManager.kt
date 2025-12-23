package manager

import com.microsoft.playwright.*
import java.nio.file.Path

/*
获取授权token，在这个页面上要求用户登录
当到/my/页面时，说明登录成功，保存新的token，之后返回登录后的context
@param page 页面对象
 */
fun getAuthToken(context: BrowserContext, storagePath: Path): BrowserContext{
    val page = context.pages().last()  // 不可能报错吧qwq
    // 登录成功会跳转到下面界面
    page.waitForSelector(
        "li.mycourse",
        Page.WaitForSelectorOptions().setTimeout(300_000.0)
    )
    context.storageState(BrowserContext.StorageStateOptions().setPath(storagePath))
    return context
}