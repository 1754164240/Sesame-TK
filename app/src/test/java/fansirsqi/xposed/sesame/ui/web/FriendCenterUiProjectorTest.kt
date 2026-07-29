package fansirsqi.xposed.sesame.ui.web

import fansirsqi.xposed.sesame.entity.friend.FriendCapabilityFilter
import fansirsqi.xposed.sesame.entity.friend.FriendCapabilityState
import fansirsqi.xposed.sesame.entity.friend.FriendCenterConfig
import fansirsqi.xposed.sesame.entity.friend.FriendGroup
import fansirsqi.xposed.sesame.entity.friend.FriendModuleCapability
import fansirsqi.xposed.sesame.entity.friend.FriendProfile
import fansirsqi.xposed.sesame.entity.friend.FriendRelation
import fansirsqi.xposed.sesame.entity.friend.FriendRelationFilter
import fansirsqi.xposed.sesame.entity.friend.FriendSelectionScope
import fansirsqi.xposed.sesame.entity.friend.FriendSelectionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendCenterUiProjectorTest {

    @Test
    fun `动态全部好友按排除关系能力和黑名单投影`() {
        val config = config()
        val spec = FriendSelectionSpec(
            selectionScope = FriendSelectionScope.ALL_FRIENDS,
            excludeGroupIds = linkedSetOf("g-one-way"),
            relationFilter = FriendRelationFilter.ALL_KNOWN,
            capabilityFilter = FriendCapabilityFilter(
                moduleKeys = linkedSetOf("forest"),
                requiredStates =
                    linkedSetOf(FriendCapabilityState.OPEN),
                includeUnknown = false
            )
        )

        val projection = FriendCenterUiProjector.project(config, spec)

        assertEquals(4, projection.totalCount)
        assertEquals(1, projection.effectiveCount)
        assertEquals(3, projection.invalidCount)
        assertEquals(listOf("100"), projection.effectiveUserIds)
        assertEquals(
            listOf("g-main", "g-one-way"),
            projection.groups.map { it.id }
        )
    }

    @Test
    fun `分组包含和用户排除按稳定顺序预览`() {
        val spec = FriendSelectionSpec(
            includeGroupIds = linkedSetOf("g-main"),
            excludeUserIds = linkedSetOf("200"),
            relationFilter = FriendRelationFilter.ALL_KNOWN
        )

        val projection = FriendCenterUiProjector.project(config(), spec)

        assertEquals(listOf("100"), projection.effectiveUserIds)
        assertEquals(3, projection.totalCount)
        assertEquals(1, projection.effectiveCount)
        assertEquals(2, projection.invalidCount)
    }

    @Test
    fun `未知能力默认排除且投影不暴露来源和原因`() {
        assertFalse(FriendCapabilityFilter().includeUnknown)
        val projection = FriendCenterUiProjector.project(
            config(),
            FriendSelectionSpec(
                includeUserIds = linkedSetOf("200"),
                relationFilter = FriendRelationFilter.ALL_KNOWN,
                capabilityFilter = FriendCapabilityFilter(
                    moduleKeys = linkedSetOf("forest")
                )
            )
        )

        assertTrue(projection.effectiveUserIds.isEmpty())
        val unknown = projection.entries.single()
        assertTrue(unknown.tags.contains("能力:forest=未知"))
        assertFalse(unknown.tags.any { it.contains("抓包") })
        assertFalse(unknown.tags.any { it.contains("RPC") })
    }

    @Test
    fun `旧列表和旧计数配置保持显式选择语义`() {
        val idsProjection = FriendCenterUiProjector.projectLegacyIds(
            config(),
            linkedSetOf("100", "200")
        )
        val countsProjection =
            FriendCenterUiProjector.projectLegacyCounts(
                config(),
                linkedMapOf("100" to 3, "400" to 2)
            )

        assertEquals(
            listOf("100", "200"),
            idsProjection.effectiveUserIds
        )
        assertEquals(
            listOf("100"),
            countsProjection.effectiveUserIds
        )
    }

    @Test
    fun `好友安全标签合并进列表且不暴露内部来源`() {
        val items = listOf(
            SettingsListItemUiContract("100", "甲"),
            SettingsListItemUiContract("200", "乙"),
            SettingsListItemUiContract("outside", "外部条目")
        )

        val decorated = FriendCenterUiProjector.decorateListItems(
            config(),
            items
        )

        assertEquals(
            listOf("关系:互为好友", "能力:forest=开放"),
            decorated[0].tags
        )
        assertEquals(
            listOf("关系:互为好友", "能力:forest=未知"),
            decorated[1].tags
        )
        assertTrue(decorated[2].tags.isEmpty())
        assertFalse(
            decorated.flatMap { it.tags }.any {
                it.contains("抓包") || it.contains("RPC")
            }
        )
    }

    private fun config(): FriendCenterConfig {
        val profiles = linkedMapOf(
            "100" to profile(
                "100",
                "甲",
                capability = FriendCapabilityState.OPEN
            ),
            "200" to profile(
                "200",
                "乙",
                capability = FriendCapabilityState.UNKNOWN,
                source = "抓包",
                reason = "RPC响应缺字段"
            ),
            "300" to profile(
                "300",
                "丙",
                relation = FriendRelation.ONE_WAY,
                capability = FriendCapabilityState.OPEN
            ),
            "400" to profile(
                "400",
                "丁",
                blocked = true,
                capability = FriendCapabilityState.OPEN
            )
        )
        return FriendCenterConfig(
            userId = "owner",
            groups = mutableListOf(
                FriendGroup(
                    id = "g-main",
                    name = "常用",
                    memberIds = linkedSetOf("100", "200", "400")
                ),
                FriendGroup(
                    id = "g-one-way",
                    name = "单向",
                    memberIds = linkedSetOf("300")
                )
            ),
            profiles = profiles
        )
    }

    private fun profile(
        id: String,
        name: String,
        relation: FriendRelation = FriendRelation.MUTUAL,
        blocked: Boolean = false,
        capability: FriendCapabilityState,
        source: String = "",
        reason: String = ""
    ): FriendProfile {
        return FriendProfile(
            userId = id,
            displayName = name,
            relation = relation,
            globalBlocked = blocked,
            capabilities = linkedMapOf(
                "forest" to FriendModuleCapability(
                    state = capability,
                    source = source,
                    reason = reason
                )
            )
        )
    }
}
