package fansirsqi.xposed.sesame.task.antMember

import fansirsqi.xposed.sesame.hook.RequestManager
import org.json.JSONArray
import org.json.JSONObject

object YebExpGoldRpcCall {
    private const val SIGN_IN_PLAY_ID = "PLAY102253251"
    private const val CH_INFO =
        "ch_url-https://render.alipay.com/p/yuyan/180020010001282160/index.html"

    fun queryMain(): String = RequestManager.requestString(
        "com.alipay.yebscenebff.needle.yebExpGold.queryMain",
        buildMainQueryArgs()
    )

    fun signIn(): String = RequestManager.requestString(
        "com.alipay.yebscenebff.needle.yebExpGold.signIn",
        buildSignInArgs()
    )

    fun queryVouchers(): String = RequestManager.requestString(
        "alipay.yebprod.query.queryYebTrialCertVoucher",
        buildVoucherQueryArgs()
    )

    fun convertVouchers(): String = RequestManager.requestString(
        "com.alipay.yebscenebff.needle.yebExpGoldVoucherConvert",
        buildVoucherConvertArgs()
    )

    internal fun buildMainQueryArgs(): String {
        val signIn = JSONObject()
            .put("daysOfQuerySignInData", 21)
            .put(
                "displaySignInTextList",
                JSONArray()
                    .put(JSONObject().put("value", "持"))
                    .put(JSONObject().put("value", "续"))
                    .put(JSONObject().put("value", "签"))
                    .put(JSONObject().put("value", "到"))
                    .put(JSONObject().put("value", "可"))
                    .put(JSONObject().put("value", "领"))
                    .put(JSONObject().put("value", ""))
            )
            .put("downgrade", false)
            .put("todayRedDotText", "戳这里")
            .put("tomorrowRedDotText", "")
        val task = JSONObject()
            .put("downgrade", false)
            .put("queryComplete", false)
            .put("strategyCode", "YEB_TRIAL_ASSET_TASK_BLOCK_REC")
        val args = JSONObject()
            .put("chInfo", CH_INFO)
            .put("signIn", signIn)
            .put("task", task)
        return JSONArray().put(args).toString()
    }

    internal fun buildSignInArgs(): String =
        JSONArray()
            .put(JSONObject().put("signInPlayId", SIGN_IN_PLAY_ID))
            .toString()

    internal fun buildVoucherQueryArgs(): String {
        val args = JSONObject()
            .put("component", "PROMO_ACTIVITY")
            .put("sortType", "drawTime")
            .put("source", "QIANAPP")
            .put(
                "voucherTemplateIdList",
                JSONArray()
                    .put("202312260007300180780087H5IR")
                    .put("2026011300073001807800H1558H")
            )
        return JSONArray().put(args).toString()
    }

    internal fun buildVoucherConvertArgs(): String =
        JSONArray()
            .put(
                JSONObject()
                    .put("convertType", "all")
                    .put("isShowExchangeModal", true)
            )
            .toString()
}
