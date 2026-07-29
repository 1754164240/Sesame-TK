package fansirsqi.xposed.sesame.ui.web

import fansirsqi.xposed.sesame.model.modelFieldExt.ListModelField
import fansirsqi.xposed.sesame.ui.dto.ModelFieldShowDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiContractTest {

    @Test
    fun `时间点字段使用结构化编辑并支持禁用`() {
        val contract = SettingsUiContract.describeField(
            code = "execAtTimeList",
            name = "定时执行",
            type = "LIST",
            configValue = "0010,0700,2359",
            defaultConfigValue = "0010,0700"
        )

        assertEquals(SettingsEditorType.TIME_POINTS, contract.editorType)
        assertTrue(contract.allowDisable)
        assertFalse(contract.disabled)
        assertEquals(listOf("0010", "0700", "2359"), contract.values)
        assertEquals(listOf("0010", "0700"), contract.defaultValues)
    }

    @Test
    fun `时间窗口字段识别范围和禁用值`() {
        val range = SettingsUiContract.describeField(
            code = "energyTime",
            name = "只收能量时间",
            type = "LIST",
            configValue = "0700-0730,2200-0030",
            defaultConfigValue = "0700-0730"
        )
        val disabled = SettingsUiContract.describeField(
            code = "modelSleepTime",
            name = "模块休眠时间",
            type = "LIST",
            configValue = "-1",
            defaultConfigValue = "0200-0201"
        )

        assertEquals(SettingsEditorType.TIME_RANGES, range.editorType)
        assertEquals(
            listOf("0700-0730", "2200-0030"),
            range.values
        )
        assertTrue(disabled.allowDisable)
        assertTrue(disabled.disabled)
        assertTrue(disabled.values.isEmpty())
    }

    @Test
    fun `普通列表和计数列表使用不同编辑器`() {
        assertEquals(
            SettingsEditorType.SELECT,
            SettingsUiContract.describeField(
                "friends",
                "好友",
                "SELECT",
                """["100"]""",
                "[]"
            ).editorType
        )
        assertEquals(
            SettingsEditorType.SELECT_AND_COUNT,
            SettingsUiContract.describeField(
                "water",
                "浇水次数",
                "SELECT_AND_COUNT",
                """{"100":3}""",
                "{}"
            ).editorType
        )
        assertEquals(
            SettingsEditorType.LIST,
            SettingsUiContract.describeField(
                "keywords",
                "关键词",
                "LIST",
                "森林,庄园",
                ""
            ).editorType
        )
    }

    @Test
    fun `列表标签只保留约定的安全标签`() {
        val tags = SettingsUiContract.safeTags(
            listOf(
                "关系:互为好友",
                "能力:forest=开放",
                "全局黑名单",
                "已移除",
                "<script>alert(1)</script>",
                "内部原因:RPC响应"
            )
        )

        assertEquals(
            listOf(
                "关系:互为好友",
                "能力:forest=开放",
                "全局黑名单",
                "已移除"
            ),
            tags
        )
    }

    @Test
    fun `字段DTO携带编辑器禁用和默认值合同`() {
        val field = ListModelField.ListJoinCommaToStringModelField(
            "execAtTimeList",
            "定时执行",
            arrayListOf("0010", "0700")
        )
        field.setConfigValue("-1")

        val dto = ModelFieldShowDto.toShowDto(field)

        assertEquals("TIME_POINTS", dto.editorType)
        assertTrue(dto.isAllowDisable)
        assertTrue(dto.isDisabled)
        assertEquals("0010,0700", dto.defaultConfigValue)
    }
}
