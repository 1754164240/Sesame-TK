package fansirsqi.xposed.sesame.task.antForest

import android.annotation.SuppressLint
import fansirsqi.xposed.sesame.data.Status
import fansirsqi.xposed.sesame.hook.Toast
import fansirsqi.xposed.sesame.util.Log
import fansirsqi.xposed.sesame.util.ResChecker
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * 6秒拼手速打地鼠
 * @author Ghostxx
 */
object WhackMole {
    private const val TAG = "WhackMole"
    // ========== 核心配置 ==========
    /** 一次性启动的游戏局数：可配置 */
    @Volatile
    private var totalGames = 5
    /** 游戏总时长（毫秒）：严格等待12秒，让所有局完成 */
    private const val GAME_DURATION_MS = 12000L
    /** 全局协程作用域：用于管理所有协程，SupervisorJob确保子协程失败不影响其他 */
    private val globalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 记录启动开始时间：用于精确计算已用时长，凑满6秒 */
    private val startTime = AtomicLong(0)

    /** 运行状态标志：防止重复启动游戏 */
    @Volatile
    private var isRunning = false

    // ========== 数据类 ==========
    /**
     * 游戏会话：存储单局游戏的关键信息（新规则：只存储token）
     * @param token 本局游戏的唯一凭证
     * @param roundNumber 局号（用于日志显示）
     */
    data class GameSession(
        val token: String,
        val roundNumber: Int
    )

    // ========== 配置方法 ==========
    /**
     * 设置游戏局数
     * @param games 游戏局数
     */
    fun setTotalGames(games: Int) {
        totalGames = games
    }

    // ========== 间隔计算器引用 ==========
    /** 引用外部间隔计算器 */
    private val intervalCalculator = GameIntervalCalculator

    // ========== 自动入口 ==========
    /**
     * 启动打地鼠游戏的主入口
     * 1. 检查运行状态，防止重复启动
     * 2. 从配置读取游戏局数
     * 3. 串行启动所有局（带随机间隔）
     * 4. 等待凑满10秒
     * 5. 串行结算所有游戏局
     */
    @SuppressLint("DefaultLocale")
    fun startWhackMole() {
        if (isRunning) {
            Log.record(TAG, "⏭️ 打地鼠游戏正在运行中，跳过重复启动")
            return
        }
        isRunning = true
        
        // 在IO协程中执行，避免阻塞主线程
        globalScope.launch {
            try {
                // 记录启动时间戳
                startTime.set(System.currentTimeMillis())
                
                // 计算动态间隔参数
                val dynamicInterval = intervalCalculator.calculateDynamicInterval(GAME_DURATION_MS, totalGames)
                Log.other( "🎮 动态间隔计算完成 - 基础间隔: ${dynamicInterval.baseInterval}ms, 随机范围: ±${dynamicInterval.randomRange}ms")
                
                // 串行启动每局游戏（避免并发限流）
                val sessions = mutableListOf<GameSession>()
                try {
                    for (roundNum in 1..totalGames) {
                        val session = startSingleRound(roundNum)
                        if (session != null) {
                            sessions.add(session)
                        }
                        // 使用动态计算的间隔，避免并发请求触发服务器限流
                        if (roundNum < totalGames) {
                            val elapsedTime = System.currentTimeMillis() - startTime.get()
                            val remainingTime = GAME_DURATION_MS - elapsedTime
                            val delayMs = intervalCalculator.calculateNextDelay(dynamicInterval, roundNum, totalGames, remainingTime)
                            Log.other( "🎮 第${roundNum}局后间隔: ${delayMs}ms (剩余时间: ${remainingTime}ms)")
                            delay(delayMs)
                        }
                    }
                } catch (e: CancellationException) {
                    Log.debug(TAG, "游戏启动被打断: ${e.message}")
                    return@launch
                }

                // 计算剩余等待时间，凑满10秒
                val elapsedTime = System.currentTimeMillis() - startTime.get()
                val remainingTime = GAME_DURATION_MS - elapsedTime
                if (remainingTime > 0) {
                    Log.other( "已启动${sessions.size}局，等待${remainingTime}ms凑满10秒...")
                    delay(remainingTime)
                } else {
                    Log.other( "已启动${sessions.size}局，已超过10秒，立即结算")
                }

                // 串行结算所有游戏局
                var totalEnergy = 0
                sessions.forEach { session ->
                    totalEnergy += settleBestRound(session)
                }
                // 最终日志：显示成功局数和总能量
                Log.other("森林能量⚡️[打地鼠${sessions.size}局结算 总计${totalEnergy}g]")
                Log.forest("森林能量⚡️[打地鼠${sessions.size}局结算 总计${totalEnergy}g]")
            } catch (_: CancellationException) {
                Log.other( "打地鼠协程被取消")
            } catch (e: Exception) {
                Log.other( "打地鼠异常: ${e.message}")
            } finally {
                isRunning = false
                Log.record(TAG, "🎮 打地鼠游戏运行状态已重置")
            }
        }
    }

    // ========== 单局游戏 ==========
    /**
     * 启动单局游戏（新规则：只启动不击打）
     * 1. 调用startWhackMole获取token
     * 2. 检测userBaseInfo判断服务器是否限流
     * 3. 直接返回token，不进行击打
     */
    private suspend fun startSingleRound(round: Int): GameSession? = withContext(Dispatchers.IO) {
        try {
            // 调用无参的startWhackMole()（source已内联）
            val startResp = JSONObject(AntForestRpcCall.startWhackMole())
            if (!ResChecker.checkRes("$TAG 启动失败:", startResp)) {
                return@withContext null
            }
            // 检查今日是否还能玩游戏
            val canPlayToday = startResp.optBoolean("canPlayToday", true)
            if (!canPlayToday) {
                Log.other( "今日打地鼠次数已用完，canPlayToday=false")
                // 设置今日已执行标志，避免重复尝试
                Status.setFlagToday("forest::whackMole::executed")
                throw CancellationException("今日打地鼠次数已用完")
            }

            val token = startResp.optString("token")
            Log.other( "第${round}局启动成功，token=$token")
            Toast.show("打地鼠 第${round}局启动成功:"+"token=$token"+"\n请速回蚂蚁森林滑块验证，10秒后结算")
            GameSession(token, round)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.other( "第${round}局异常: ${e.message}")
            return@withContext null
        }
    }



    // ========== 结算 ==========
    /**
     * 结算单局游戏
     * 调用单参的settlementWhackMole()，获取最终能量
     * @return 获得的能量数
     */
    private suspend fun settleBestRound(session: GameSession): Int = withContext(Dispatchers.IO) {
        try {
            // 调用单参的settlementWhackMole()（其他参数已内联）
            delay(max(50L, 200L)) // 短暂延时，避免请求过快
            val resp = JSONObject(AntForestRpcCall.settlementWhackMole(session.token))

            if (ResChecker.checkRes(TAG, resp)) {
                val total = resp.optInt("totalEnergy", 0)
                val provide = resp.optInt("provideDefaultEnergy", 0)
                Log.forest(
                    "森林能量⚡️[第${session.roundNumber}局结算 " +
                            "默认${provide}g 总计${total}g]"
                )
                Log.other(
                    "森林能量⚡️[第${session.roundNumber}局结算 " +
                            "默认${provide}g 总计${total}g]"
                )
                return@withContext total
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.other( "结算异常: ${e.message}")
        }
        return@withContext 0
    }
}
