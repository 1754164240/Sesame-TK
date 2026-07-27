package fansirsqi.xposed.sesame.task.antForest

object AntForestResponsePolicy {
    fun isAnimalEnergyAlreadyCollected(resultCode: String?): Boolean {
        return resultCode == "ENERGY_HAS_COLLECTED"
    }
}
