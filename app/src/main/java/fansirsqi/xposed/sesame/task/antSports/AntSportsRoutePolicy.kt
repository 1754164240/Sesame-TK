package fansirsqi.xposed.sesame.task.antSports

import org.json.JSONObject

enum class AntSportsRouteOutcome {
    CONFIRMED,
    PARTIAL,
    RETRY,
    SKIPPED_UNSUPPORTED
}

data class AntSportsRouteUserSnapshot(
    val recognized: Boolean,
    val joinedPathId: String
)

data class AntSportsWorldMapSnapshot(
    val recognized: Boolean,
    val onlineCityIds: List<String>
)

data class AntSportsCityPathSnapshot(
    val recognized: Boolean,
    val unfinishedPathIds: List<String>
)

data class AntSportsRouteSnapshot(
    val recognized: Boolean,
    val pathId: String,
    val pathName: String,
    val completion: String,
    val forwardStepCount: Int?,
    val remainStepCount: Int?,
    val minGoStepCount: Int?,
    val pathStepCount: Int?,
    val eventIds: Set<String>
)

object AntSportsRoutePolicy {

    fun isActionAccepted(response: String): Boolean {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return false
        return isSuccess(root)
    }

    fun parseCityPaths(response: String): AntSportsCityPathSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return AntSportsCityPathSnapshot(false, emptyList())
        if (!isSuccess(root)) {
            return AntSportsCityPathSnapshot(false, emptyList())
        }
        val data = root.optJSONObject("data")
            ?: root.optJSONObject("result")
            ?: root
        val paths = data.optJSONArray("cityPathList")
            ?: return AntSportsCityPathSnapshot(false, emptyList())
        val unfinishedPathIds = mutableListOf<String>()
        for (index in 0 until paths.length()) {
            val path = paths.optJSONObject(index) ?: continue
            val pathId = path.optString("pathId")
            if (
                pathId.isNotBlank() &&
                !path.optString("pathCompleteStatus")
                    .equals("COMPLETED", true)
            ) {
                unfinishedPathIds += pathId
            }
        }
        return AntSportsCityPathSnapshot(true, unfinishedPathIds)
    }

    fun parseWorldMap(response: String): AntSportsWorldMapSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return AntSportsWorldMapSnapshot(false, emptyList())
        if (!isSuccess(root)) {
            return AntSportsWorldMapSnapshot(false, emptyList())
        }
        val data = root.optJSONObject("data")
            ?: root.optJSONObject("result")
            ?: root
        val cities = data.optJSONArray("cityList")
            ?: return AntSportsWorldMapSnapshot(false, emptyList())
        val onlineCityIds = mutableListOf<String>()
        for (index in 0 until cities.length()) {
            val city = cities.optJSONObject(index) ?: continue
            val cityId = city.optString("cityId")
            if (
                cityId.isNotBlank() &&
                cityId != "000000" &&
                city.optString("status").equals("ONLINE", true)
            ) {
                onlineCityIds += cityId
            }
        }
        return AntSportsWorldMapSnapshot(true, onlineCityIds)
    }

    fun verifyEvent(
        eventId: String,
        before: AntSportsRouteSnapshot,
        after: AntSportsRouteSnapshot
    ): AntSportsRouteOutcome {
        return if (
            eventId.isNotBlank() &&
            before.recognized &&
            after.recognized &&
            before.pathId == after.pathId &&
            eventId in before.eventIds &&
            eventId !in after.eventIds
        ) {
            AntSportsRouteOutcome.CONFIRMED
        } else {
            AntSportsRouteOutcome.RETRY
        }
    }

    fun verifyJoin(
        targetPathId: String,
        user: AntSportsRouteUserSnapshot,
        path: AntSportsRouteSnapshot
    ): AntSportsRouteOutcome {
        return if (
            targetPathId.isNotBlank() &&
            user.recognized &&
            user.joinedPathId == targetPathId &&
            path.recognized &&
            path.pathId == targetPathId
        ) {
            AntSportsRouteOutcome.CONFIRMED
        } else {
            AntSportsRouteOutcome.RETRY
        }
    }

    fun parseUser(response: String): AntSportsRouteUserSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return AntSportsRouteUserSnapshot(false, "")
        if (!isSuccess(root)) {
            return AntSportsRouteUserSnapshot(false, "")
        }
        val data = root.optJSONObject("data")
            ?: root.optJSONObject("result")
            ?: root
        if (!data.has("joinedPathId")) {
            return AntSportsRouteUserSnapshot(false, "")
        }
        return AntSportsRouteUserSnapshot(
            recognized = true,
            joinedPathId = data.optString("joinedPathId")
        )
    }

    fun verifyWalk(
        before: AntSportsRouteSnapshot,
        after: AntSportsRouteSnapshot
    ): AntSportsRouteOutcome {
        if (
            !before.recognized ||
            !after.recognized ||
            before.pathId != after.pathId
        ) {
            return AntSportsRouteOutcome.RETRY
        }
        val beforeSteps = before.forwardStepCount
            ?: return AntSportsRouteOutcome.RETRY
        val afterSteps = after.forwardStepCount
            ?: return AntSportsRouteOutcome.RETRY
        if (afterSteps <= beforeSteps) {
            return AntSportsRouteOutcome.RETRY
        }
        return if (after.completion.equals("COMPLETED", true)) {
            AntSportsRouteOutcome.CONFIRMED
        } else {
            AntSportsRouteOutcome.PARTIAL
        }
    }

    fun parsePath(response: String): AntSportsRouteSnapshot {
        val root = runCatching { JSONObject(response) }.getOrNull()
            ?: return emptySnapshot()
        if (!isSuccess(root)) {
            return emptySnapshot()
        }
        val data = root.optJSONObject("data")
            ?: root.optJSONObject("result")
            ?: root
        val userPath = data.optJSONObject("userPathStep")
            ?: return emptySnapshot()
        val pathId = userPath.optString("pathId")
        if (pathId.isBlank()) {
            return emptySnapshot()
        }
        val path = data.optJSONObject("path")
        val eventIds = mutableSetOf<String>()
        val events = data.optJSONArray("treasureBoxList")
        if (events != null) {
            for (index in 0 until events.length()) {
                val event = events.optJSONObject(index) ?: continue
                val eventId = event.optString("boxNo")
                    .ifBlank { event.optString("eventBillNo") }
                if (eventId.isNotBlank()) {
                    eventIds += eventId
                }
            }
        }
        return AntSportsRouteSnapshot(
            recognized = true,
            pathId = pathId,
            pathName = userPath.optString("pathName")
                .ifBlank { path?.optString("name").orEmpty() },
            completion = userPath.optString("pathCompleteStatus"),
            forwardStepCount = userPath.optIntOrNull("forwardStepCount"),
            remainStepCount = userPath.optIntOrNull("remainStepCount"),
            minGoStepCount = path?.optIntOrNull("minGoStepCount"),
            pathStepCount = path?.optIntOrNull("pathStepCount"),
            eventIds = eventIds
        )
    }

    private fun JSONObject.optIntOrNull(key: String): Int? {
        if (!has(key) || isNull(key)) {
            return null
        }
        return optString(key).toIntOrNull()
    }

    private fun isSuccess(root: JSONObject): Boolean {
        if (root.has("success")) {
            return root.optBoolean("success", false)
        }
        return root.optString("resultCode").uppercase() in
            setOf("SUCCESS", "100", "200", "0")
    }

    private fun emptySnapshot(): AntSportsRouteSnapshot {
        return AntSportsRouteSnapshot(
            recognized = false,
            pathId = "",
            pathName = "",
            completion = "",
            forwardStepCount = null,
            remainStepCount = null,
            minGoStepCount = null,
            pathStepCount = null,
            eventIds = emptySet()
        )
    }
}
