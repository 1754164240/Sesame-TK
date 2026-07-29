package fansirsqi.xposed.sesame.task.youthPrivilege

import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.data.StatusFlags
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.task.antForest.AntForestRpcCall
import fansirsqi.xposed.sesame.util.Log
import java.util.Calendar

class YouthPrivilege : ModelTask() {
    private val checkIn =
        BooleanModelField(
            "youthPrivilegeCheckIn",
            "青春特权 | 签到红包",
            false
        )
    private val forestProps =
        BooleanModelField(
            "youthPrivilegeForestProps",
            "青春特权 | 免费森林道具",
            false
        )
    private val tasks =
        BooleanModelField(
            "youthPrivilegeTasks",
            "青春特权 | 任务总览",
            false
        )

    override fun getName(): String = "青春特权"

    override fun getGroup(): ModelGroup = ModelGroup.MEMBER

    override fun getIcon(): String = "AntMember.png"

    override fun getFields(): ModelFields {
        return ModelFields().apply {
            addField(checkIn)
            addField(forestProps)
            addField(tasks)
        }
    }

    override fun runJava() {
        if (checkIn.value == true) {
            checkInFromForest()
        }
        if (forestProps.value == true) {
            claimForestPropsFromForest()
        }
        if (tasks.value == true) {
            queryTasksFromModule()
        }
    }

    companion object {
        private const val TAG = "YouthPrivilege"
        private const val CHECK_IN_START_HOUR = 5
        private val workflowLock = Any()

        @JvmStatic
        fun checkInFromForest(): Boolean {
            synchronized(workflowLock) {
                if (
                    Status.hasFlagToday(
                        StatusFlags.FLAG_YOUTH_PRIVILEGE_CHECK_IN
                    )
                ) {
                    return true
                }
                if (
                    Calendar.getInstance().get(Calendar.HOUR_OF_DAY) <
                    CHECK_IN_START_HOUR
                ) {
                    Log.record(TAG, "5点前不执行青春特权签到")
                    return false
                }

                val result = workflow().checkIn {
                    Status.setFlagToday(
                        StatusFlags.FLAG_YOUTH_PRIVILEGE_CHECK_IN
                    )
                }
                when {
                    result.confirmed ->
                        Log.forest("青春特权🧧签到已由服务端确认")
                    result.retryNeeded ->
                        Log.record(TAG, "青春特权签到未确认，等待后续重试")
                }
                return result.confirmed
            }
        }

        @JvmStatic
        fun claimForestPropsFromForest(): Boolean {
            synchronized(workflowLock) {
                if (
                    Status.hasFlagToday(
                        StatusFlags.FLAG_YOUTH_PRIVILEGE_FOREST_PROPS
                    )
                ) {
                    return true
                }

                val result = workflow().claimForestProps {
                    Status.setFlagToday(
                        StatusFlags.FLAG_YOUTH_PRIVILEGE_FOREST_PROPS
                    )
                }
                if (result.claimedCount > 0) {
                    Log.forest(
                        "青春特权🌸已请求领取${result.claimedCount}项免费森林道具"
                    )
                }
                if (result.retryNeeded) {
                    Log.record(TAG, "青春特权森林道具未全部确认，等待后续重试")
                }
                return result.confirmed
            }
        }

        @JvmStatic
        fun queryTasksFromModule(): Boolean {
            synchronized(workflowLock) {
                if (
                    Status.hasFlagToday(
                        StatusFlags.FLAG_YOUTH_PRIVILEGE_TASKS_QUERIED
                    )
                ) {
                    return true
                }

                val successful = workflow().queryTaskOverview()
                if (successful) {
                    Status.setFlagToday(
                        StatusFlags.FLAG_YOUTH_PRIVILEGE_TASKS_QUERIED
                    )
                    Log.record(TAG, "青春特权任务总览查询成功，未自动执行任务")
                } else {
                    Log.record(TAG, "青春特权任务总览查询失败，等待后续重试")
                }
                return successful
            }
        }

        private fun workflow(): YouthPrivilegeWorkflow {
            return YouthPrivilegeWorkflow(AntForestYouthPrivilegeRpcGateway())
        }
    }
}

private class AntForestYouthPrivilegeRpcGateway :
    YouthPrivilegeRpcGateway {
    override fun queryCheckIn(): String =
        AntForestRpcCall.studentQqueryCheckInModel()

    override fun executeCheckIn(): String =
        AntForestRpcCall.studentCheckin()

    override fun queryForestReward(queryTaskType: String): String =
        AntForestRpcCall.queryTaskListV2(queryTaskType)

    override fun claimForestReward(rewardTaskType: String): String =
        AntForestRpcCall.receiveTaskAwardV2(rewardTaskType)
}
