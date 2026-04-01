package fansirsqi.xposed.sesame.util

import com.fasterxml.jackson.core.type.TypeReference

/**
 * 通用任务黑名单管理器
 * 使用DataStore持久化存储黑名单数据
 */
object TaskBlacklist {
    private const val TAG = "TaskBlacklist"
    private const val BLACKLIST_KEY = "task_blacklist"

    // 默认黑名单（可按需扩展）
    private val defaultBlacklist = setOf<String>()

    /**
     * 获取黑名单列表
     * @return 黑名单任务集合
     */
    fun getBlacklist(): Set<String> {
        return try {
            val storedBlacklist = DataStore.getOrCreate(BLACKLIST_KEY, object : TypeReference<Set<String>>() {})
            // 合并存储的黑名单和默认黑名单
            (storedBlacklist + defaultBlacklist).toSet()
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "获取黑名单失败，使用默认黑名单", e)
            defaultBlacklist
        }
    }

    /**
     * 保存黑名单列表
     * @param blacklist 要保存的黑名单集合
     */
    private fun saveBlacklist(blacklist: Set<String>) {
        try {
            DataStore.put(BLACKLIST_KEY, blacklist)
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "保存黑名单失败", e)
        }
    }

    /**
     * 检查任务是否在黑名单中（精确匹配逻辑）
     * @param taskInfo 任务信息（可以是任务ID、任务标题或组合信息）
     * @return true表示在黑名单中，应该跳过
     */
    fun isTaskInBlacklist(taskInfo: String?): Boolean {
        if (taskInfo.isNullOrBlank()) return false

        val blacklist = getBlacklist()
        return blacklist.any { item ->
            if (item.isBlank()) return@any false

            // 完全匹配（最精确）
            if (taskInfo == item) return@any true

            // 区分处理中文关键词和纯英文的匹配模式。
            val itemHasChinese = item.any { it in '\u4e00'..'\u9fa5' }

            if (itemHasChinese) {
                // 包含中文的项维持双向模糊匹配逻辑
                taskInfo.contains(item) || item.contains(taskInfo)
            } else {
                /* 纯英文/数字/符号项使用单向模糊匹配逻辑；防止黑名单中"TAOBAO"这类比较简短、通用的字段匹配到任务
                    "TAOBAO_tab2gzy" ，导致不是在黑名单中的任务被跳过
                 */
                item.contains(taskInfo)
            }
        }
    }

    /**
     * 添加任务到黑名单
     * @param taskId 要添加的任务ID
     * @param taskTitle 任务标题（可选，用于模糊匹配）
     */
    fun addToBlacklist(taskId: String, taskTitle: String = "") {
        if (taskId.isBlank()) return
        // 如果提供了任务标题，则将ID和标题组合后添加，支持模糊匹配
        val blacklistItem = if (taskTitle.isNotBlank()) "$taskId$taskTitle" else taskId
        val currentBlacklist = getBlacklist().toMutableSet()
        if (currentBlacklist.add(blacklistItem)) {
            saveBlacklist(currentBlacklist)
        }
    }

    /**
     * 从黑名单中移除任务
     * @param taskId 要移除的任务ID
     * @param taskTitle 任务标题（可选，用于模糊匹配）
     */
    fun removeFromBlacklist(taskId: String, taskTitle: String = "") {
        if (taskId.isBlank()) return

        // 如果提供了任务标题，则将ID和标题组合后移除，支持模糊匹配
        val blacklistItem = if (taskTitle.isNotBlank()) "$taskId$taskTitle" else taskId

        val currentBlacklist = getBlacklist().toMutableSet()
        if (currentBlacklist.remove(blacklistItem)) {
            saveBlacklist(currentBlacklist)
            val displayInfo = if (taskTitle.isNotBlank()) "$taskId - $taskTitle" else taskId
            Log.record(TAG, "任务[$displayInfo]已从黑名单移除")
        }
    }

    /**
     * 清空黑名单
     */
    fun clearBlacklist() {
        try {
            saveBlacklist(emptySet())
            Log.record(TAG, "黑名单已清空")
        } catch (e: Exception) {
            Log.printStackTrace(TAG, "清空黑名单失败", e)
        }
    }

    /**
     * 根据错误码自动添加任务到黑名单
     * 当任务执行失败时，如果错误码属于预定义的无法恢复的错误类型，
     * 系统会自动将该任务加入黑名单，避免重复执行失败的任务
     *
     * @param taskId 任务ID，用于标识具体任务
     * @param taskTitle 任务标题（可选），用于显示和模糊匹配
     * @param errorCode 错误码/错误信息，用于判断是否需要自动加入黑名单
     */
    fun autoAddToBlacklist(taskId: String, taskTitle: String = "", errorCode: String) {
        // 参数校验：如果任务ID为空，直接返回
        if (taskId.isBlank()) return

        var shouldAutoAdd = false
        var reason = ""

        // 判断逻辑重构为 when 结构，便于扩展
        when {
            errorCode == "400000040" -> {
                shouldAutoAdd = true
                reason = "不支持rpc调用"
            }
            errorCode == "CAMP_TRIGGER_ERROR" -> {
                shouldAutoAdd = true
                reason = "海豚活动触发错误"
            }
            errorCode == "OP_REPEAT_CHECK" -> {
                shouldAutoAdd = true
                reason = "操作太频繁"
            }
            errorCode == "ILLEGAL_ARGUMENT" -> {
                shouldAutoAdd = true
                reason = "参数错误"
            }
            errorCode == "104" || errorCode == "PROMISE_HAS_PROCESSING_TEMPLATE" -> {
                shouldAutoAdd = true
                reason = "存在进行中的生活记录"
            }
            errorCode == "TASK_ID_INVALID" -> {
                shouldAutoAdd = true
                reason = "海豚任务ID非法"
            }
            // 新增：适配中文错误提示，如好家无忧卡任务返回的 "系统繁忙，请稍后再试"
            errorCode.contains("系统繁忙") || errorCode.contains("稍后再试") -> {
                shouldAutoAdd = true
                reason = "系统繁忙/稍后再试"
            }
        }

        // 如果确定需要自动加入黑名单
        if (shouldAutoAdd) {
            // 调用添加方法，将任务ID和标题组合后加入黑名单（支持模糊匹配）
            addToBlacklist(taskId, taskTitle)

            // 生成日志信息并记录
            // 优先显示完整信息（ID-标题），如果标题为空则只显示ID
            val taskInfo = if (taskTitle.isNotBlank()) "$taskId - $taskTitle" else taskId
            Log.record(TAG, "任务[$taskInfo]因$reason 自动加入黑名单")
        }
    }
}