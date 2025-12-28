package model

import manager.CourseManager
import manager.Session
import manager.VideoManager
import mu.KotlinLogging
import java.nio.file.Path

fun executeCourse(session: Session, course: Course, planDir: Path) {
    val logger = KotlinLogging.logger {}
    val courseManager = CourseManager(course, planDir)
    var nextVideo = courseManager.nextVideo()

    while (nextVideo != null) {
        VideoManager(session, nextVideo, courseManager.videoFile).use { vm ->
            val result = vm.playUntilFinished()

            result.onFailure { logger.error(it)
                    { "Failed to keep playing ${nextVideo!!.name}, skipping..." } }
                .onSuccess { logger.info { "${nextVideo!!.name}: Finish playing" } }
        }

        nextVideo = courseManager.nextVideo()
    }
}