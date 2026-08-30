package eu.hxreborn.biometricapplock.util

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log

class MiuiFaceAuthenticator(
    private val onResult: (Boolean) -> Unit,
) : Binder() {
    private var faceService: IBinder? = null
    private var isAuthenticating = false
    private val token = Binder()

    init {
        try {
            val sm = Class.forName("android.os.ServiceManager")
            faceService =
                sm
                    .getMethod(
                        "getService",
                        String::class.java,
                    ).invoke(null, "miui.face.FaceService") as IBinder?
        } catch (e: Exception) {
            Log.e("MiuiFace", "Failed to get FaceService", e)
        }
    }

    override fun getInterfaceDescriptor(): String = "android.hardware.face.IFaceServiceReceiver"

    fun isAvailable(): Boolean = faceService != null

    fun authenticate() {
        val service = faceService ?: return
        isAuthenticating = true
        try {
            // 1. Call preInitAuthen (Transaction 2)
            val preData = Parcel.obtain()
            val preReply = Parcel.obtain()
            preData.writeInterfaceToken("miui.face.FaceService")
            preData.writeStrongBinder(token)
            preData.writeString("eu.hxreborn.biometricapplock")
            preData.writeStrongBinder(this)
            service.transact(2, preData, preReply, 0)
            preReply.readException()
            preData.recycle()
            preReply.recycle()

            // 2. Call authenticate (Transaction 3)
            val authData = Parcel.obtain()
            val authReply = Parcel.obtain()
            authData.writeInterfaceToken("miui.face.FaceService")
            authData.writeStrongBinder(token)
            authData.writeLong(0L) // sessionId
            authData.writeInt(0) // userId
            authData.writeStrongBinder(this) // receiver
            authData.writeInt(0) // flags
            authData.writeString("eu.hxreborn.biometricapplock") // opPackageName
            authData.writeInt(10000) // timeout

            service.transact(3, authData, authReply, 0)
            authReply.readException()
            authData.recycle()
            authReply.recycle()
        } catch (e: Exception) {
            Log.e("MiuiFace", "Failed to authenticate", e)
            isAuthenticating = false
            onResult(false)
        }
    }

    fun cancel() {
        if (!isAuthenticating) return
        val service = faceService ?: return
        try {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            data.writeInterfaceToken("miui.face.FaceService")
            data.writeStrongBinder(token)
            data.writeString("eu.hxreborn.biometricapplock")
            service.transact(9, data, reply, 0)
            reply.readException()
            data.recycle()
            reply.recycle()
        } catch (e: Exception) {
            Log.e("MiuiFace", "Failed to cancel", e)
        }
        isAuthenticating = false
    }

    override fun onTransact(
        code: Int,
        data: Parcel,
        reply: Parcel?,
        flags: Int,
    ): Boolean {
        Log.d("MiuiFace", "onTransact code=$code")
        when (code) {
            3, 203 -> { // TRANSACTION_onAuthenticationSucceeded
                Log.d("MiuiFace", "Face Auth Succeeded!")
                isAuthenticating = false
                onResult(true)
                return true
            }

            5, 6, 204, 205, 206 -> { // TRANSACTION_onAuthenticationFailed or onError
                Log.d("MiuiFace", "Face Auth Failed or Error!")
                isAuthenticating = false
                onResult(false)
                return true
            }
        }
        return super.onTransact(code, data, reply, flags)
    }
}
