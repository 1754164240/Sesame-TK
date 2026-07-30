package fansirsqi.xposed.sesame.task.antOcean

interface AiFishGateway {
    fun queryStatus(): String
    fun queryHome(): String
    fun listTasks(sceneCode: String): String
    fun finishTask(sceneCode: String, taskType: String): String
    fun receiveTaskAward(sceneCode: String, taskType: String): String
    fun rescueFish(): String
    fun touchFish(): String
    fun waitMillis(millis: Long)
}

data class AiFishRunResult(
    val available: Boolean,
    val rescued: Boolean,
    val completedTaskCount: Int,
    val receivedRewardCount: Int,
    val touchCount: Int,
    val events: List<String>
)

class AiFishWorkflow(
    private val gateway: AiFishGateway
) {
    private val events = mutableListOf<String>()
    private var completedTaskCount = 0
    private var receivedRewardCount = 0

    fun run(): AiFishRunResult {
        if (!AiFishProtocol.isActionAccepted(gateway.queryStatus())) {
            events += "AI摸鱼状态接口不可用"
            return result(available = false)
        }
        var home = AiFishProtocol.parseHome(gateway.queryHome())
        if (!home.recognized) {
            events += "AI摸鱼主页结构未知"
            return result(available = true)
        }

        var rescued = false
        if (home.fishStatus.equals("CAPTURED", true)) {
            rescued = rescueCapturedFish()
            if (!rescued) {
                return result(available = true)
            }
        }

        processMainTasks()
        val touchCount = touchAvailableFish()
        return result(
            available = true,
            rescued = rescued,
            touchCount = touchCount
        )
    }

    private fun rescueCapturedFish(): Boolean {
        val snapshot = AiFishProtocol.parseTasks(
            gateway.listTasks(AiFishProtocol.RESCUE_SCENE)
        )
        val task = AiFishProtocol.selectRescueTask(snapshot)
        if (task == null) {
            events += "AI摸鱼未找到可用找回任务"
            return false
        }
        val waitMillis = (task.waitSeconds + 1) * 1000L
        events += "AI摸鱼找回任务等待${task.waitSeconds}秒"
        gateway.waitMillis(waitMillis)
        if (!AiFishProtocol.isActionAccepted(gateway.rescueFish())) {
            events += "AI摸鱼找回接口未受理"
            return false
        }
        val confirmed = AiFishProtocol.parseHome(gateway.queryHome())
        val rescued = confirmed.recognized &&
            !confirmed.fishStatus.equals("CAPTURED", true)
        events += if (rescued) {
            "AI摸鱼被抓的鱼已找回"
        } else {
            "AI摸鱼找回状态未确认"
        }
        return rescued
    }

    private fun processMainTasks() {
        val attemptedTasks = linkedSetOf<String>()
        val attemptedRewards = linkedSetOf<String>()
        repeat(MAX_TASK_PASSES) {
            val snapshot = AiFishProtocol.parseTasks(
                gateway.listTasks(AiFishProtocol.MAIN_SCENE)
            )
            if (!snapshot.recognized) {
                events += "AI摸鱼主任务结构未知"
                return
            }
            var attemptedInPass = false
            snapshot.tasks.forEach { task ->
                when {
                    task.status.equals("FINISHED", true) &&
                        attemptedRewards.add(task.taskType) -> {
                        attemptedInPass = true
                        claimAndConfirm(task)
                    }

                    task.status.equals("TODO", true) &&
                        attemptedTasks.add(task.taskType) -> {
                        attemptedInPass = true
                        finishAndConfirm(task, attemptedRewards)
                    }
                }
            }
            if (!attemptedInPass) {
                return
            }
        }
        events += "AI摸鱼主任务达到轮询上限"
    }

    private fun finishAndConfirm(
        task: AiFishTask,
        attemptedRewards: MutableSet<String>
    ) {
        events += "AI摸鱼任务等待${task.waitSeconds}秒[${task.title}]"
        gateway.waitMillis(task.waitSeconds * 1000L)
        if (
            !AiFishProtocol.isActionAccepted(
                gateway.finishTask(task.sceneCode, task.taskType)
            )
        ) {
            events += "AI摸鱼任务完成未受理[${task.title}]"
            return
        }
        val after = queryMainTask(task.taskType)
        when {
            after?.status.equals("FINISHED", true) -> {
                completedTaskCount++
                events += "AI摸鱼任务完成已确认[${task.title}]"
                if (attemptedRewards.add(task.taskType)) {
                    claimAndConfirm(after ?: task)
                }
            }

            after?.status.equals("RECEIVED", true) -> {
                completedTaskCount++
                events += "AI摸鱼任务已直接领取[${task.title}]"
            }

            else -> events += "AI摸鱼任务状态未推进[${task.title}]"
        }
    }

    private fun claimAndConfirm(task: AiFishTask) {
        if (
            !AiFishProtocol.isActionAccepted(
                gateway.receiveTaskAward(task.sceneCode, task.taskType)
            )
        ) {
            events += "AI摸鱼奖励领取未受理[${task.title}]"
            return
        }
        val after = queryMainTask(task.taskType)
        if (after?.status.equals("RECEIVED", true)) {
            receivedRewardCount++
            events += "AI摸鱼奖励领取已确认[${task.title}]"
        } else {
            events += "AI摸鱼奖励状态未推进[${task.title}]"
        }
    }

    private fun queryMainTask(taskType: String): AiFishTask? {
        val snapshot = AiFishProtocol.parseTasks(
            gateway.listTasks(AiFishProtocol.MAIN_SCENE)
        )
        if (!snapshot.recognized) {
            return null
        }
        return AiFishProtocol.findTask(snapshot, taskType)
    }

    private fun touchAvailableFish(): Int {
        var before = AiFishProtocol.parseHome(gateway.queryHome())
        if (!before.recognized) {
            events += "AI摸鱼主页复查结构未知"
            return 0
        }
        var touchCount = 0
        repeat(MAX_TOUCH_COUNT) {
            if ((before.remainTouchChance ?: 0) <= 0) {
                return touchCount
            }
            val response = gateway.touchFish()
            if (!AiFishProtocol.isActionAccepted(response)) {
                events += "AI摸鱼动作未受理"
                return touchCount
            }
            val after = AiFishProtocol.parseHome(response)
            if (!after.recognized) {
                events += "AI摸鱼响应结构未知"
                return touchCount
            }
            val progressed =
                (after.touchTotal ?: -1) > (before.touchTotal ?: -1) ||
                    (after.remainTouchChance ?: Int.MAX_VALUE) <
                    (before.remainTouchChance ?: Int.MAX_VALUE)
            if (!progressed) {
                events += "AI摸鱼状态无进展"
                return touchCount
            }
            touchCount++
            events += "AI摸鱼成功，累计${after.touchTotal ?: 0}次"
            before = after
        }
        events += "AI摸鱼达到单轮上限"
        return touchCount
    }

    private fun result(
        available: Boolean,
        rescued: Boolean = false,
        touchCount: Int = 0
    ): AiFishRunResult {
        return AiFishRunResult(
            available = available,
            rescued = rescued,
            completedTaskCount = completedTaskCount,
            receivedRewardCount = receivedRewardCount,
            touchCount = touchCount,
            events = events.toList()
        )
    }

    private companion object {
        const val MAX_TASK_PASSES = 50
        const val MAX_TOUCH_COUNT = 20
    }
}
