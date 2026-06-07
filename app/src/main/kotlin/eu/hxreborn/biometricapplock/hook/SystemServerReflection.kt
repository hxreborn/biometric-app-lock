package eu.hxreborn.biometricapplock.hook

import android.content.Intent
import android.os.Build
import eu.hxreborn.biometricapplock.util.Logger
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method

@Volatile
internal var reflection: SystemServerReflection? = null

// intercept arg count drifts by API: 8 on A13, 9 on A14, 10 on A15, 11 on A16
internal fun ClassLoader.findMethod(
    className: String,
    methodName: String,
    argCount: Int,
): Executable {
    val cls = loadClass(className)
    val named = cls.declaredMethods.filter { it.name == methodName }
    named.firstOrNull { it.parameterCount == argCount }?.let { return it }
    // take the widest overload when nothing matches the exact arg count
    named.maxByOrNull { it.parameterCount }?.let {
        Logger.warn(
            "$className.$methodName arg count drift expected=$argCount " +
                "actual=${it.parameterCount} sdk=${Build.VERSION.SDK_INT}",
        )
        return it
    }
    error(
        "$className.$methodName($argCount args) not found sdk=${Build.VERSION.SDK_INT} " +
            "candidates=${named.map { it.parameterCount }}",
    )
}

// match an arg by type so a position shift across API levels does not break the hook
internal fun Executable.firstArgIndexOfType(typeName: String): Int =
    parameterTypes.indexOfFirst { it.simpleName == typeName || it.name == typeName }

// try framework class names in order so a package move or rename still resolves
internal fun ClassLoader.anyClassFromNames(vararg names: String): Class<*> {
    for (name in names) {
        val cls = runCatching { loadClass(name) }.getOrNull()
        if (cls != null) return cls
    }
    error("no class from ${names.toList()} sdk=${Build.VERSION.SDK_INT}")
}

internal class SystemServerReflection(
    cl: ClassLoader,
) {
    private val activityStartInterceptorClass =
        cl.loadClass("com.android.server.wm.ActivityStartInterceptor")
    private val activityTaskSupervisorClass =
        cl.anyClassFromNames(
            "com.android.server.wm.ActivityTaskSupervisor",
            "com.android.server.wm.ActivityStackSupervisor",
        )
    private val activityTaskManagerServiceClass =
        cl.loadClass("com.android.server.wm.ActivityTaskManagerService")
    private val activityRecordClass =
        cl.loadClass("com.android.server.wm.ActivityRecord")

    val activityRecordPackageNameField: Field = activityRecordClass.getField("packageName")
    val activityRecordUserIdField: Field = activityRecordClass.getField("mUserId")

    private val taskInfoClass = cl.loadClass("android.app.TaskInfo")
    val taskInfoUserIdField: Field = taskInfoClass.getField("userId")

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

    // resolveIntent is 5 args on A13 and 6 on A14+ (added callingPid), match by name
    val resolveIntent: Method =
        activityTaskSupervisorClass.declaredMethods
            .filter { it.name == "resolveIntent" }
            .maxByOrNull { it.parameterCount }
            ?: error("ActivityTaskSupervisor.resolveIntent not found")

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
    val startActivityAsUser: Method by lazy {
        contextField.type.getMethod(
            "startActivityAsUser",
            Intent::class.java,
            android.os.UserHandle::class.java,
        )
    }
    val userHandleOf: Method by lazy {
        android.os.UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType)
    }
    val handlerField: Field =
        activityTaskManagerServiceClass.getDeclaredField("mH").apply { isAccessible = true }

    val rootWindowContainerField: Field =
        activityTaskManagerServiceClass.getField("mRootWindowContainer")
    private val getTopResumedActivity: Method by lazy {
        rootWindowContainerField.type.getMethod("getTopResumedActivity")
    }
    private val packageNameField: Field by lazy {
        getTopResumedActivity.returnType.getField("packageName")
    }
    private val userIdFieldTop: Field by lazy {
        getTopResumedActivity.returnType.getField("mUserId")
    }
    val refreshSecureSurfaceState: Method by lazy {
        rootWindowContainerField.type.getMethod("refreshSecureSurfaceState")
    }

    fun findTopResumedPackageKey(activityTaskManagerService: Any): String? {
        val rootWindowContainer =
            rootWindowContainerField.get(activityTaskManagerService) ?: return null
        val topResumedActivityRecord =
            getTopResumedActivity.invoke(rootWindowContainer) ?: return null
        val pkg = packageNameField.get(topResumedActivityRecord) as? String ?: return null
        val userId = userIdFieldTop.get(topResumedActivityRecord) as? Int ?: 0
        return "$pkg:$userId"
    }
}
