package com.example.scamdetection

import android.content.Context
import android.content.Intent
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager

/**
 * Auto-triggers monitoring on call-state changes: OFFHOOK starts the service,
 * IDLE stops it. No call-type or contact differentiation. The manual Start/Stop
 * buttons stay independent: [noteManual] clears the call-started flag so a manual
 * session is neither duplicated nor killed by a subsequent IDLE.
 */
object CallStateManager {

    private var context: Context? = null
    private var telephony: TelephonyManager? = null
    private var startedByCall = false
    /** Last number seen in OFFHOOK; used by EvidenceRecorder for filenames. */
    @Volatile var lastCallNumber: String? = null

    /** True while a phone call is active (OFFHOOK / RINGING). Checks both auto-start flag and actual call state. */
    val isCallActive: Boolean
        get() = checkCallActive(null)

    fun checkCallActive(ctx: Context? = null): Boolean {
        if (startedByCall) return true
        val tm = telephony ?: run {
            val c = ctx ?: context ?: return false
            (c.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.also {
                telephony = it
                android.util.Log.d("CallStateManager", "Initialized TelephonyManager fallback")
            }
        } ?: return false

        val state = try {
            tm.callState
        } catch (e: Exception) {
            android.util.Log.e("CallStateManager", "Error checking callState", e)
            TelephonyManager.CALL_STATE_IDLE
        }
        return state != TelephonyManager.CALL_STATE_IDLE
    }

    private val listener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            val ctx = context ?: return
            android.util.Log.d("CallStateManager", "onCallStateChanged: state=$state")
            when (state) {
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    if (ScamDetectionService.active || startedByCall) return  // double-start guard
                    lastCallNumber = phoneNumber ?: "unknown"
                    val url = ScamDetectionService.lastServerUrl
                    if (url.isNullOrEmpty()) {
                        ScamDetectionService.notifyLog(
                            LogEntry("Call active — no server URL set; enter it above")
                        )
                        return
                    }
                    startedByCall = true
                    ctx.startForegroundService(
                        Intent(ctx, ScamDetectionService::class.java)
                            .putExtra(ScamDetectionService.EXTRA_URL, url)
                    )
                    ScamDetectionService.notifyLog(LogEntry("Call active — monitoring started"))
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (startedByCall) {
                        startedByCall = false
                        lastCallNumber = null
                        ctx.stopService(Intent(ctx, ScamDetectionService::class.java))
                        ScamDetectionService.notifyLog(LogEntry("Call ended — monitoring stopped"))
                    }
                }
            }
        }
    }

    /** Requires READ_PHONE_STATE already granted; no-op if already registered. */
    fun register(ctx: Context) {
        context = ctx.applicationContext
        if (telephony == null) {
            telephony = context?.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        }
        try {
            telephony?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            android.util.Log.d("CallStateManager", "Registered PhoneStateListener successfully")
        } catch (e: Exception) {
            android.util.Log.e("CallStateManager", "Failed to register PhoneStateListener", e)
        }
    }

    fun unregister() {
        try {
            telephony?.listen(listener, PhoneStateListener.LISTEN_NONE)
        } catch (_: Exception) {}
        telephony = null
        context = null
    }

    /** Mark the current session as manual so a later IDLE doesn't stop it. */
    fun noteManual() {
        startedByCall = false
    }
}
