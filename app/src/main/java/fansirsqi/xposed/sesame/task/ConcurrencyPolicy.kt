package fansirsqi.xposed.sesame.task

object ConcurrencyPolicy {
    fun task(value: Int): Int = value.coerceIn(1, 8)

    fun forest(value: Int): Int = value.coerceIn(1, 100)
}
