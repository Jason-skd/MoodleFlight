package model

import com.microsoft.playwright.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import kotlin.io.path.*

import manager.Session
import mu.KotlinLogging
import java.nio.file.Path
import java.util.Objects

/**
 * fetch的课程
 * @param name 课程名称
 * @param url 课程链接
 */
@Serializable
data class Course(
    val name: String,
    val url: String,
    val id: String =
        url.substringAfter("id="),
    val isFinished: Boolean = false,
    val currentPlaying: Int = 0  // 当前播放到的视频
){
    override fun equals(other: Any?): Boolean =
        other is Course && id == other.id

    override fun hashCode(): Int = id.hashCode()
}


@Serializable
data class Video(
    val name: String,
    val url: String,
    val watchSeconds: Int? =null,
    val totalSeconds: Int? = null,
    val isFinished: Boolean = false
) {
    override fun equals(other: Any?): Boolean = other is Video
            && name == other.name && url == other.url


    override fun hashCode(): Int = Objects.hash(name, url, totalSeconds)
}