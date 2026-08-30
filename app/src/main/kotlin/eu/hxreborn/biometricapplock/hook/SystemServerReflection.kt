package eu.hxreborn.biometricapplock.hook

import android.content.ComponentName
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

// these framework members are package-private in AOSP so getField and getMethod miss them
internal fun Class<*>.requiredField(name: String): Field =
    getDeclaredField(name).apply { isAccessible = true }

internal fun Class<*>.requiredMethod(
    name: String,
    vararg params: Class<*>?,
): Method = getDeclaredMethod(name, *params).apply { isAccessible = true }

internal fun Class<*>.optionalField(vararg names: String): Field? {
    for (name in names) {
        runCatching { getDeclaredField(name).apply { isAccessible = true } }
            .getOrNull()
            ?.let { return it }
    }
    Logger.warn("optional field absent $simpleName.${names.joinToString("/")}")
    return null
}

internal fun Class<*>.optionalMethod(
    name: String,
    argCount: Int? = null,
): Method? {
    declaredMethods
        .filter { it.name == name && (argCount == null || it.parameterCount == argCount) }
        .minByOrNull { it.parameterCount }
        ?.let { return it.apply { isAccessible = true } }
    Logger.warn("optional method absent $simpleName.$name")
    return null
}

internal fun Class<*>.optionalIntConst(name: String): Int? {
    runCatching { getDeclaredField(name).apply { isAccessible = true }.getInt(null) }
        .getOrNull()
        ?.let { return it }
    Logger.warn("optional const absent $simpleName.$name")
    return null
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
    private val activityRecordClass = cl.loadClass("com.android.server.wm.ActivityRecord")

    val activityRecordPackageNameField: Field = activityRecordClass.requiredField("packageName")
    val activityRecordUserIdField: Field = activityRecordClass.requiredField("mUserId")

    private val taskInfoClass = cl.loadClass("android.app.TaskInfo")
    val taskInfoUserIdField: Field = taskInfoClass.requiredField("userId")

    val taskLookup: TaskLookup? =
        runCatching { TaskLookup(cl) }
            .onFailure { Logger.warn("task lookup init failed: ${it.message}") }
            .getOrNull()

    val intentField: Field = activityStartInterceptorClass.requiredField("mIntent")
    val resolvedInfoField: Field = activityStartInterceptorClass.requiredField("mRInfo")
    val activityInfoField: Field = activityStartInterceptorClass.requiredField("mAInfo")
    val callingPidField: Field = activityStartInterceptorClass.requiredField("mCallingPid")
    val callingUidField: Field = activityStartInterceptorClass.requiredField("mCallingUid")
    val realCallingPidField: Field = activityStartInterceptorClass.requiredField("mRealCallingPid")
    val realCallingUidField: Field = activityStartInterceptorClass.requiredField("mRealCallingUid")
    val resolvedTypeField: Field = activityStartInterceptorClass.requiredField("mResolvedType")
    val supervisorField: Field = activityStartInterceptorClass.requiredField("mSupervisor")
    val userIdField: Field = activityStartInterceptorClass.requiredField("mUserId")
    val startFlagsField: Field = activityStartInterceptorClass.requiredField("mStartFlags")

    // resolveIntent is 5 args on A13 and 6 on A14+ (added callingPid), match by name
    val resolveIntent: Method =
        activityTaskSupervisorClass.declaredMethods
            .filter { it.name == "resolveIntent" }
            .maxByOrNull { it.parameterCount }
            ?.apply { isAccessible = true }
            ?: error("ActivityTaskSupervisor.resolveIntent not found")

    val resolveActivity: Method =
        activityTaskSupervisorClass.requiredMethod(
            "resolveActivity",
            Intent::class.java,
            cl.loadClass("android.content.pm.ResolveInfo"),
            Int::class.javaPrimitiveType,
            cl.loadClass("android.app.ProfilerInfo"),
        )

    val activityTaskManagerServiceField: Field =
        activityTaskSupervisorClass.requiredField("mService")
    val contextField: Field = activityTaskManagerServiceClass.requiredField("mContext")
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

    private val uriGrantsManagerInternalClass =
        cl.loadClass("com.android.server.uri.UriGrantsManagerInternal")

    // system_server-internal singleton registry, the only path to UriGrantsManagerInternal
    val uriGrantsInternal: Any? by lazy {
        runCatching {
            cl
                .loadClass("com.android.server.LocalServices")
                .getMethod("getService", Class::class.java)
                .invoke(null, uriGrantsManagerInternalClass)
        }.getOrNull()
    }

    // skips uri regrant on ROMs that removed these methods
    val checkGrantUriPermissionFromIntent: Method? =
        uriGrantsManagerInternalClass.declaredMethods
            .filter { it.name == "checkGrantUriPermissionFromIntent" }
            .minByOrNull { it.parameterCount }
            ?.apply { isAccessible = true }

    val grantUriPermissionUncheckedFromIntent: Method? =
        uriGrantsManagerInternalClass.declaredMethods
            .filter { it.name == "grantUriPermissionUncheckedFromIntent" }
            .minByOrNull { it.parameterCount }
            ?.apply { isAccessible = true }
    val handlerField: Field =
        activityTaskManagerServiceClass.getDeclaredField("mH").apply { isAccessible = true }

    val startSystemLockTaskMode: Method? =
        activityTaskManagerServiceClass.optionalMethod("startSystemLockTaskMode", argCount = 1)

    val rootWindowContainerField: Field =
        activityTaskManagerServiceClass.requiredField("mRootWindowContainer")
    private val getTopResumedActivity: Method by lazy {
        rootWindowContainerField.type.requiredMethod("getTopResumedActivity")
    }
    private val packageNameField: Field by lazy {
        getTopResumedActivity.returnType.requiredField("packageName")
    }
    private val userIdFieldTop: Field by lazy {
        getTopResumedActivity.returnType.requiredField("mUserId")
    }
    val refreshSecureSurfaceState: Method by lazy {
        rootWindowContainerField.type.requiredMethod("refreshSecureSurfaceState")
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

// Recents/task reflection, isolated from SystemServerReflection so an OEM-stripped symbol never
// fails the whole init. Every member is optional and self-reports when absent, the hooks degrade
// per capability: no package reader means cache-only entries, no anyTaskForId means no live
// taskId lookup. A new OEM group should follow this shape, optional members plus a capability().
internal class TaskLookup(
    cl: ClassLoader,
) {
    private val taskClass = cl.loadClass("com.android.server.wm.Task")
    private val rootWindowContainerClass: Class<*>? =
        runCatching { cl.loadClass("com.android.server.wm.RootWindowContainer") }.getOrNull()

    val userIdField: Field? = taskClass.optionalField("mUserId")

    // three independent ways to read the package off a Task, any one surviving is enough
    private val realActivityField: Field? = taskClass.optionalField("realActivity")
    private val intentField: Field? = taskClass.optionalField("intent")
    private val getBaseIntent: Method? = taskClass.optionalMethod("getBaseIntent")

    // resolves a taskId to its live Task, absent on OneUI so the recents resolver stays cache-only
    val anyTaskForId: Method? =
        rootWindowContainerClass?.optionalMethod(
            "anyTaskForId",
            argCount = 2,
        )
    val matchAttachedOrRecents: Int? =
        rootWindowContainerClass?.optionalIntConst("MATCH_ATTACHED_TASK_OR_RECENT_TASKS")

    fun packageOf(task: Any): String? {
        (realActivityField?.get(task) as? ComponentName)?.packageName?.let { return it }
        (intentField?.get(task) as? Intent)?.component?.packageName?.let { return it }
        (getBaseIntent?.invoke(task) as? Intent)?.component?.packageName?.let { return it }
        return null
    }

    // one line naming what this ROM can and can't do, read once at boot
    fun capability(): String {
        val canReadPkg = realActivityField != null || intentField != null || getBaseIntent != null
        val canLiveLookup = anyTaskForId != null && matchAttachedOrRecents != null
        return "taskLookup pkg=$canReadPkg user=${userIdField != null} liveLookup=$canLiveLookup"
    }
}
