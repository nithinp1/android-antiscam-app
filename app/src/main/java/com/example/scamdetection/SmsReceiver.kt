package com.example.scamdetection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Routes real SMS broadcasts through the same handler as the debug simulator. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.originatingAddress ?: "unknown"
        val body = messages.joinToString("") { it.messageBody ?: "" }
        ScamDetectionService.handleIncomingSms(context.applicationContext, sender, body)
    }
}
