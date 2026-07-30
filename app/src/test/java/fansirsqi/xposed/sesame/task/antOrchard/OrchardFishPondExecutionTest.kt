package fansirsqi.xposed.sesame.task.antOrchard

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OrchardFishPondExecutionTest {

    @Test
    fun `农场主体抛错时仍执行鱼池和金豆`() = runBlocking {
        val events = mutableListOf<String>()

        OrchardFishPondExecution.run(
            orchardBlock = {
                events += "orchard"
                error("农场执行失败")
            },
            fishPondBlock = {
                events += "fishpond"
            },
            goldenBeanBlock = {
                events += "goldenBean"
            }
        )

        assertEquals(listOf("orchard", "fishpond", "goldenBean"), events)
    }

    @Test
    fun `鱼池抛错时仍执行金豆`() = runBlocking {
        val events = mutableListOf<String>()

        OrchardFishPondExecution.run(
            orchardBlock = {
                events += "orchard"
            },
            fishPondBlock = {
                events += "fishpond"
                error("鱼池执行失败")
            },
            goldenBeanBlock = {
                events += "goldenBean"
            }
        )

        assertEquals(listOf("orchard", "fishpond", "goldenBean"), events)
    }

    @Test
    fun `协程取消后不再执行后续阶段`() {
        val events = mutableListOf<String>()

        assertThrows(CancellationException::class.java) {
            runBlocking {
                OrchardFishPondExecution.run(
                    orchardBlock = {
                        events += "orchard"
                        throw CancellationException("任务已取消")
                    },
                    fishPondBlock = {
                        events += "fishpond"
                    },
                    goldenBeanBlock = {
                        events += "goldenBean"
                    }
                )
            }
        }

        assertEquals(listOf("orchard"), events)
    }
}
