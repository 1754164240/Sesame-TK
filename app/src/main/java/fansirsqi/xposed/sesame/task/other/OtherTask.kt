package fansirsqi.xposed.sesame.task.other

import fansirsqi.xposed.sesame.entity.OtherEntityProvider.listCreditOptions
import fansirsqi.xposed.sesame.entity.OtherEntityProvider.listCreditTaskOptions
import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectAndCountModelField
import fansirsqi.xposed.sesame.model.modelFieldExt.SelectModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.task.other.credit2101.Credit2101
import fansirsqi.xposed.sesame.task.other.haojia.HaoJiaWuyou
import fansirsqi.xposed.sesame.util.Log

class OtherTask : ModelTask() {
    override fun getName(): String {
        return "其他任务"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.OTHER
    }

    override fun getIcon(): String {
        return ""
    }

    /** @brief 信用2101 游戏开关 */
    private var credit2101: BooleanModelField? = null

    /** @brief 信用2101 事件列表 */
    private var creditEventOptions: SelectAndCountModelField? = null

    /** @brief 信用2101 任务列表 */
    private var credit2101OTaskOptions: SelectModelField? = null


    /** @brief 好家无忧卡 开关 */
    private var haojiaWuyou: BooleanModelField? = null


    override fun getFields(): ModelFields {
        val fields = ModelFields()
        fields.addField(
            BooleanModelField(
                "credit2101", "信用2101", false
            ).apply { credit2101 = this })



        fields.addField(
            SelectModelField(
                "credit2101Options",
                "信用2101 | 任务选项",
                LinkedHashSet<String?>(),
                listCreditTaskOptions()
            ).also { credit2101OTaskOptions = it })

        fields.addField(
            SelectAndCountModelField(
                "CreditOptions",
                "信用2101 | 事件类型",
                LinkedHashMap<String?, Int?>(),
                listCreditOptions(),
                "设置运行次数(-1为不限制)"
            ).also {
                creditEventOptions = it
            })

        // 新增好家无忧卡开关
        fields.addField(
            BooleanModelField(
                "haojiaWuyou", "好家无忧卡", false
            ).apply { haojiaWuyou = this }
        )

        return fields
    }

    override suspend fun runSuspend() {
        try {
            if (credit2101!!.value) {
                Credit2101.doCredit2101(credit2101OTaskOptions!!,creditEventOptions!!)
            }
            // 执行好家无忧卡任务
            if (haojiaWuyou!!.value) {
                HaoJiaWuyou.start()
            }
        } catch (e: Exception) {
            Log.printStackTrace(TAG, e)
        }
    }

    companion object {
        const val TAG = "OtherTask"
        fun run() {
            // TODO: 添加其他任务
        }
    }
}