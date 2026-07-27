package fansirsqi.xposed.sesame.task.antForest

enum class ForestDrawTaskAction {
    EXCHANGE_VITALITY,
    FINISH_XLIGHT,
    FINISH_STANDARD,
    WAIT_FOR_CAPTURE
}

/**
 * 森林抽抽乐任务分发规则。
 *
 * 游戏、宝箱和分享任务缺少完整官方调用链，必须等待抓包后再增加专用处理器。
 */
object ForestDrawTaskPolicy {
    private val unsupportedTypes = setOf(
        "FOREST_NORMAL_DRAW_SHARE",
        "FOREST_ACTIVITY_DRAW_SHARE",
        "FOREST_ACTIVITY_DRAW_XS"
    )

    fun actionFor(taskType: String, taskName: String): ForestDrawTaskAction {
        if (
            unsupportedTypes.any(taskType::contains) ||
            taskType.contains("GAME", ignoreCase = true) ||
            taskName.contains("玩游戏") ||
            taskName.contains("开宝箱")
        ) {
            return ForestDrawTaskAction.WAIT_FOR_CAPTURE
        }

        if (taskType == "NORMAL_DRAW_EXCHANGE_VITALITY") {
            return ForestDrawTaskAction.EXCHANGE_VITALITY
        }

        if (taskType.contains("XLIGHT")) {
            return ForestDrawTaskAction.FINISH_XLIGHT
        }

        if (
            taskType.startsWith("FOREST_NORMAL_DRAW") ||
            taskType.startsWith("FOREST_ACTIVITY_DRAW")
        ) {
            return ForestDrawTaskAction.FINISH_STANDARD
        }

        return ForestDrawTaskAction.WAIT_FOR_CAPTURE
    }

    fun isRetryableFailure(resultCode: String?, resultDescription: String?): Boolean {
        if (resultCode in setOf("ILLEGAL_ARGUMENT", "I07", "I09")) {
            return false
        }
        val description = resultDescription.orEmpty()
        return !description.contains("不支持rpc完成的任务", ignoreCase = true) &&
            !description.contains("不支持rpc调用", ignoreCase = true) &&
            !description.contains("任务全局配置不存在")
    }
}
