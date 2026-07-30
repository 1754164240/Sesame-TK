package fansirsqi.xposed.sesame.task.antOrchard

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OrchardFishPondExecutionTest {

    @Test
    fun `农场主体抛错时仍执行鱼池`() = runBlocking {
        val events = mutableListOf<String>()

        runCatching {
            OrchardFishPondExecution.run(
                orchardBlock = {
                    events += "orchard"
                    error("农场执行失败")
                },
                fishPondBlock = {
                    events += "fishpond"
                }
            )
        }

        assertEquals(listOf("orchard", "fishpond"), events)
    }
}
