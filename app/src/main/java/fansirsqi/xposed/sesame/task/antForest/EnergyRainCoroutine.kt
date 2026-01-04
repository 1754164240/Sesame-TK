package fansirsqi.xposed.sesame.task.antForest

import fansirsqi.xposed.sesame.hook.Toast
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import fansirsqi.xposed.sesame.util.maps.UserMap
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.random.Random

/**
 * 能量雨功能 - Kotlin协程版本
 *
 * 这是EnergyRain.java的协程版本重构，提供更好的性能和可维护性
 */
object EnergyRainCoroutine {
    private const val TAG = "EnergyRain"

    /**
     * 上次执行能量雨的时间戳
     */
    @Volatile
    private var lastExecuteTime: Long = 0

    /**
     * 随机延迟，增加随机性避免风控检测
     * @param min 最小延迟（毫秒）
     * @param max 最大延迟（毫秒）
     */
    private suspend fun randomDelay(min: Int, max: Int) {
        val delayTime = Random.nextInt(min, max + 1).toLong()
        delay(delayTime)
    }

    /**
     * 执行能量雨功能
     */
    suspend fun execEnergyRain() {
        try {
            // 执行频率检查：防止短时间内重复执行
            val currentTime = System.currentTimeMillis()
            val timeSinceLastExec = currentTime - lastExecuteTime
            val cooldownSeconds = 3 // 冷却时间：3秒

            if (timeSinceLastExec < cooldownSeconds * 1000) {
                val remainingSeconds = (cooldownSeconds * 1000 - timeSinceLastExec) / 1000
                Log.record(TAG, "⏱️ 能量雨冷却中，还需等待 $remainingSeconds 秒")
                return
            }

            energyRain()

            // 更新最后执行时间
            lastExecuteTime = System.currentTimeMillis()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消是正常现象，不记录为错误
            Log.debug(TAG, "execEnergyRain 协程被取消")
            throw e  // 必须重新抛出以保证取消机制正常工作
        } catch (th: Throwable) {
            Log.record(TAG, "执行能量雨出错:")
            Log.printStackTrace(TAG, th)
        }
    }

    /**
     * 能量雨主逻辑（协程版本）
     */
    private suspend fun energyRain() {
        try {
            var joEnergyRainHome = JSONObject(AntForestRpcCall.queryEnergyRainHome())
            randomDelay(250, 400) // 随机延迟 300-400ms
            if (!ResChecker.checkRes(TAG, joEnergyRainHome)) {
                Log.record(TAG, "查询能量雨状态失败")
                return
            }
            var hasExecuted = false // 标记是否已执行过能量雨
            // 1️⃣ 优先执行自己的能量雨
            if (joEnergyRainHome.getBoolean("canPlayToday")) {
                startEnergyRain()
                hasExecuted = true
                randomDelay(1000, 1200) // 随机延迟 1-1.2秒
            }

            // 2️⃣ 检查是否可以赠送能量雨
            if (joEnergyRainHome.getBoolean("canGrantStatus")) {
                Log.record(TAG, "有送能量雨的机会")
                val joEnergyRainCanGrantList = JSONObject(AntForestRpcCall.queryEnergyRainCanGrantList())
                val grantInfos = joEnergyRainCanGrantList.getJSONArray("grantInfos")
                val giveEnergyRainSet = AntForest.giveEnergyRainList!!.value
                var granted = false
                for (j in 0 until grantInfos.length()) {
                    val grantInfo = grantInfos.getJSONObject(j)
                    if (grantInfo.getBoolean("canGrantedStatus")) {
                        val uid = grantInfo.getString("userId")
                        if (giveEnergyRainSet.contains(uid)) {
                            val rainJsonObj = JSONObject(AntForestRpcCall.grantEnergyRainChance(uid))
                            Log.record(TAG, "尝试送能量雨给【${UserMap.getMaskName(uid)}】")
                            if (ResChecker.checkRes(TAG, rainJsonObj)) {
                                Log.forest("赠送能量雨机会给🌧️[${UserMap.getMaskName(uid)}]#${UserMap.getMaskName(UserMap.currentUid)}")
                                randomDelay(300, 400) // 随机延迟 300-400ms
                                // 赠送成功后，检查是否还能再玩一次
                                if (!hasExecuted) {
                                    val recheckHome = JSONObject(AntForestRpcCall.queryEnergyRainHome())
                                    if (ResChecker.checkRes(TAG, recheckHome) && recheckHome.getBoolean("canPlayToday")) {
                                        startEnergyRain()
                                        hasExecuted = true
                                    }
                                }
                            } else {
                                Log.record(TAG, "送能量雨失败")
                                Log.record(TAG, rainJsonObj.toString())
                            }
                            granted = true
                            break
                        }
                    }
                }

                if (!granted) {
                    Log.record(TAG, "今日已无可送能量雨好友")
                }
            }

            // 3️⃣ 最后检查：如果前面都没执行过，再次尝试
            if (!hasExecuted) {
                joEnergyRainHome = JSONObject(AntForestRpcCall.queryEnergyRainHome())
                if (ResChecker.checkRes(TAG, joEnergyRainHome) && joEnergyRainHome.getBoolean("canPlayToday")) {
                    startEnergyRain()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消是正常现象，不记录为错误
            Log.debug(TAG, "energyRain 协程被取消")
            throw e  // 必须重新抛出以保证取消机制正常工作
        } catch (th: Throwable) {
            Log.record(TAG, "energyRain err:")
            Log.printStackTrace(TAG, th)
        }
    }

    /**
     * 开始能量雨（协程版本）
     */
    private suspend fun startEnergyRain() {
        try {
            Log.forest("开始执行能量雨🌧️")
            val joStart = JSONObject(AntForestRpcCall.startEnergyRain())

            if (ResChecker.checkRes(TAG, joStart)) {
                val token = joStart.getString("token")
                val bubbleEnergyList = joStart.getJSONObject("difficultyInfo").getJSONArray("bubbleEnergyList")
                var sum = 0

                for (i in 0 until bubbleEnergyList.length()) {
                    sum += bubbleEnergyList.getInt(i)
                }

                randomDelay(5000, 5200) // 随机延迟 5-5.2秒，模拟真人玩游戏
                val resultJson = JSONObject(AntForestRpcCall.energyRainSettlement(sum, token))

                if (ResChecker.checkRes(TAG, resultJson)) {
                    val s = "收获能量雨🌧️[${sum}g]"
                    Toast.show(s)
                    Log.forest(s)
                }
                randomDelay(300, 400) // 随机延迟 300-400ms
            } else {
                Log.record(TAG, "startEnergyRain: $joStart")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程取消是正常现象，不记录为错误
            Log.debug(TAG, "startEnergyRain 协程被取消")
            throw e  // 必须重新抛出以保证取消机制正常工作
        } catch (th: Throwable) {
            Log.record(TAG, "startEnergyRain err:")
            Log.printStackTrace(TAG, th)
        }
    }

    /**
     * 兼容Java调用的包装方法
     */
    @JvmStatic
    fun execEnergyRainCompat() {
        kotlinx.coroutines.runBlocking {
            execEnergyRain()
        }
    }
}