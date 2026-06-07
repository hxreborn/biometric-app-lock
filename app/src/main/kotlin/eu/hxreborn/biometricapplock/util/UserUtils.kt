package eu.hxreborn.biometricapplock.util

import android.os.UserHandle

fun getUserId(user: UserHandle): Int =
    runCatching {
        UserHandle::class.java.getMethod("getIdentifier").invoke(user) as Int
    }.getOrDefault(0)

fun getUserHandle(userId: Int): UserHandle =
    runCatching {
        UserHandle::class.java
            .getMethod(
                "of",
                Int::class.javaPrimitiveType,
            ).invoke(null, userId) as UserHandle
    }.getOrElse {
        val constructor = UserHandle::class.java.getConstructor(Int::class.javaPrimitiveType)
        constructor.newInstance(userId)
    }
