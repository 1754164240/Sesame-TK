package fansirsqi.xposed.sesame.hook.server.handlers

import fansirsqi.xposed.sesame.hook.internal.AuthCodeHelper
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response

/**
 * OAuth2 授权码处理器
 * 提供获取 OAuth2 授权码的接口
 */
class AuthCodeHandler : HttpHandler {
    
    /**
     * 处理HTTP请求
     * 支持GET请求获取OAuth2授权码
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
     * 获取OAuth2授权码
     * 
     * @param session HTTP会话
     * @return HTTP响应
     */
    private fun handleGetRequest(session: IHTTPSession): Response {
        val appId = session.parms["appId"]
        
        // 参数验证
        if (appId.isNullOrBlank()) {
            return createResponse(
                Response.Status.BAD_REQUEST,
                """{"error":"参数缺失，请提供appId参数"}"""
            )
        }
        
        try {
            // 调用OAuth2授权码获取方法
            val authCode = AuthCodeHelper.getAuthCode(appId)
            
            if (authCode != null) {
                return createResponse(
                    Response.Status.OK,
                    """{"success":true,"authCode":"$authCode"}"""
                )
            } else {
                return createResponse(
                    Response.Status.INTERNAL_ERROR,
                    """{"error":"获取OAuth2授权码失败"}"""
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