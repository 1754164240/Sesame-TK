package fansirsqi.xposed.sesame.hook

object RpcBridgeDispatchPolicy {
    fun shouldBlock(
        offline: Boolean,
        purpose: RpcRequestPurpose?,
        requestGeneration: Long?,
        currentGeneration: Long,
        blockReason: RpcBlockReason
    ): Boolean {
        if (!offline) return false
        return purpose != RpcRequestPurpose.VERIFICATION_PROBE ||
            blockReason != RpcBlockReason.VERIFICATION ||
            requestGeneration == null ||
            requestGeneration != currentGeneration
    }
}
