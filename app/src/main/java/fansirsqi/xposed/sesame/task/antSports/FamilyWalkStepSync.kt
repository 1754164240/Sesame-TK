package fansirsqi.xposed.sesame.task.antSports

import org.json.JSONObject

interface FamilyWalkStepSyncClient {
    fun queryDonationSteps(): String
    fun walkDonateSignInfo(step: Int): String
    fun donateWalkHome(step: Int): String
}

enum class FamilyWalkStepSyncOutcome {
    VERIFIED,
    UNVERIFIED,
    FAILED
}

data class FamilyWalkStepSyncResult(
    val outcome: FamilyWalkStepSyncOutcome,
    val verifiedStep: Int
)

object FamilyWalkStepSync {
    fun sync(
        targetStep: Int,
        client: FamilyWalkStepSyncClient
    ): FamilyWalkStepSyncResult {
        return try {
            val currentStep = parseStep(client.queryDonationSteps())
                ?: return failed()
            if (currentStep >= targetStep) {
                return verified(currentStep)
            }

            if (!isSuccessful(client.walkDonateSignInfo(targetStep))) {
                return failed(currentStep)
            }
            if (!isSuccessful(client.donateWalkHome(targetStep))) {
                return failed(currentStep)
            }

            val verifiedStep = parseStep(client.queryDonationSteps())
                ?: return failed(currentStep)
            if (verifiedStep >= targetStep) {
                verified(verifiedStep)
            } else {
                FamilyWalkStepSyncResult(
                    outcome = FamilyWalkStepSyncOutcome.UNVERIFIED,
                    verifiedStep = verifiedStep
                )
            }
        } catch (_: Exception) {
            failed()
        }
    }

    private fun parseStep(response: String): Int? {
        if (response.isBlank()) {
            return null
        }
        val result = JSONObject(response)
        if (!isSuccessful(result)) {
            return null
        }
        return result.optInt("stepCount", -1).takeIf { it >= 0 }
    }

    private fun isSuccessful(response: String): Boolean {
        return response.isNotBlank() && isSuccessful(JSONObject(response))
    }

    private fun isSuccessful(result: JSONObject): Boolean {
        if (result.has("success")) {
            return result.optBoolean("success")
        }
        if (result.has("isSuccess")) {
            return result.optBoolean("isSuccess")
        }
        return result.optString("resultCode") in setOf("SUCCESS", "100", "200")
    }

    private fun verified(step: Int): FamilyWalkStepSyncResult {
        return FamilyWalkStepSyncResult(FamilyWalkStepSyncOutcome.VERIFIED, step)
    }

    private fun failed(step: Int = -1): FamilyWalkStepSyncResult {
        return FamilyWalkStepSyncResult(FamilyWalkStepSyncOutcome.FAILED, step)
    }
}
