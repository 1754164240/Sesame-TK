package fansirsqi.xposed.sesame.task.antMember

import org.junit.Assert.assertEquals
import org.junit.Test

class MemberTaskSafetyPolicyTest {

    @Test
    fun `未知任务配置不自动执行`() {
        assertEquals(
            MemberTaskDecision.SKIP_UNSUPPORTED,
            MemberTaskSafetyPolicy.classify(
                MemberTaskCandidate(
                    configId = "unknown",
                    title = "未知任务",
                    targetBusiness = "BROWSE#15S#biz-param"
                )
            )
        )
    }

    @Test
    fun `白名单浏览任务允许执行`() {
        assertEquals(
            MemberTaskDecision.EXECUTE_BROWSE,
            MemberTaskSafetyPolicy.classify(
                MemberTaskCandidate(
                    configId = "600202500151482",
                    title = "浏览会员会场",
                    targetBusiness = "BROWSE#15S#biz-param"
                )
            )
        )
    }

    @Test
    fun `CALL_APP任务只允许回查`() {
        assertEquals(
            MemberTaskDecision.VERIFY_ONLY,
            MemberTaskSafetyPolicy.classify(
                MemberTaskCandidate(
                    configId = "600202500151482",
                    title = "访问会员会场",
                    targetBusiness = "CALL_APP#member-scene"
                )
            )
        )
    }

    @Test
    fun `金融动作优先于白名单被阻断`() {
        assertEquals(
            MemberTaskDecision.SKIP_FINANCIAL,
            MemberTaskSafetyPolicy.classify(
                MemberTaskCandidate(
                    configId = "600202500151482",
                    title = "去借一笔并领取奖励",
                    targetBusiness = "BROWSE#15S#loan"
                )
            )
        )
    }

    @Test
    fun `所有广告任务都禁止伪完成`() {
        assertEquals(
            MemberTaskDecision.SKIP_AD,
            MemberTaskSafetyPolicy.classify(
                MemberTaskCandidate(
                    configId = "32002001",
                    title = "浏览精选好物",
                    targetBusiness = "BROWSE#15S#ad-param",
                    adBizId = "ad-biz"
                )
            )
        )
        assertEquals(
            MemberTaskDecision.SKIP_AD,
            MemberTaskSafetyPolicy.classify(
                MemberTaskCandidate(
                    configId = "unknown-ad",
                    title = "浏览未知广告",
                    targetBusiness = "BROWSE#15S#ad-param",
                    adBizId = "ad-biz"
                )
            )
        )
    }
}
