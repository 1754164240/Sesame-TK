package fansirsqi.xposed.sesame.task.other

import fansirsqi.xposed.sesame.model.ModelFields
import fansirsqi.xposed.sesame.model.ModelGroup
import fansirsqi.xposed.sesame.model.modelFieldExt.BooleanModelField
import fansirsqi.xposed.sesame.task.ModelTask
import fansirsqi.xposed.sesame.task.other.credit2101.Credit2101
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.Log.record

class OtherTask : ModelTask() {
    override fun getName(): String {
        return "其他任务"
    }

    override fun getGroup(): ModelGroup {
        return ModelGroup.OTHER
    }


    override fun check(): Boolean {
        return true
    }

    override fun getIcon(): String {
        return ""
    }

    private var credit2101: BooleanModelField? = null
    override fun getFields(): ModelFields {
        val fields = ModelFields()
        fields.addField(
            BooleanModelField(
                "credit2101", "信用2101", false
            ).apply { credit2101 = this })
        return fields
    }

    override suspend fun runSuspend() {
        try {
            if (credit2101!!.value) {
                Credit2101.doCredit2101()
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