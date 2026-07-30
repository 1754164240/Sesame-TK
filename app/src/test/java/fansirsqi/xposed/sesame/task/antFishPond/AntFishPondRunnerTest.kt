package fansirsqi.xposed.sesame.task.antFishPond

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class AntFishPondRunnerTest {

    @Test(expected = CancellationException::class)
    fun `鱼池执行边界必须重抛协程取消`() = runBlocking {
        AntFishPondRunner.runGuarded {
            throw CancellationException("任务已取消")
        }
    }

    @Test
    fun `鱼池执行边界隔离普通业务异常`() = runBlocking {
        var reached = false

        AntFishPondRunner.runGuarded {
            reached = true
            error("普通业务异常")
        }

        assertTrue(reached)
    }
}
