package fansirsqi.xposed.sesame.hook.modern

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

object ModernXposedRuntime {
    @Volatile
    private var api: XposedInterface? = null

    val frameworkName: String
        get() = requireApi().frameworkName

    fun initialize(api: XposedInterface) {
        this.api = api
    }

    fun hook(
        executable: Executable,
        before: (HookInvocation) -> Unit = {},
        after: (HookInvocation) -> Unit = {}
    ): XposedInterface.HookHandle = requireApi()
        .hook(executable)
        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
        .intercept { chain ->
            HookInvocation(
                executable = chain.executable,
                thisObject = chain.thisObject,
                initialArgs = chain.args.map { it }.toTypedArray(),
                proceedCall = { args -> chain.proceed(args) }
            ).execute(before, after)
        }

    fun replaceWithConstant(executable: Executable, value: Any?): XposedInterface.HookHandle =
        hook(executable, before = { it.result = value })

    fun deoptimize(executable: Executable): Boolean = requireApi().deoptimize(executable)

    fun log(priority: Int, tag: String, message: String) {
        requireApi().log(priority, tag, message)
    }

    fun log(priority: Int, tag: String, message: String, throwable: Throwable) {
        requireApi().log(priority, tag, message, throwable)
    }

    private fun requireApi(): XposedInterface =
        checkNotNull(api) { "libxposed 运行时尚未初始化" }
}
