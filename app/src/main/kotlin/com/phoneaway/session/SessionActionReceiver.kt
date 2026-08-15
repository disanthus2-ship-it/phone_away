package com.phoneaway.session

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Backs the "End session" action on the ongoing notification. */
class SessionActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return
        context.startService(IdleSessionService.stopIntent(context))
    }

    companion object {
        const val ACTION_STOP = "com.phoneaway.action.NOTIFICATION_STOP"
    }
}
