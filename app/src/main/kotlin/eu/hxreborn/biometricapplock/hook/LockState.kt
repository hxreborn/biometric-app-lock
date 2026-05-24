package eu.hxreborn.biometricapplock.hook

import eu.hxreborn.biometricapplock.BiometricAuthActivity
import java.util.concurrent.ConcurrentHashMap

internal data class TaskEntry(
    val packageName: String,
    val topActivityClassName: String,
)

@Volatile
internal var lockedPackages: Set<String> = emptySet()

internal val unlockedPackages: MutableSet<String> = ConcurrentHashMap.newKeySet()

internal val taskCache = ConcurrentHashMap<Int, TaskEntry>()

internal fun relockOtherPackages(keepPackageName: String?) {
    if (unlockedPackages.isEmpty()) return
    if (keepPackageName == BiometricAuthActivity.MODULE_PACKAGE) return
    val keep = keepPackageName?.takeIf { it in unlockedPackages }
    unlockedPackages.clear()
    if (keep != null) unlockedPackages.add(keep)
}
