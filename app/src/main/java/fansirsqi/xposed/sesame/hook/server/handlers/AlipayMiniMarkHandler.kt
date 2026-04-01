package fansirsqi.xposed.sesame.hook.server.handlers

import fansirsqi.xposed.sesame.hook.internal.AlipayMiniMarkHelper
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response

/**
 * 支付宝小程序标记处理器
 * 提供获取支付宝小程序标记的接口
 */
class AlipayMiniMarkHandler : HttpHandler {
    
    /**
     * 处理HTTP请求
     * 支持GET请求获取支付宝小程序标记
     * 
     * @param session HTTP会话
     * @param body 请求体（未使用）
     * @return HTTP响应
     */
    override fun handle(session: IHTTPSession, body: String?): Response {
        return when (session.method) {
            fi.iki.elonen.NanoHTTPD.Method.GET -> handleGetRequest(session)
            else -> createResponse(Response.Status.METHOD_NOT_ALLOWED, """{"error":"Method not allowed"}""")
        }
    }
    
    /**
     * 处理GET请求
     * 获取支付宝小程序标记
     * 
     * @param session HTTP会话
     * @return HTTP响应
     */
    private fun handleGetRequest(session: IHTTPSession): Response {
        val appid = session.parms["appid"]
        val version = session.parms["version"]
        
        // 参数验证
        if (appid.isNullOrBlank() || version.isNullOrBlank()) {
            return createResponse(
                Response.Status.BAD_REQUEST,
                """{"error":"参数缺失，请提供appid和version参数"}"""
            )
        }
        try {
            // 调用支付宝小程序标记获取方法
            val miniMark = AlipayMiniMarkHelper.getAlipayMiniMark(appid, version)
            return if (miniMark.isNotBlank()) {
                createResponse(
                    Response.Status.OK,
                    """{"success":true,"alipayMiniMark":"$miniMark"}"""
                )
            } else {
                createResponse(
                    Response.Status.INTERNAL_ERROR,
                    """{"error":"获取支付宝小程序标记失败"}"""
                )
            }
        } catch (e: Exception) {
            return createResponse(
                Response.Status.INTERNAL_ERROR,
                """{"error":"服务器内部错误: ${e.message}"}"""
            )
        }
    }
    
    /**
     * 创建JSON格式的HTTP响应
     * 
     * @param status HTTP状态
     * @param jsonBody JSON响应体
     * @return HTTP响应
     */
    private fun createResponse(status: Response.Status, jsonBody: String): Response {
        return fi.iki.elonen.NanoHTTPD.newFixedLengthResponse(
            status,
            "application/json",
            jsonBody
        )
    }
}