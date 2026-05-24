package eu.hxreborn.biometricapplock.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Process

class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        Process.killProcess(Process.myPid())
    }
}
