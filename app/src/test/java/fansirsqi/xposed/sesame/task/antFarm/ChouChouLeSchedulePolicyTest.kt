package fansirsqi.xposed.sesame.task.antFarm

import org.junit.Assert.assertEquals
import org.junit.Test

class ChouChouLeSchedulePolicyTest {

    @Test
    fun `已到执行时间时不等待游戏改分直接执行抽抽乐`() {
        assertEquals(
            ChouChouLeScheduleAction.RUN,
            ChouChouLeSchedulePolicy.actionFor(
                completedToday = false,
                timeReached = true
            )
        )
    }

    @Test
    fun `未到执行时间时等待`() {
        assertEquals(
            ChouChouLeScheduleAction.WAIT_FOR_TIME,
            ChouChouLeSchedulePolicy.actionFor(
                completedToday = false,
                timeReached = false
            )
        )
    }

    @Test
    fun `今日已完成时跳过`() {
        assertEquals(
            ChouChouLeScheduleAction.SKIP_COMPLETED,
            ChouChouLeSchedulePolicy.actionFor(
                completedToday = true,
                timeReached = true
            )
        )
    }
}
