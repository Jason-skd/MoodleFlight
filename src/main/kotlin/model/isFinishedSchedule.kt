package model

import com.microsoft.playwright.Page

fun isFinishedSchedule(videoPage: Page): Boolean {
    val finishText = videoPage.querySelector(".tips-completion")?.textContent()
    return finishText == "已完成"
}