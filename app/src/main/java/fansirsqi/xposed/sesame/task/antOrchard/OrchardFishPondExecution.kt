package fansirsqi.xposed.sesame.task.antOrchard

object OrchardFishPondExecution {

    suspend fun run(
        orchardBlock: suspend () -> Unit,
        fishPondBlock: suspend () -> Unit
    ) {
        try {
            orchardBlock()
        } finally {
            fishPondBlock()
        }
    }
}
