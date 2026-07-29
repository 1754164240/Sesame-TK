package fansirsqi.xposed.sesame.hook.rpc.bridge;
import fansirsqi.xposed.sesame.entity.RpcEntity;
import fansirsqi.xposed.sesame.util.RandomUtil;

public interface RpcBridge {
    RpcVersion getVersion();
    void load() throws Exception;
    void unload();

    String requestString(RpcEntity rpcEntity, int tryCount, int retryInterval);
    RpcEntity requestObject(RpcEntity rpcEntity, int tryCount, int retryInterval);

    default String requestString(RpcEntity rpcEntity) {
        return requestString(rpcEntity, 3, -1);
    }
    /**
     * 发送RPC请求并获取响应字符串（使用默认重试参数）
     *
     * @param method RPC方法名
     * @param data 请求数据
     * @return 响应字符串，如果请求失败则返回null
     */
    default String requestString(String method, String data) {
        return requestString(method, data, 3, 1500);
    }

    /**
     * 发送带关联数据的RPC请求并获取响应字符串（使用默认重试参数）
     *
     * @param method RPC方法名
     * @param data 请求数据
     * @param relation 关联数据
     * @return 响应字符串，如果请求失败则返回null
     */
    default String requestString(String method, String data, String relation) {
        return requestString(method, data, relation, 3, 1500);
    }
    default String requestString(String method, String data, String appName, String methodName, String facadeName) {
        return requestString(
                new RpcEntity(method, data, null, appName, methodName, facadeName),
                3,
                -1
        );
    }
    default String requestString(String method, String data, int tryCount, int retryInterval) {
        return requestString(new RpcEntity(method, data), tryCount, retryInterval);
    }
    default String requestString(String method, String data, String relation, int tryCount, int retryInterval) {
        return requestString(new RpcEntity(method, data, relation), tryCount, retryInterval);
    }


    default RpcEntity requestObject(String method, String data, String relation) {
        return requestObject(method, data, relation, 3, -1);
    }
    default RpcEntity requestObject(String method, String data, int tryCount, int retryInterval) {
        return requestObject(new RpcEntity(method, data), tryCount, retryInterval);
    }
    default RpcEntity requestObject(String method, String data, String relation, int tryCount, int retryInterval) {
        return requestObject(new RpcEntity(method, data, relation), tryCount, retryInterval);
    }
}
