package model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import manager.Session
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * 收集一个课程所有视频的详细信息
 */
class GatherVideoStatics private constructor(val session: Session, val videoFile: Path) {
    init {
        logger.info { "begin to gather video statics for ${videoFile}..." }
        gatherVideosStatistics()
    }

    /**
     * 获取所有视频的详细信息
     */
    fun gatherVideosStatistics() {
        val videos: List<Video> = Json.decodeFromString(videoFile.readText())
        val results = videos.map { video -> video to gatherAVideo(video) }

        val successCount = results.count { it.second.isSuccess }
        val failCount = results.count { it.second.isFailure }
        logger.info { "video statics in ${videoFile.name} have all been gathered. success: $successCount fail: $failCount." }

        val updatedVideos = results.map { (original, result) ->
            result.getOrElse { original }
        }

        videoFile.writeText(Json.encodeToString(updatedVideos))
        logger.info { "video statics in ${videoFile.name} have been persisted" }
    }


    /**
     * 获取一个视频的详细信息
     * @return 通过Result传递获取结果
     */
    private fun gatherAVideo(video: Video): Result<Video> {
        if (video.watchSeconds != null && video.totalSeconds != null) {
            logger.info { "statics of ${video.name} already exists, skip" }
            return Result.success(video)
        }

        return runCatching {
            val totalTimeText: String?
            val watchSecondsText: String?
            val finish: Boolean

            session.newPage(video.url).use { videoPage ->
                // 等待统计信息加载
                videoPage.waitForSelector(".num-gksc > span")

                // 等待视频时长加载完成（不为 "0:00"）
                videoPage.waitForFunction(
                    """
                    () => {
                        const el = document.querySelector('.vjs-duration-display');
                        return el && el.textContent && el.textContent !== '0:00';
                    }
                """
                )

                totalTimeText = videoPage.querySelector(".vjs-duration-display")?.textContent()
                watchSecondsText = videoPage.querySelector(".num-gksc > span")?.textContent()
                finish = isFinishedSchedule(videoPage)
            }

            val totalSeconds = totalTimeText?.let { parseDuration(it) }
                ?: throw Exception("Unable to parse total time")
            val watchSeconds = watchSecondsText?.toIntOrNull()
                ?: throw Exception("Unable to parse watch seconds")

            video.copy(totalSeconds = totalSeconds, watchSeconds = watchSeconds, isFinished = finish)
        }.onFailure { e ->
            logger.error(e) { "failed to gather statics of ${video.name}: ${e.message}" }
        }
    }
    companion object {
        val logger = KotlinLogging.logger {}

        fun create(session: Session, course: Course, planDir: Path): Result<GatherVideoStatics> {
            val videoFile = planDir.resolve("${course.name}_videos.json")
            if (!Files.exists(videoFile)) {
                return Result.failure(Exception("course ${course.name} doesn't exists"))
            }
            return Result.success(GatherVideoStatics(session, videoFile))
        }

        fun parseDuration(text: String): Int {
            val parts = text.split(":")
            return when (parts.size) {
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                else -> 0
            }
        }
    }
}