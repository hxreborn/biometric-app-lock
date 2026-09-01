package eu.hxreborn.biometricapplock.util

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import eu.hxreborn.biometricapplock.BuildConfig

/**
 * Performs Binder IPC to `miui.face.FaceService` to trigger the native face scanner on
 * HyperOS / MIUI devices where `BiometricPrompt` hides face unlock from third-party apps.
 *
 * Callbacks arrive on the Binder thread pool — callers must dispatch back to the UI thread
 * before touching any views.
 */
class MiuiFaceAuthenticator(
    private val onResult: (Boolean) -> Unit,
) : Binder() {
    private var faceService: IBinder? = null

    // Written from the Binder thread pool (onTransact) and read from any thread (cancel/authenticate).
    @Volatile private var isAuthenticating = false

    private val token = Binder()

    init {
        try {
            val sm = Class.forName("android.os.ServiceManager")
            faceService =
                sm
                    .getMethod("getService", String::class.java)
                    .invoke(null, "miui.face.FaceService") as IBinder?
            if (faceService == null) {
                Log.d("MiuiFace", "miui.face.FaceService not present on this device")
            }
        } catch (e: Exception) {
            Log.e("MiuiFace", "Failed to get FaceService", e)
        }
    }

    override fun getInterfaceDescriptor(): String = "android.hardware.face.IFaceServiceReceiver"

    /** True when the face service binder is available. Enrollment is checked separately at the call site. */
    fun isAvailable(): Boolean = faceService != null

    fun authenticate() {
        val service = faceService ?: return
        isAuthenticating = true
        try {
            // Transaction 2 — preInitAuthen: registers our Binder receiver with the face service
            val preData = Parcel.obtain()
            val preReply = Parcel.obtain()
            preData.writeInterfaceToken("miui.face.FaceService")
            preData.writeStrongBinder(token)
            preData.writeString(BuildConfig.APPLICATION_ID)
            preData.writeStrongBinder(this)
            service.transact(2, preData, preReply, 0)
            preReply.readException()
            preData.recycle()
            preReply.recycle()

            // Transaction 3 — authenticate: activates the front-camera face scanner
            val authData = Parcel.obtain()
            val authReply = Parcel.obtain()
            authData.writeInterfaceToken("miui.face.FaceService")
            authData.writeStrongBinder(token)
            authData.writeLong(0L) // sessionId
            authData.writeInt(0) // userId
            authData.writeStrongBinder(this) // receiver (us)
            authData.writeInt(0) // flags
            authData.writeString(BuildConfig.APPLICATION_ID) // opPackageName
            authData.writeInt(10000) // timeout ms
            service.transact(3, authData, authReply, 0)
            authReply.readException()
            authData.recycle()
            authReply.recycle()
        } catch (e: Exception) {
            Log.e("MiuiFace", "Failed to start authentication", e)
            isAuthenticating = false
            onResult(false)
        }
    }

    fun cancel() {
        if (!isAuthenticating) return
        val service = faceService ?: return
        isAuthenticating = false
        try {
            // Transaction 9 — cancelAuthentication
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            data.writeInterfaceToken("miui.face.FaceService")
            data.writeStrongBinder(token)
            data.writeString(BuildConfig.APPLICATION_ID)
            service.transact(9, data, reply, 0)
            reply.readException()
            data.recycle()
            reply.recycle()
        } catch (e: Exception) {
            Log.e("MiuiFace", "Failed to cancel authentication", e)
        }
    }

    override fun onTransact(
        code: Int,
        data: Parcel,
        reply: Parcel?,
        flags: Int,
    ): Boolean {
        Log.d("MiuiFace", "onTransact code=$code")

        // Security: only the system server (UID 1000) should be calling back into this receiver.
        if (Binder.getCallingUid() != 1000) {
            Log.w("MiuiFace", "Rejected onTransact from UID ${Binder.getCallingUid()}")
            return false
        }

        return when (code) {
            // Success — both legacy (3) and modern (203) transaction codes observed on HyperOS
            3, 203 -> {
                Log.d("MiuiFace", "Face authentication succeeded")
                isAuthenticating = false
                onResult(true)
                true
            }

            // Failure / error / lockout — codes 5, 6 observed on older MIUI; 204–206 on HyperOS
            5, 6, 204, 205, 206 -> {
                Log.d("MiuiFace", "Face authentication failed or error (code=$code)")
                isAuthenticating = false
                onResult(false)
                true
            }

            else -> {
                super.onTransact(code, data, reply, flags)
            }
        }
    }
}
