package fansirsqi.xposed.sesame.task.antForest

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForestPatrolPolicyTest {

    @Test
    fun `图鉴只保留常驻在线动物`() {
        val config = JSONObject()
            .put(
                "animals",
                JSONArray()
                    .put(animal(1, "ONLINE"))
                    .put(animal(2, "OFFLINE"))
                    .put(animal(3, "ONLINE").put("limited", true))
                    .put(
                        animal(4, "ONLINE").put(
                            "extInfo",
                            JSONObject().put("shortDesc", "节日限定")
                        )
                    )
                    .put(animal(-1, "ONLINE"))
            )

        assertEquals(
            linkedSetOf(1),
            ForestPatrolPolicy.normalOnlineAnimalIds(config)
        )
    }

    @Test
    fun `缺失或空动物目录属于未知数据`() {
        assertFalse(ForestPatrolPolicy.hasRecognizedAnimalCatalog(JSONObject()))
        assertFalse(
            ForestPatrolPolicy.hasRecognizedAnimalCatalog(
                JSONObject().put("animals", JSONArray())
            )
        )
        assertTrue(
            ForestPatrolPolicy.hasRecognizedAnimalCatalog(
                JSONObject().put(
                    "animals",
                    JSONArray().put(animal(1, "ONLINE"))
                )
            )
        )
    }

    @Test
    fun `图鉴数组兼容data和result嵌套容器`() {
        val animalProps = JSONArray().put(
            JSONObject().put("animal", JSONObject().put("id", 1))
        )
        val response = JSONObject().put(
            "data",
            JSONObject().put(
                "result",
                JSONObject().put("animalProps", animalProps)
            )
        )

        assertEquals(
            animalProps.toString(),
            ForestPatrolPolicy.findAnimalProps(response)?.toString()
        )
        assertNull(ForestPatrolPolicy.findAnimalProps(JSONObject()))
    }

    @Test
    fun `只有常驻动物明确缺片时才判定图鉴可推进`() {
        val animalProps = JSONArray()
            .put(
                JSONObject()
                    .put("animal", JSONObject().put("id", 1))
                    .put("pieces", JSONArray().put(JSONObject().put("holdsNum", 1)))
            )
            .put(
                JSONObject()
                    .put("animal", JSONObject().put("id", 2))
                    .put("pieces", JSONArray().put(JSONObject().put("holdsNum", 0)))
            )
            .put(
                JSONObject()
                    .put("animal", JSONObject().put("id", 3))
                    .put("pieces", JSONArray().put(JSONObject().put("holdsNum", 0)))
            )

        assertTrue(
            ForestPatrolPolicy.hasMissingNormalAnimalPieces(
                setOf(1, 2),
                animalProps
            )
        )
        assertFalse(
            ForestPatrolPolicy.hasMissingNormalAnimalPieces(
                setOf(1),
                animalProps
            )
        )
        assertFalse(
            ForestPatrolPolicy.hasMissingNormalAnimalPieces(
                emptySet(),
                animalProps
            )
        )
    }

    @Test
    fun `地图优先缺片再按旧到新选择未完成记录`() {
        val records = listOf(
            ForestPatrolRecordCandidate(
                patrolId = 30,
                startDate = 300,
                hasUnreachedNodes = true
            ),
            ForestPatrolRecordCandidate(
                patrolId = 10,
                startDate = 100,
                hasUnreachedNodes = false
            ),
            ForestPatrolRecordCandidate(
                patrolId = 20,
                startDate = 200,
                hasUnreachedNodes = true
            )
        )

        assertEquals(
            ForestPatrolTarget(10, ForestPatrolTargetReason.MISSING_PIECES),
            ForestPatrolPolicy.selectTarget(records, setOf(10, 30))
        )
        assertEquals(
            ForestPatrolTarget(20, ForestPatrolTargetReason.UNREACHED_NODES),
            ForestPatrolPolicy.selectTarget(records, emptySet())
        )
    }

    @Test
    fun `全部地图完成后选择最新记录且空记录不切换`() {
        val records = listOf(
            ForestPatrolRecordCandidate(20, 200, false),
            ForestPatrolRecordCandidate(10, 100, false),
            ForestPatrolRecordCandidate(30, 300, false)
        )

        assertEquals(
            ForestPatrolTarget(30, ForestPatrolTargetReason.LATEST_LOOP),
            ForestPatrolPolicy.selectTarget(records, emptySet())
        )
        assertNull(ForestPatrolPolicy.selectTarget(emptyList(), emptySet()))
    }

    @Test
    fun `伙伴按库存优先并在同库存时按预计收益排序`() {
        val props = JSONArray()
            .put(prop("zero", 0, 999))
            .put(prop("low-stock", 1, 100))
            .put(prop("same-stock-low-energy", 3, 10))
            .put(prop("same-stock-high-energy", 3, 20))

        assertEquals(
            ForestAnimalDispatchSelection(
                index = 3,
                holdsNum = 3,
                estimatedEnergy = 20
            ),
            ForestPatrolPolicy.selectDispatchAnimal(props)
        )
    }

    @Test
    fun `预计收益兼容字符串扩展字段且无库存时不派遣`() {
        val extInfo = JSONObject()
            .put(
                "robAbility",
                JSONObject()
                    .put("robEnergyInDaily", 15)
                    .put("robEnergyInRound", 25)
            )
            .toString()
        val props = JSONArray()
            .put(
                JSONObject()
                    .put("main", JSONObject().put("holdsNum", 2))
                    .put("partner", JSONObject().put("extInfo", extInfo))
            )

        assertEquals(25, ForestPatrolPolicy.selectDispatchAnimal(props)?.estimatedEnergy)
        assertNull(
            ForestPatrolPolicy.selectDispatchAnimal(
                JSONArray().put(prop("none", 0, 20))
            )
        )
    }

    private fun animal(id: Int, status: String): JSONObject =
        JSONObject()
            .put("id", id)
            .put("status", status)

    private fun prop(name: String, holdsNum: Int, energy: Int): JSONObject =
        JSONObject()
            .put(
                "main",
                JSONObject()
                    .put("holdsNum", holdsNum)
                    .put("propGroup", "group-$name")
                    .put("propType", "type-$name")
            )
            .put(
                "partner",
                JSONObject()
                    .put("name", name)
                    .put(
                        "robAbility",
                        JSONObject().put("robEnergyInDaily", energy)
                    )
            )
}
