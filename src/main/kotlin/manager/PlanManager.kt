package manager

import kotlinx.serialization.json.Json
import model.Course
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.streams.asSequence

class PlanManager() {
    val planDir = Path("data/plans/")
    init {
        planDir.createDirectories()
    }

    /**
     * 创建计划, 只创建目录
     */
    fun createPlan(name: String): Path {
        try {
            val path = planDir.resolve(name)
            path.createDirectories()
            logger.info { "Created plan: $path" }
            return path
        } catch (e: Exception) {
            logger.error(e) { "Failed to create plan: $name" }
            throw e
        }
    }

    /**
     * 列出所有已有计划
     */
    fun listPlans(): List<String> =
        Files.list(planDir)
            .asSequence()
            .filter { it.toFile().isDirectory }
            .map { it.fileName.toString() }
            .toList()

    /**
     * 按名字删除计划
     */
    fun deletePlan(name: String): Boolean {
        return try {
            val path = planDir.resolve(name)
            Files.walk(path)  // 遍历目录树
                .sorted(Comparator.reverseOrder())  // 反向排序( 先删文件, 后删目录)
                .forEach { Files.deleteIfExists(it) }
            logger.info { "Delete plan: $name" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete plan: $name" }
            false
        }
    }


    fun getPlan(name: String): Pair<Path, Map<String, Course>?> {
        return try {
            val path = planDir.resolve(name)
            val courseFile = path.resolve("courses.json")
            val json: Map<String, Course> = Json.decodeFromString(courseFile.readText())
            logger.debug { "Loaded plan: $name" }
            path to json
        } catch (e: Exception) {
            logger.error(e) { "Failed to load plan: $name" }
            planDir.resolve(name) to null
        }
    }

    companion object {
        val logger = KotlinLogging.logger {}
    }
}

/**
 * 交互式选择或创建计划
 * @return Pair<计划目录路径, 课程数据> 新计划课程数据为 null
 */
fun PlanManager.choosePlanIO(): Pair<Path, Map<String, Course>?> {
    val plans = listPlans()

    // 处理空计划列表的情况
    if (plans.isEmpty()) {
        return createNewPlan()
    }

    // 显示现有计划列表
    println("检测到以下计划: ")
    plans.forEachIndexed { index, name ->
        println("${index + 1}: $name")
    }
    println("请输入序号选择，或键入 0 创建新计划")

    // 读取并验证输入
    val input = readln().trim()
    val inp = input.toIntOrNull()

    return when (inp) {
        null -> {
            println("输入无效，请输入数字")
            choosePlanIO()
        }
        0 -> createNewPlan()
        in 1..plans.size -> getPlan(plans[inp - 1])
        else -> {
            println("序号超出范围，请重新输入")
            choosePlanIO()
        }
    }
}

/**
 * 创建新计划的辅助函数
 */
private fun PlanManager.createNewPlan(): Pair<Path, Map<String, Course>?> {
    println("请键入计划名称: ")
    val newPlanName = readln().trim()
    if (newPlanName.isEmpty()) {
        println("计划名称不能为空")
        return choosePlanIO()
    }
    val path = createPlan(newPlanName)
    return path to null
}