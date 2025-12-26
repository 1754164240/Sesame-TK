package fansirsqi.xposed.sesame.task;

import java.util.Arrays;
import java.util.List;

import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.model.Model;

/**
 * TaskRunner适配器类

 * 为Java代码提供更友好的CoroutineTaskRunner调用方式
 */
public class TaskRunnerAdapter {

    private final CoroutineTaskRunner coroutineTaskRunner;

    /**
     * 构造函数 - 使用所有已注册的模型
     */
    public TaskRunnerAdapter() {
        List<Model> modelList = Arrays.asList(Model.getModelArray());
        this.coroutineTaskRunner = new CoroutineTaskRunner(modelList);
    }

    /**
     * 构造函数 - 使用指定的模型列表
     */
    public TaskRunnerAdapter(List<Model> models) {
        this.coroutineTaskRunner = new CoroutineTaskRunner(models);
    }

    /**
     * 执行任务 - 简化版本
     */
    public void run() {
        run(true, ModelTask.TaskExecutionMode.SEQUENTIAL);
    }

    /**
     * 执行任务 - 完整参数版本
     */
    public void run(boolean isFirst, ModelTask.TaskExecutionMode mode) {
        run(isFirst, mode, BaseModel.Companion.getTaskExecutionRounds().getValue());
    }

    /**
     * 执行任务 - 包含轮数参数（主方法）
     */
    public void run(boolean isFirst, ModelTask.TaskExecutionMode mode, int rounds) {
        coroutineTaskRunner.run(isFirst, rounds);
    }

    /**
     * 停止任务执行器
     */
    public void stop() {
        coroutineTaskRunner.stop();
    }

    /**
     * 静态方法：快速执行所有任务
     */
    public static void runAllTasks() {
        runAllTasks(ModelTask.TaskExecutionMode.SEQUENTIAL);
    }

    /**
     * 静态方法：使用指定模式执行所有任务
     */
    public static void runAllTasks(ModelTask.TaskExecutionMode mode) {
        new TaskRunnerAdapter().run(true, mode);
    }
}