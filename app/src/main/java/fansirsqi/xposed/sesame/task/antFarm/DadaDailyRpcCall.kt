package fansirsqi.xposed.sesame.task.antFarm

import fansirsqi.xposed.sesame.hook.RequestManager.requestString

/**
 * @author Constanline
 * @since 2023/08/04
 */
object DadaDailyRpcCall {
    fun home(activityId: String?): String {
        return requestString(
            "com.alipay.reading.game.dadaDaily.home",
            "[{\"activityId\":$activityId,\"dadaVersion\":\"1.3.0\",\"version\":1}]"
        )
    }

    fun submit(activityId: String?, answer: String?, questionId: Long?): String {
        return requestString(
            "com.alipay.reading.game.dadaDaily.submit",
            "[{\"activityId\":$activityId,\"answer\":\"$answer\",\"dadaVersion\":\"1.3.0\",\"questionId\":$questionId,\"version\":1}]"
        )
    }
}
