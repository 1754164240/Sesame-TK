package fansirsqi.xposed.sesame.task.antForest

object AntForestResponsePolicy {
    fun shouldCollectRobExpandEnergy(
        leftEnergy: Double,
        threshold: Int,
        overLimitToday: Boolean
    ): Boolean {
        return leftEnergy >= threshold || (overLimitToday && leftEnergy >= 1.0)
    }

    fun isAnimalEnergyAlreadyCollected(resultCode: String?): Boolean {
        return resultCode == "ENERGY_HAS_COLLECTED"
    }
}
