package eu.hxreborn.biometricapplock.hook

import android.content.Intent
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method

internal var reflection: SystemServerReflection? = null

internal fun ClassLoader.findMethod(
    className: String,
    methodName: String,
    argCount: Int,
): Executable {
    val cls = loadClass(className)
    return cls.declaredMethods.firstOrNull {
        it.name == methodName && it.parameterCount == argCount
    } ?: error("$className.$methodName($argCount args) not found")
}

internal class SystemServerReflection(
    cl: ClassLoader,
) {
    private val activityStartInterceptorClass =
        cl.loadClass("com.android.server.wm.ActivityStartInterceptor")
    private val activityTaskSupervisorClass =
        cl.loadClass("com.android.server.wm.ActivityTaskSupervisor")
    private val activityTaskManagerServiceClass =
        cl.loadClass("com.android.server.wm.ActivityTaskManagerService")

    val intentField: Field = activityStartInterceptorClass.getField("mIntent")
    val resolvedInfoField: Field = activityStartInterceptorClass.getField("mRInfo")
    val activityInfoField: Field = activityStartInterceptorClass.getField("mAInfo")
    val callingPidField: Field = activityStartInterceptorClass.getField("mCallingPid")
    val callingUidField: Field = activityStartInterceptorClass.getField("mCallingUid")
    val realCallingPidField: Field = activityStartInterceptorClass.getField("mRealCallingPid")
    val realCallingUidField: Field = activityStartInterceptorClass.getField("mRealCallingUid")
    val resolvedTypeField: Field = activityStartInterceptorClass.getField("mResolvedType")
    val supervisorField: Field = activityStartInterceptorClass.getField("mSupervisor")
    val userIdField: Field = activityStartInterceptorClass.getField("mUserId")
    val startFlagsField: Field = activityStartInterceptorClass.getField("mStartFlags")

    val resolveIntent: Method =
        activityTaskSupervisorClass.getMethod(
            "resolveIntent",
            Intent::class.java,
            String::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )

    val resolveActivity: Method =
        activityTaskSupervisorClass.getMethod(
            "resolveActivity",
            Intent::class.java,
            cl.loadClass("android.content.pm.ResolveInfo"),
            Int::class.javaPrimitiveType,
            cl.loadClass("android.app.ProfilerInfo"),
        )

    val activityTaskManagerServiceField: Field = activityTaskSupervisorClass.getField("mService")
    val contextField: Field = activityTaskManagerServiceClass.getField("mContext")
    val handlerField: Field =
        activityTaskManagerServiceClass.getDeclaredField("mH").apply { isAccessible = true }

    private val rootWindowContainerField: Field =
        activityTaskManagerServiceClass.getField("mRootWindowContainer")
    private val getTopResumedActivity: Method by lazy {
        rootWindowContainerField.type.getMethod("getTopResumedActivity")
    }
    private val packageNameField: Field by lazy {
        getTopResumedActivity.returnType.getField("packageName")
    }

    fun findTopResumedPackageName(activityTaskManagerService: Any): String? {
        val rootWindowContainer =
            rootWindowContainerField.get(activityTaskManagerService) ?: return null
        val topResumedActivityRecord =
            getTopResumedActivity.invoke(rootWindowContainer) ?: return null
        return packageNameField.get(topResumedActivityRecord) as? String
    }
}
