package fansirsqi.xposed.sesame.task.antSports

data class AntSportsRouteDiscovery(
    val recognized: Boolean,
    val pathId: String?
)

class AntSportsRouteWorkflow(
    private val queryUser: suspend () -> String,
    private val queryPath: suspend (String) -> String,
    private val queryWorldMap: suspend (String) -> String,
    private val queryCityPath: suspend (String) -> String,
    private val queryCityKnowledgeDetail: suspend (String) -> String,
    private val joinPath: suspend (String) -> String,
    private val walkGo: suspend (String, Int) -> String,
    private val receiveEvent: suspend (String) -> String
) {

    suspend fun findJoinablePath(themeId: String): AntSportsRouteDiscovery {
        if (themeId.isBlank()) {
            return AntSportsRouteDiscovery(false, null)
        }
        val worldMap = AntSportsRoutePolicy.parseWorldMap(
            queryWorldMap(themeId)
        )
        if (!worldMap.recognized) {
            return AntSportsRouteDiscovery(false, null)
        }
        for (cityId in worldMap.onlineCityIds) {
            val cityPaths = AntSportsRoutePolicy.parseCityPaths(
                queryCityPath(cityId)
            )
            if (!cityPaths.recognized) {
                return AntSportsRouteDiscovery(false, null)
            }
            if (cityPaths.unfinishedPathIds.isEmpty()) {
                continue
            }
            val knowledge = AntSportsRoutePolicy.parseCityKnowledge(
                queryCityKnowledgeDetail(cityId)
            )
            if (!knowledge.recognized) {
                return AntSportsRouteDiscovery(false, null)
            }
            AntSportsRoutePolicy.selectKnowledgePaths(
                cityPaths,
                knowledge
            ).firstOrNull()?.let { pathId ->
                return AntSportsRouteDiscovery(true, pathId)
            }
        }
        return AntSportsRouteDiscovery(true, null)
    }

    suspend fun claimEvent(
        pathId: String,
        eventId: String
    ): AntSportsRouteOutcome {
        if (pathId.isBlank() || eventId.isBlank()) {
            return AntSportsRouteOutcome.RETRY
        }
        val before = AntSportsRoutePolicy.parsePath(queryPath(pathId))
        if (
            !before.recognized ||
            before.pathId != pathId ||
            eventId !in before.eventIds
        ) {
            return AntSportsRouteOutcome.RETRY
        }
        val actionResponse = receiveEvent(eventId)
        if (!AntSportsRoutePolicy.isActionAccepted(actionResponse)) {
            return AntSportsRouteOutcome.RETRY
        }
        val after = AntSportsRoutePolicy.parsePath(queryPath(pathId))
        return AntSportsRoutePolicy.verifyEvent(eventId, before, after)
    }

    suspend fun joinRoute(targetPathId: String): AntSportsRouteOutcome {
        if (targetPathId.isBlank()) {
            return AntSportsRouteOutcome.RETRY
        }
        val before = AntSportsRoutePolicy.parseUser(queryUser())
        if (!before.recognized || before.joinedPathId == targetPathId) {
            return AntSportsRouteOutcome.RETRY
        }
        val actionResponse = joinPath(targetPathId)
        if (!AntSportsRoutePolicy.isActionAccepted(actionResponse)) {
            return AntSportsRouteOutcome.RETRY
        }
        val user = AntSportsRoutePolicy.parseUser(queryUser())
        val path = AntSportsRoutePolicy.parsePath(
            queryPath(targetPathId)
        )
        return AntSportsRoutePolicy.verifyJoin(
            targetPathId,
            user,
            path
        )
    }

    suspend fun advancePath(
        pathId: String,
        useStepCount: Int
    ): AntSportsRouteOutcome {
        if (pathId.isBlank() || useStepCount <= 0) {
            return AntSportsRouteOutcome.RETRY
        }
        val before = AntSportsRoutePolicy.parsePath(queryPath(pathId))
        if (
            !before.recognized ||
            before.pathId != pathId ||
            before.forwardStepCount == null
        ) {
            return AntSportsRouteOutcome.RETRY
        }
        val actionResponse = walkGo(pathId, useStepCount)
        if (!AntSportsRoutePolicy.isActionAccepted(actionResponse)) {
            return AntSportsRouteOutcome.RETRY
        }
        val after = AntSportsRoutePolicy.parsePath(queryPath(pathId))
        return AntSportsRoutePolicy.verifyWalk(before, after)
    }
}
