package fansirsqi.xposed.sesame.task.antSports

import java.lang.reflect.Method
import java.lang.reflect.Modifier

data class ResolvedStepSyncMethods(
    val factory: Method,
    val syncMethod: Method
)

object StepSyncMethodResolver {
    fun resolve(managerClass: Class<*>): ResolvedStepSyncMethods? {
        val methods = managerClass.declaredMethods.filterNot { it.isSynthetic }
        val factories = methods.filter { method ->
            Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.isEmpty() &&
                managerClass.isAssignableFrom(method.returnType)
        }
        val syncMethods = methods.filter { method ->
            !Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.contentEquals(
                    arrayOf(
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                        String::class.java
                    )
                ) &&
                (method.returnType == Boolean::class.javaPrimitiveType ||
                    method.returnType == Boolean::class.javaObjectType)
        }

        if (factories.size != 1 || syncMethods.size != 1) {
            return null
        }

        return ResolvedStepSyncMethods(
            factory = factories.single().also { it.isAccessible = true },
            syncMethod = syncMethods.single().also { it.isAccessible = true }
        )
    }

    fun describeMethods(managerClass: Class<*>): String {
        return managerClass.declaredMethods
            .filterNot { it.isSynthetic }
            .sortedBy { it.name }
            .joinToString(separator = "\n") { method ->
                val staticPrefix = if (Modifier.isStatic(method.modifiers)) "static " else ""
                val parameters = method.parameterTypes.joinToString(",") { it.name }
                "$staticPrefix${method.returnType.name} ${method.name}($parameters)"
            }
    }
}
