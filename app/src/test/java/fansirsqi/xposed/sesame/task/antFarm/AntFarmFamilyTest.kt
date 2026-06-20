package fansirsqi.xposed.sesame.task.antFarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AntFarmFamilyTest {
    private val sourceText: String by lazy {
        File("src/main/java/fansirsqi/xposed/sesame/task/antFarm/AntFarmFamily.kt").readText()
    }

    @Test
    fun `家庭入口不再触发第二次帮喂`() {
        assertFalse(
            sourceText.contains("familyFeedFriendAnimal(familyAnimals, designatedFeedUserIds)")
        )
    }

    @Test
    fun `家庭捐步使用本地每日标记防止重复执行`() {
        assertTrue(sourceText.contains("antFarm::familyWalkDonate"))
        assertTrue(sourceText.contains("Status.hasFlagToday(FAMILY_WALK_DONATE_FLAG)"))
        assertTrue(sourceText.contains("Status.setFlagToday(FAMILY_WALK_DONATE_FLAG)"))
        assertTrue(sourceText.contains("finally"))
    }

    @Test
    fun `家庭请客并发异常本轮跳过不继续重试`() {
        assertTrue(AntFarmFamily.isFamilyEatTogetherConcurrentError("FAMILY27"))
        assertFalse(AntFarmFamily.isFamilyEatTogetherConcurrentError("FAMILY12"))
        assertTrue(sourceText.contains("FAMILY_EAT_RETRY_DELAY_MS"))
        assertTrue(sourceText.contains("isFamilyEatTogetherConcurrentError"))
        assertTrue(sourceText.contains("并发"))
    }
}
