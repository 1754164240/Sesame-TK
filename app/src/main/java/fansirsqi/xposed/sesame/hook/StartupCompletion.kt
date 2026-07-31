package fansirsqi.xposed.sesame.hook

internal object StartupCompletion {
    fun launchInitialTask(launch: () -> Unit) {
        launch()
    }
}
