package fansirsqi.xposed.sesame.hook.rpc.intervallimit

import fansirsqi.xposed.sesame.util.GlobalThreadPools
import fansirsqi.xposed.sesame.util.Log

object RpcIntervalLimit {
    private const val TAG = "RpcIntervalLimit"
    private const val DEFAULT_INTERVAL = 500
    private val DEFAULT_INTERVAL_LIMIT = DefaultIntervalLimit(DEFAULT_INTERVAL)
    private val limiter = RpcIntervalLimiter(
        globalLimit = DEFAULT_INTERVAL_LIMIT,
        nowMillis = System::currentTimeMillis,
        sleepMillis = GlobalThreadPools::sleepCompat
    )

    /**
     * 为指定方法添加间隔限制。
     *
     * @param method 方法名称
     * @param interval 间隔时间（毫秒）
     */
    fun addIntervalLimit(method: String, interval: Int) {
        addIntervalLimit(method, DefaultIntervalLimit(interval))
    }

    /**
     * 为指定方法添加自定义间隔限制对象。
     *
     * @param method 方法名称
     * @param intervalLimit 自定义的间隔限制对象
     */
    fun addIntervalLimit(method: String, intervalLimit: IntervalLimit) {
        if (!limiter.putIfAbsent(method, intervalLimit)) {
            Log.record(TAG, "方法：$method 间隔限制已存在")
            throw IllegalArgumentException("方法：$method 间隔限制已存在")
        }
    }

    /**
     * 更新指定方法的间隔限制。
     *
     * @param method 方法名称
     * @param interval 新的间隔时间（毫秒）
     */
    fun updateIntervalLimit(method: String, interval: Int) {
        updateIntervalLimit(method, DefaultIntervalLimit(interval))
    }

    /**
     * 更新指定方法的间隔限制对象。
     *
     * @param method 方法名称
     * @param intervalLimit 新的自定义间隔限制对象
     */
    fun updateIntervalLimit(method: String, intervalLimit: IntervalLimit) {
        limiter.put(method, intervalLimit)
    }

    /**
     * 进入指定方法的间隔限制，确保调用间隔时间不小于设定值。
     *
     * @param method 方法名称
     */
    fun enterIntervalLimit(method: String) {
        limiter.enter(method)
    }

    /**
     * 清除所有方法的间隔限制。
     */
    fun clearIntervalLimit() {
        limiter.clear()
    }
}
