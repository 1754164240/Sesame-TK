package fansirsqi.xposed.sesame.task.antForest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AntForestShieldRetryPolicyTest {
    @Test
    fun `空包领取的保护罩使用失败后同轮不再尝试`() {
        val policy = AntForestShieldRetryPolicy()
        assertTrue(policy.tryBegin(emptySet()))
        policy.recordAttemptedIds(setOf("claimed-shield"))
        policy.finish()
        assertFalse(policy.tryBegin(setOf("claimed-shield")))
        policy.startRound()
        assertTrue(policy.tryBegin(setOf("claimed-shield")))
    }

    @Test
    fun `上一轮迟到的领取结果不污染下一轮道具记录`() {
        val policy = AntForestShieldRetryPolicy()
        assertTrue(policy.tryBegin(emptySet()))
        policy.startRound()
        policy.recordAttemptedIds(setOf("claimed-shield"))
        policy.finish()
        assertTrue(policy.tryBegin(emptySet()))
        policy.finish()
        assertTrue(policy.tryBegin(setOf("claimed-shield")))
    }

    @Test
    fun `空背包同轮只尝试一次下一轮允许重试`() {
        val policy = AntForestShieldRetryPolicy()
        assertTrue(policy.tryBegin(emptySet()))
        policy.finish()
        repeat(89) { assertFalse(policy.tryBegin(emptySet())) }
        policy.startRound()
        assertTrue(policy.tryBegin(emptySet()))
    }

    @Test
    fun `出现新的保护罩可以重试重复背包和无关更新不重试`() {
        val policy = AntForestShieldRetryPolicy()
        assertTrue(policy.tryBegin(emptySet()))
        policy.finish()
        assertTrue(policy.tryBegin(setOf("shield-a")))
        policy.finish()
        assertFalse(policy.tryBegin(setOf("shield-a")))
        assertTrue(policy.tryBegin(setOf("shield-b")))
        policy.finish()
        assertFalse(policy.tryBegin(emptySet()))
    }

    @Test
    fun `并发调用以及轮次重置不能重入在途获取`() {
        val policy = AntForestShieldRetryPolicy()
        assertTrue(policy.tryBegin(emptySet()))
        assertFalse(policy.tryBegin(setOf("shield-a")))
        policy.startRound()
        assertFalse(policy.tryBegin(emptySet()))
        policy.finish()
        assertTrue(policy.tryBegin(emptySet()))
    }

    @Test
    fun `同时到达的能量球只允许一个保护罩流程开始`() {
        val policy = AntForestShieldRetryPolicy()
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val results = (1..8).map {
                executor.submit<Boolean> {
                    start.await()
                    policy.tryBegin(emptySet())
                }
            }
            start.countDown()
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
        } finally {
            executor.shutdownNow()
            policy.finish()
        }
    }
}
