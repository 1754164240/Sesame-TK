package fansirsqi.xposed.sesame.ui.web

import fansirsqi.xposed.sesame.entity.MapperEntity
import fansirsqi.xposed.sesame.model.ModelField
import org.json.JSONException

enum class SettingsEditorType {
    BOOLEAN,
    INTEGER,
    TEXT,
    CHOICE,
    TIME_POINTS,
    TIME_RANGES,
    LIST,
    SELECT,
    SELECT_AND_COUNT,
    UNKNOWN
}

data class SettingsFieldUiContract(
    val editorType: SettingsEditorType,
    val allowDisable: Boolean,
    val disabled: Boolean,
    val values: List<String>,
    val defaultValues: List<String>
)

data class SettingsListItemUiContract(
    val id: String,
    val name: String,
    val tags: List<String> = emptyList()
)

object SettingsUiContract {
    private val timePointCodes = setOf(
        "execAtTimeList",
        "wakenAtTimeList"
    )
    private val timeRangeCodes = setOf(
        "energyTime",
        "modelSleepTime"
    )
    private val safeExactTags = setOf(
        "全局黑名单",
        "已移除",
        "已选择",
        "不可用"
    )
    private val timePointPattern = Regex("""^\d{4}$""")
    private val timeRangePattern = Regex("""^\d{4}-\d{4}$""")
    private val capabilityTagPattern =
        Regex("""^能力:[A-Za-z0-9_.-]{1,48}=(开放|未开放|不可用|未知)$""")
    private val relationTagPattern =
        Regex("""^关系:(自己|互为好友|单向好友|已移除|未知)$""")

    @JvmStatic
    fun describeField(
        code: String,
        name: String,
        type: String,
        configValue: String?,
        defaultConfigValue: String?
    ): SettingsFieldUiContract {
        val rawValues = splitValues(configValue)
        val defaultValues = splitValues(defaultConfigValue)
        val editorType = resolveEditorType(
            code,
            name,
            type,
            rawValues.ifEmpty { defaultValues }
        )
        val allowDisable = editorType == SettingsEditorType.TIME_POINTS ||
            editorType == SettingsEditorType.TIME_RANGES
        val disabled = allowDisable && rawValues == listOf("-1")
        return SettingsFieldUiContract(
            editorType = editorType,
            allowDisable = allowDisable,
            disabled = disabled,
            values = if (disabled) emptyList() else rawValues,
            defaultValues = defaultValues.filterNot { it == "-1" }
        )
    }

    @JvmStatic
    fun describeField(
        modelField: ModelField<*>
    ): SettingsFieldUiContract {
        return describeField(
            code = modelField.code.orEmpty(),
            name = modelField.name.orEmpty(),
            type = modelField.type.orEmpty(),
            configValue = modelField.configValue,
            defaultConfigValue = defaultConfigValue(modelField)
        )
    }

    @JvmStatic
    @Throws(JSONException::class)
    fun listItems(
        modelField: ModelField<*>
    ): List<SettingsListItemUiContract> {
        val expandValue = modelField.expandValue
        if (expandValue !is Iterable<*>) {
            return emptyList()
        }
        return expandValue.mapNotNull { value ->
            val item = value as? MapperEntity ?: return@mapNotNull null
            val id = item.id.trim()
            if (id.isEmpty()) {
                return@mapNotNull null
            }
            SettingsListItemUiContract(
                id = id,
                name = item.name.trim().ifEmpty { id }
            )
        }.distinctBy { it.id }
    }

    @JvmStatic
    fun safeTags(tags: Iterable<String>): List<String> {
        return tags.map(String::trim)
            .filter { tag ->
                tag in safeExactTags ||
                    relationTagPattern.matches(tag) ||
                    capabilityTagPattern.matches(tag)
            }
            .distinct()
    }

    private fun resolveEditorType(
        code: String,
        name: String,
        type: String,
        values: List<String>
    ): SettingsEditorType {
        val normalizedType = type.uppercase()
        if (
            code in timePointCodes ||
            name.contains("定时执行") ||
            name.contains("定时唤醒")
        ) {
            return SettingsEditorType.TIME_POINTS
        }
        if (
            code in timeRangeCodes ||
            values.filterNot { it == "-1" }.any(timeRangePattern::matches)
        ) {
            return SettingsEditorType.TIME_RANGES
        }
        if (
            normalizedType == "LIST" &&
            values.filterNot { it == "-1" }.isNotEmpty() &&
            values.filterNot { it == "-1" }.all(timePointPattern::matches)
        ) {
            return SettingsEditorType.TIME_POINTS
        }
        return when (normalizedType) {
            "BOOLEAN" -> SettingsEditorType.BOOLEAN
            "INTEGER", "MULTIPLY_INTEGER" ->
                SettingsEditorType.INTEGER
            "STRING", "TEXT", "URL_TEXT", "READ_ONLY_TEXT" ->
                SettingsEditorType.TEXT
            "CHOICE" -> SettingsEditorType.CHOICE
            "LIST" -> SettingsEditorType.LIST
            "SELECT", "SELECT_ONE" -> SettingsEditorType.SELECT
            "SELECT_AND_COUNT", "SELECT_AND_COUNT_ONE" ->
                SettingsEditorType.SELECT_AND_COUNT
            else -> SettingsEditorType.UNKNOWN
        }
    }

    private fun splitValues(value: String?): List<String> {
        return value.orEmpty()
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
    }

    private fun defaultConfigValue(modelField: ModelField<*>): String {
        val defaultValue = modelField.defaultValue ?: return ""
        return if (
            modelField.type.equals("LIST", true) &&
            defaultValue is Iterable<*>
        ) {
            defaultValue.joinToString(",") { it.toString() }
        } else {
            defaultValue.toString()
        }
    }
}
