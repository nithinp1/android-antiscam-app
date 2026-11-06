package com.echoguard.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** One line of the in-app log. risk > 70 makes the UI render it red. */
data class LogEntry(val text: String, val risk: Int = -1)

private const val TAG = "ScamWS"

private enum class WsState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

/**
 * Records the mic, chunks it into ~6s 16kHz-mono-PCM WAV blobs, and streams each
 * to the server's /audio-stream WebSocket. Server score replies are routed to
 * [onLog], which MainActivity installs while it is visible.
 */
class ScamDetectionService : Service() {

    companion object {
        @Volatile var instance: ScamDetectionService? = null

        const val EXTRA_URL = "server_url"
        private const val CHANNEL_ID = "scam_detection"
        private const val NOTIF_ID = 1
        private const val SUSPICIOUS_RISK_THRESHOLD = 40
        private const val SCAM_RISK_THRESHOLD = 70
        private const val SUSPICIOUS_ALERT_INTERVAL_MS = 10_000L
        private const val SCAM_ALERT_INTERVAL_MS = 8_000L
        private const val SCAM_TONE_DURATION_MS = 500

        /** Multi-consumer log stream — MainActivity, TechnicalLogActivity both subscribe. */
        private val logListeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()
        private val logBuffer = ArrayList<LogEntry>() // recent history for late subscribers
        private const val LOG_BUFFER_MAX = 200
        @Volatile private var lastLoggedText: String = ""

        fun addLogListener(l: (LogEntry) -> Unit) = logListeners.add(l).also { replayBuffer(l) }
        fun removeLogListener(l: (LogEntry) -> Unit) = logListeners.remove(l)
        fun notifyLog(e: LogEntry) {
            // Deduplicate consecutive identical messages to eliminate repeated clutter
            if (e.text.isNotEmpty() && e.text == lastLoggedText) return
            lastLoggedText = e.text

            synchronized(logBuffer) {
                logBuffer.add(e)
                if (logBuffer.size > LOG_BUFFER_MAX) logBuffer.removeAt(0)
            }
            logListeners.forEach { it(e) }
        }
        private fun replayBuffer(l: (LogEntry) -> Unit) {
            synchronized(logBuffer) { logBuffer.forEach(l) }
        }

        /** Multi-consumer SMS stream. */
        private val smsListeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()
        private val smsBuffer = ArrayList<LogEntry>()
        private const val SMS_BUFFER_MAX = 50
        fun addSmsListener(l: (LogEntry) -> Unit) = smsListeners.add(l).also { replaySmsBuffer(l) }
        fun removeSmsListener(l: (LogEntry) -> Unit) = smsListeners.remove(l)
        fun notifySms(e: LogEntry) {
            synchronized(smsBuffer) {
                smsBuffer.add(e)
                if (smsBuffer.size > SMS_BUFFER_MAX) smsBuffer.removeAt(0)
            }
            smsListeners.forEach { it(e) }
            // Persist to disk using service context (Service extends Context)
            ScamDetectionService.instance?.let { appendSmsLog(it, e) }
        }
        private fun replaySmsBuffer(l: (LogEntry) -> Unit) {
            synchronized(smsBuffer) { smsBuffer.forEach(l) }
        }

        /** Multi-consumer risk stream — the overlay badge and the Home risk indicator both subscribe. */
        private val riskListeners = CopyOnWriteArrayList<(LogEntry) -> Unit>()
        fun addRiskListener(l: (LogEntry) -> Unit) = riskListeners.add(l)
        fun removeRiskListener(l: (LogEntry) -> Unit) = riskListeners.remove(l)
        private fun notifyRisk(e: LogEntry) = riskListeners.forEach { it(e) }

        /** Multi-consumer Audio Source stream — UI Dashboard and status badges subscribe. */
        @Volatile var activeAudioSource: String = "IDLE"
            private set
        private val audioSourceListeners = CopyOnWriteArrayList<(String) -> Unit>()
        fun addAudioSourceListener(l: (String) -> Unit) = audioSourceListeners.add(l).also { l(activeAudioSource) }
        fun removeAudioSourceListener(l: (String) -> Unit) = audioSourceListeners.remove(l)
        fun notifyAudioSource(sourceName: String) {
            activeAudioSource = sourceName
            audioSourceListeners.forEach { it(sourceName) }
        }

        /** Last server URL set by Start Monitoring / the simulator; the SMS path reuses it. */
        @Volatile var lastServerUrl: String? = null

        /** Package / packet cadence configuration for streaming audio. */
        const val PREF_PACKAGE_TIME_MS = "package_time_ms"
        const val DEFAULT_PACKAGE_TIME_MS = 100
        const val MIN_PACKAGE_TIME_MS = 80

        fun getPackageTimeMs(context: Context): Int {
            val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
            return maxOf(MIN_PACKAGE_TIME_MS, prefs.getInt(PREF_PACKAGE_TIME_MS, DEFAULT_PACKAGE_TIME_MS))
        }

        fun setPackageTimeMs(context: Context, ms: Int) {
            val valid = maxOf(MIN_PACKAGE_TIME_MS, ms)
            context.getSharedPreferences("app", Context.MODE_PRIVATE)
                .edit()
                .putInt(PREF_PACKAGE_TIME_MS, valid)
                .apply()
        }

        /** SMS log persistence. */
        private const val SMS_LOG_NAME = "sms_log.json"

        fun saveSmsRecord(
            context: Context,
            sender: String,
            senderType: String,
            body: String,
            risk: Int,
            reason: String,
            timestamp: Long = System.currentTimeMillis()
        ) {
            val logFile = File(context.filesDir, SMS_LOG_NAME)
            val list = if (logFile.exists()) {
                try { JSONArray(logFile.readText()) }
                catch (_: Exception) { JSONArray() }
            } else { JSONArray() }
            val obj = JSONObject()
                .put("sender", sender)
                .put("sender_type", senderType)
                .put("body", body)
                .put("risk", risk)
                .put("reason", reason)
                .put("text", reason)
                .put("timestamp", timestamp)
            list.put(obj)
            val tmp = File(context.filesDir, "${SMS_LOG_NAME}.tmp")
            tmp.writeText(list.toString(2))
            tmp.renameTo(logFile)
        }

        fun clearSmsLog(context: Context) {
            val logFile = File(context.filesDir, SMS_LOG_NAME)
            if (logFile.exists()) logFile.delete()
            synchronized(smsBuffer) {
                smsBuffer.clear()
            }
        }

        private fun appendSmsLog(context: Context, entry: LogEntry) {
            val senderType = entry.text.substringBefore(" | ")
            val reason = entry.text.substringAfter(" | ")
            saveSmsRecord(
                context = context,
                sender = "unknown",
                senderType = senderType,
                body = reason,
                risk = entry.risk,
                reason = reason
            )
        }

        fun loadSmsLog(context: Context): JSONArray {
            val logFile = File(context.filesDir, SMS_LOG_NAME)
            return if (logFile.exists()) {
                try { JSONArray(logFile.readText()) }
                catch (_: Exception) { JSONArray() }
            } else { JSONArray() }
        }

        /** True while the service is recording; CallStateManager uses it as the double-start guard. */
        @Volatile var active = false

        private val DLT_REGEX = Regex("^[A-Za-z]{2,3}-[A-Za-z0-9]{4,10}(-[A-Za-z0-9]{1,4})?$")
        private val NUMBER_REGEX = Regex("^\\+?[0-9]{10,15}$")

        /**
         * Classify an SMS sender address:
         * dlt_registered — 2-3 letter prefix + hyphen + 4-10 alphanumerics (any case), optionally a
         *                  short second segment (e.g. VM-HDFCBK, JK-SBIUPI-S, AX-IRSMSa-S)
         * plain_number   — 10-15 digits, optionally prefixed with + (e.g. +919876543210)
         * unknown        — anything else
         */
        fun classifySender(sender: String): String = when {
            DLT_REGEX.matches(sender) -> "dlt_registered"
            NUMBER_REGEX.matches(sender) -> "plain_number"
            else -> "unknown"
        }

        fun senderLabel(type: String) = when (type) {
            "dlt_registered" -> "[DLT Verified]"
            "plain_number" -> "[Plain Number]"
            else -> "[Unknown]"
        }

        /** Single entry point for real SMS (BroadcastReceiver) and the debug simulator. */
        fun handleIncomingSms(sender: String, body: String) {
            val ctx = instance?.applicationContext
            if (ctx != null) {
                handleIncomingSms(ctx, sender, body)
            } else {
                val url = lastServerUrl ?: run {
                    notifyLog(LogEntry("SMS: no server URL — set it above and Start Monitoring first"))
                    return
                }
                executeSmsCheck(url, null, sender, body)
            }
        }

        fun handleIncomingSms(context: Context, sender: String, body: String) {
            val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)
            val savedUrl = prefs.getString("server_url", "ws://10.0.2.2:8000/audio-stream") ?: "ws://10.0.2.2:8000/audio-stream"
            val rawUrl = lastServerUrl ?: savedUrl
            executeSmsCheck(rawUrl, context, sender, body)
        }

        private fun executeSmsCheck(rawUrl: String, context: Context?, sender: String, body: String) {
            val senderType = classifySender(sender)
            val label = senderLabel(senderType)
            notifyLog(LogEntry("SMS from $sender $label"))
            val client = OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

            val wsUrl = when {
                rawUrl.endsWith("/sms-check") -> rawUrl
                rawUrl.contains("/audio-stream") -> rawUrl.substringBeforeLast("/audio-stream") + "/sms-check"
                rawUrl.endsWith("/") -> rawUrl + "sms-check"
                else -> "$rawUrl/sms-check"
            }

            val request = try {
                Request.Builder().url(wsUrl).build()
            } catch (e: Exception) {
                notifyLog(LogEntry("SMS invalid URL: $wsUrl"))
                context?.let {
                    saveSmsRecord(it, sender, senderType, body, -1, "Invalid URL: $wsUrl")
                }
                return
            }

            fun sendViaHttpFallback() {
                val httpUrl = wsUrl.replace("ws://", "http://").replace("wss://", "https://")
                    .substringBeforeLast("/") + "/api/sms"
                try {
                    val jsonPayload = JSONObject()
                        .put("sender", sender)
                        .put("sender_type", senderType)
                        .put("body", body)
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val bodyReq = jsonPayload.toString().toRequestBody(mediaType)
                    val httpReq = Request.Builder().url(httpUrl).post(bodyReq).build()
                    client.newCall(httpReq).enqueue(object : okhttp3.Callback {
                        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                            val err = e.message ?: "unknown"
                            notifyLog(LogEntry("SMS error: $err"))
                            context?.let {
                                saveSmsRecord(it, sender, senderType, body, -1, "Server unreachable ($err)")
                            }
                            notifySms(LogEntry("$label | Offline scan pending", -1))
                        }

                        override fun onResponse(call: okhttp3.Call, response: Response) {
                            val respStr = response.body?.string() ?: ""
                            val obj = try { JSONObject(respStr) } catch (_: Exception) { null }
                            val rec = obj?.optJSONObject("record")
                            val risk = rec?.optInt("risk_score", -1) ?: -1
                            val serverReason = rec?.optString("reason", "")?.trim() ?: ""
                            val cleanReason = when {
                                serverReason.isNotEmpty() -> serverReason
                                risk in 0..29 -> "Legitimate message pattern"
                                risk >= 70 -> "High fraud probability detected"
                                risk >= 30 -> "Suspicious elements detected"
                                else -> "Processed"
                            }
                            val entry = LogEntry("SMS $label | $cleanReason", risk)
                            notifyLog(entry)
                            context?.let {
                                saveSmsRecord(
                                    it,
                                    sender = sender,
                                    senderType = senderType,
                                    body = body,
                                    risk = risk,
                                    reason = cleanReason,
                                    timestamp = System.currentTimeMillis()
                                )
                            }
                            notifySms(LogEntry("$label | $cleanReason", risk))
                        }
                    })
                } catch (e: Exception) {
                    notifyLog(LogEntry("SMS HTTP fallback error: ${e.message}"))
                }
            }

            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        val payload = JSONObject()
                            .put("sender", sender)
                            .put("sender_type", senderType)
                            .put("body", body)
                            .put("timestamp", System.currentTimeMillis())
                            .toString()
                        webSocket.send(payload)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val obj = try { JSONObject(text) } catch (_: Exception) { null }
                        val serverReason = obj?.optString("reason", "")?.trim() ?: ""
                        val risk = if (obj != null && obj.has("risk_score") && !obj.isNull("risk_score")) {
                            obj.optInt("risk_score", -1)
                        } else if (obj != null && obj.has("risk")) {
                            obj.optInt("risk", -1)
                        } else {
                            parseScore(text).risk
                        }
                        val cleanReason = when {
                            serverReason.isNotEmpty() -> serverReason
                            risk in 0..29 -> "Legitimate message pattern"
                            risk >= 70 -> "High fraud probability detected"
                            risk >= 30 -> "Suspicious elements detected"
                            else -> "Processed"
                        }
                        val entry = LogEntry("SMS $label | $cleanReason", risk)
                        notifyLog(entry)
                        context?.let {
                            saveSmsRecord(
                                it,
                                sender = sender,
                                senderType = senderType,
                                body = body,
                                risk = risk,
                                reason = cleanReason,
                                timestamp = obj?.optLong("timestamp", System.currentTimeMillis()) ?: System.currentTimeMillis()
                            )
                        }
                        notifySms(LogEntry("$label | $cleanReason", risk))
                        webSocket.close(1000, "done")
                        client.dispatcher.executorService.shutdown()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.d(TAG, "SMS WebSocket failed (${t.message}), falling back to HTTP REST /api/sms")
                        sendViaHttpFallback()
                    }
                }
            )
        }

        fun parseScore(text: String): LogEntry {
            return try {
                val o = JSONObject(text)
                // Handle silence response: risk_score is null
                if (!o.has("risk_score") || o.isNull("risk_score")) {
                    LogEntry("silence", -1)
                } else {
                    val risk = o.optInt("risk_score", -1)
                    val reason = o.optString("reason", "").trim()
                    val transcript = o.optString("transcript", "").trim()
                    val flags = o.optJSONArray("flags")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList()
                    
                    val cleanMsg = when {
                        transcript.isNotEmpty() && reason.isNotEmpty() -> "\"$transcript\" — $reason"
                        transcript.isNotEmpty() -> "\"$transcript\""
                        reason.isNotEmpty() -> reason
                        flags.isNotEmpty() -> "Flags: ${flags.joinToString(", ")}"
                        else -> "Risk Score: $risk"
                    }
                    LogEntry(cleanMsg, risk)
                }
            } catch (e: Exception) {
                LogEntry(text.take(120), -1)
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(180, TimeUnit.SECONDS)  // first chunk is slow: whisper + cold model load
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)  // keep the socket alive through long processing
        .build()
    @Volatile private var ws: WebSocket? = null
    @Volatile private var recording = false
    private var recordThread: Thread? = null

    // WebSocket lifecycle & reconnection
    @Volatile private var wsState = WsState.DISCONNECTED
    private var currentUrl: String? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val baseReconnectDelayMs = 2000L
    private val maxReconnectDelayMs = 10000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val shutdownRequested = AtomicInteger(0)

    // User-facing risk alerts are deliberately throttled: low scores stay quiet,
    // suspicious scores get a gentle reminder, and scams stay noticeable without
    // firing for every score packet received from the server.
    private var wasSuspicious = false
    private var lastSuspiciousAlertScore = -1
    private var lastSuspiciousAlertAtMs = 0L
    private var lastScamAlertAtMs = 0L
    private var alertTone: ToneGenerator? = null

    // Track last chunk for buffering during disconnect
    private var lastAudioChunk: ByteArray? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Clean up any existing stuck session first
        if (recording || wsState != WsState.DISCONNECTED) {
            Log.d(TAG, "onStartCommand: Cleaning up existing session (recording=$recording, wsState=$wsState)")
            cleanupSession()
        }

        startForeground(NOTIF_ID, buildNotification())
        EvidenceRecorder.init(applicationContext)
        CallStateManager.register(applicationContext)
        startService(Intent(this, OverlayService::class.java))

        // Best-effort: grant CAPTURE_AUDIO_OUTPUT via root (harmless on Oreo, required on 10+)
        if (RootHelper.hasRoot()) RootHelper.grantCaptureAudioOutputRoot(applicationContext)

        val url = intent?.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        recording = true
        active = true
        shutdownRequested.set(0)
        reconnectAttempts = 0
        lastServerUrl = url
        currentUrl = url
        lastLoggedText = ""
        resetRiskAlerts()
        val packageTimeMs = getPackageTimeMs(applicationContext)
        Log.d(TAG, "onStartCommand: Starting call audio monitoring with URL: $url (packageTime: ${packageTimeMs}ms)")
        log(LogEntry("Connecting to $url (package time: ${packageTimeMs}ms)"))
        connect(url)
        recordThread = Thread(::recordLoop, "mic").apply { start() }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        CallStateManager.register(applicationContext)
        alertTone = try {
            ToneGenerator(AudioManager.STREAM_ALARM, 80)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unable to initialize risk alert tone", e)
            null
        }
    }

    override fun onDestroy() {
        shutdownRequested.set(1)
        cleanupSession()
        alertTone?.release()
        alertTone = null
        CallStateManager.unregister()
        instance = null
        super.onDestroy()
    }

    private fun cleanupSession() {
        recording = false
        active = false
        lastLoggedText = ""
        wsState = WsState.DISCONNECTED
        EvidenceRecorder.stop()
        stopService(Intent(this, OverlayService::class.java))
        ws?.close(1000, "stopped")
        ws = null
        currentUrl = null
        lastAudioChunk = null
        reconnectAttempts = 0
        resetRiskAlerts()
        recordThread?.interrupt()
        recordThread = null
        client.dispatcher.executorService.shutdown()
    }

    private fun connect(url: String) {
        if (shutdownRequested.get() == 1) return
        wsState = WsState.CONNECTING
        Log.d(TAG, "connect: Attempting WebSocket connection to $url (attempt ${reconnectAttempts + 1})")
        mainHandler.post { log(LogEntry("Connecting...")) }
        ws = client.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (shutdownRequested.get() == 1) {
                        webSocket.close(1000, "shutdown")
                        return
                    }
                    wsState = WsState.CONNECTED
                    reconnectAttempts = 0
                    Log.d(TAG, "onOpen: WebSocket connected, response: $response")
                    mainHandler.post { log(LogEntry("Connected")) }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val e = parseScore(text)
                    // Skip silence and heartbeat clutter from user-visible log
                    val isSilence = e.risk < 0 ||
                            e.text.equals("silence", ignoreCase = true) ||
                            e.text.contains("No speech detected", ignoreCase = true) ||
                            e.text.contains("Stream initialized", ignoreCase = true) ||
                            e.text.isBlank() ||
                            (e.risk == 0 && !e.text.startsWith("\""))

                    val o = try { JSONObject(text) } catch (_: Exception) { null }
                    val isPartial = o?.optBoolean("is_partial", false) ?: false

                    // If it is a low-risk partial, do not flood the transcript log; wait for the final sentence.
                    // Always log final sentences (isPartial == false) or immediate high risk alerts (risk >= 40).
                    val shouldLog = !isSilence && (!isPartial || e.risk >= 40)
                    if (shouldLog) {
                        mainHandler.post { log(e) }
                    }
                    // Skip silence responses (risk == -1)
                    if (e.risk >= 0) {
                        notifyRisk(e)
                        alertForRisk(e.risk)
                        EvidenceRecorder.onRisk(e.risk, CallStateManager.lastCallNumber)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (shutdownRequested.get() == 1) return
                    Log.e(TAG, "onFailure: WebSocket error", t)
                    handleDisconnect("WS error: ${t.message ?: "unknown"}")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "onClosing: WebSocket closing, code=$code, reason=$reason")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (shutdownRequested.get() == 1) return
                    Log.d(TAG, "onClosed: WebSocket closed, code=$code, reason=$reason")
                    handleDisconnect("Closed ($code $reason)")
                }
            }
        )
    }

    /**
     * Alerts are driven by server score updates, so they do not need a separate
     * repeating job and automatically stop when scoring stops.
     */
    @Synchronized
    private fun alertForRisk(risk: Int) {
        val now = SystemClock.elapsedRealtime()
        when {
            risk >= SCAM_RISK_THRESHOLD -> {
                wasSuspicious = false
                // A scam warning is audible and haptic, but never more than once every 8 seconds.
                if (lastScamAlertAtMs == 0L ||
                    now - lastScamAlertAtMs >= SCAM_ALERT_INTERVAL_MS
                ) {
                    vibrateScamWarning()
                    alertTone?.startTone(ToneGenerator.TONE_PROP_BEEP2, SCAM_TONE_DURATION_MS)
                    lastScamAlertAtMs = now
                }
            }

            risk >= SUSPICIOUS_RISK_THRESHOLD -> {
                // Pulse once on entry. Repeat only after 10 seconds when the exact
                // suspicious score remains unchanged, preventing score fluctuations
                // from causing constant vibration.
                val enteringSuspicious = !wasSuspicious
                if (enteringSuspicious ||
                    (risk == lastSuspiciousAlertScore &&
                        now - lastSuspiciousAlertAtMs >= SUSPICIOUS_ALERT_INTERVAL_MS)
                ) {
                    vibrateSuspiciousWarning()
                    lastSuspiciousAlertScore = risk
                    lastSuspiciousAlertAtMs = now
                }
                wasSuspicious = true
            }

            else -> {
                // Clean scores reset the next suspicious threshold crossing.
                wasSuspicious = false
                lastSuspiciousAlertScore = -1
                lastSuspiciousAlertAtMs = 0L
            }
        }
    }

    private fun vibrateSuspiciousWarning() {
        vibrator()?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120), -1)
        )
    }

    private fun vibrateScamWarning() {
        vibrator()?.vibrate(VibrationEffect.createOneShot(450, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibrator(): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "Unable to access vibrator", e)
        null
    }

    private fun resetRiskAlerts() {
        wasSuspicious = false
        lastSuspiciousAlertScore = -1
        lastSuspiciousAlertAtMs = 0L
        lastScamAlertAtMs = 0L
    }

    private fun handleDisconnect(reason: String) {
        if (wsState == WsState.DISCONNECTED) return
        wsState = WsState.DISCONNECTED
        mainHandler.post { log(LogEntry("Connection lost: $reason")) }
        if (recording && shutdownRequested.get() == 0) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.d(TAG, "scheduleReconnect: Max reconnect attempts reached, giving up")
            mainHandler.post { log(LogEntry("Max reconnect attempts reached. Stop Monitoring to retry.")) }
            return
        }
        wsState = WsState.RECONNECTING
        reconnectAttempts++
        val delay = minOf((baseReconnectDelayMs * Math.pow(2.0, (reconnectAttempts - 1).toDouble())).toLong(), maxReconnectDelayMs)
        Log.d(TAG, "scheduleReconnect: Scheduling reconnect attempt $reconnectAttempts in ${delay}ms")
        mainHandler.post { log(LogEntry("Reconnecting... (attempt $reconnectAttempts)")) }
        mainHandler.postDelayed({
            val url = currentUrl ?: return@postDelayed  // service stopped; cancel reconnect
            connect(url)
        }, delay)
    }

    private fun recordLoop() {
        var hasLoggedWaiting = false

        // Outer loop: keeps the background worker alive as long as monitoring is enabled
        while (recording && !Thread.currentThread().isInterrupted()) {
            val isCall = CallStateManager.checkCallActive(applicationContext)

            // Strictly wait for an active phone call
            if (!isCall) {
                if (!hasLoggedWaiting) {
                    log(LogEntry("Call monitoring active — waiting for active phone call."))
                    hasLoggedWaiting = true
                }
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                continue
            }

            hasLoggedWaiting = false
            log(LogEntry("Call detected — initializing call audio recorder."))

            // Read user preference for Audio Source Mode
            val prefs = applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE)
            val audioSourceMode = prefs.getString("audio_source_mode", "HARDWARE_CALL") ?: "HARDWARE_CALL"

            val audioSources = if (audioSourceMode == "FORCE_MIC") {
                intArrayOf(
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                )
            } else {
                intArrayOf(
                    MediaRecorder.AudioSource.VOICE_CALL,
                    MediaRecorder.AudioSource.VOICE_UPLINK,
                    MediaRecorder.AudioSource.VOICE_DOWNLINK,
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    MediaRecorder.AudioSource.MIC
                )
            }

            var currentPackageTimeMs = getPackageTimeMs(applicationContext)
            var framesPerChunk = (16000 * currentPackageTimeMs) / 1000
            var bytesPerChunk = framesPerChunk * 2

            var record: AudioRecord? = null
            var selectedSource = -1

            for (source in audioSources) {
                try {
                    val minBufferSize = AudioRecord.getMinBufferSize(
                        16000,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    if (minBufferSize <= 0) continue

                    val candidate = AudioRecord(
                        source,
                        16000,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(minBufferSize * 2, bytesPerChunk * 2)
                    )

                    if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                        record = candidate
                        selectedSource = source
                        break
                    } else {
                        candidate.release()
                    }
                } catch (_: Exception) {
                    // Source not supported, proceed to next candidate
                }
            }

            if (record == null) {
                notifyAudioSource("ERROR (Init Failed)")
                log(LogEntry("Error: Failed to initialize call audio source"))
                try { Thread.sleep(2000) } catch (e: InterruptedException) { break }
                continue
            }

            // Audio streaming loop for active call
            try {
                record.startRecording()
                val sourceName = when (selectedSource) {
                    MediaRecorder.AudioSource.VOICE_CALL -> "VOICE_CALL (Hardware Call)"
                    MediaRecorder.AudioSource.VOICE_UPLINK -> "VOICE_UPLINK (Hardware Uplink)"
                    MediaRecorder.AudioSource.VOICE_DOWNLINK -> "VOICE_DOWNLINK (Hardware Downlink)"
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
                    MediaRecorder.AudioSource.MIC -> if (audioSourceMode == "FORCE_MIC") "MIC (Microphone Forced)" else "MIC (Microphone Fallback)"
                    else -> "Source $selectedSource"
                }

                notifyAudioSource(sourceName)
                log(LogEntry("Recording call audio via source: $sourceName (packet: ${currentPackageTimeMs}ms, ${bytesPerChunk}B)"))
                updateNotificationText("Call Monitoring Active | $sourceName | ${currentPackageTimeMs}ms")

                var shortBuf = ShortArray(minOf(1024, framesPerChunk))
                val chunk = ByteArrayOutputStream()
                var skippedSilenceCount = 0
                var consecutiveSilenceChunks = 0
                var minSilenceChunksBeforeSkip = maxOf(1, 300 / currentPackageTimeMs)
                var totalChunksSent = 0
                var lastSendLogTime = 0L
                var lastSilenceLogTime = 0L

                while (recording && CallStateManager.checkCallActive(applicationContext)) {
                    val neededSamples = framesPerChunk - (chunk.size() / 2)
                    val toRead = minOf(shortBuf.size, neededSamples)
                    val readCount = record.read(shortBuf, 0, toRead)
                    if (readCount <= 0) continue

                    val pcm = ShortArrayToPcmBytes(shortBuf, readCount)
                    chunk.write(pcm)

                    if (chunk.size() >= bytesPerChunk) {
                        val data = chunk.toByteArray()
                        chunk.reset()

                        val rms = calculateRms(data)
                        val isSilenceFilterEnabled = prefs.getBoolean("silence_filter_enabled", true)
                        val formattedRms = String.format(java.util.Locale.US, "%.1f", rms)

                        if (isSilenceFilterEnabled && rms < 50.0) {
                            consecutiveSilenceChunks++
                            if (consecutiveSilenceChunks > minSilenceChunksBeforeSkip) {
                                skippedSilenceCount++
                                continue
                            }
                        } else {
                            consecutiveSilenceChunks = 0
                        }

                        if (wsState == WsState.CONNECTED) {
                            totalChunksSent++
                            // Log periodically to LogCat to avoid spamming 10x per second
                            if (totalChunksSent == 1 || totalChunksSent % 50 == 0) {
                                Log.d(TAG, "Sent audio packet #$totalChunksSent: size=${data.size}B, rms=$formattedRms, source=$sourceName")
                            }
                            ws?.send((wavHeader(data.size) + data).toByteString())
                        } else {
                            mainHandler.post { log(LogEntry("Connection lost — buffering audio")) }
                        }

                        // Dynamically update package time if changed in preferences during active call
                        val newPackageTimeMs = getPackageTimeMs(applicationContext)
                        if (newPackageTimeMs != currentPackageTimeMs) {
                            currentPackageTimeMs = newPackageTimeMs
                            framesPerChunk = (16000 * currentPackageTimeMs) / 1000
                            bytesPerChunk = framesPerChunk * 2
                            shortBuf = ShortArray(minOf(1024, framesPerChunk))
                            minSilenceChunksBeforeSkip = maxOf(1, 300 / currentPackageTimeMs)
                            updateNotificationText("Call Monitoring Active | $sourceName | ${currentPackageTimeMs}ms")
                            log(LogEntry("[SETTING] Package time updated to ${currentPackageTimeMs}ms during active call"))
                        }
                    }
                }

                // Flush remaining audio in buffer if call ended mid-chunk
                if (chunk.size() > 0) {
                    val data = chunk.toByteArray()
                    chunk.reset()
                    if (wsState == WsState.CONNECTED) {
                        totalChunksSent++
                        ws?.send((wavHeader(data.size) + data).toByteString())
                        Log.d(TAG, "Flushed final audio chunk: size=${data.size} bytes")
                    }
                }
                // Notify server that call audio stream has finished
                if (wsState == WsState.CONNECTED) {
                    ws?.send("END")
                    Log.d(TAG, "Sent END signal to server")
                }
            } catch (e: Exception) {
                log(LogEntry("Recording loop error: ${e.message}"))
            } finally {
                try {
                    record.stop()
                } catch (_: Exception) {}
                record.release()
                notifyAudioSource("IDLE")
                updateNotificationText("Call Monitoring Active")
                log(LogEntry("Call ended — released call audio resources"))
            }
        }
    }

    /** Calculate Root Mean Square (RMS) volume of 16-bit PCM byte array. */
    private fun calculateRms(pcmData: ByteArray): Double {
        val sampleCount = pcmData.size / 2
        if (sampleCount <= 0) return 0.0
        var sumSq = 0.0
        var i = 0
        while (i < pcmData.size - 1) {
            val low = pcmData[i].toInt() and 0xFF
            val high = pcmData[i + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toDouble()
            sumSq += sample * sample
            i += 2
        }
        return Math.sqrt(sumSq / sampleCount)
    }

    /** little-endian PCM_16 -> raw bytes, first [count] shorts only */
    private fun ShortArrayToPcmBytes(samples: ShortArray, count: Int): ByteArray {
        val out = ByteArray(count * 2)
        var j = 0
        for (i in 0 until count) {
            val s = samples[i].toInt()
            out[j++] = (s and 0xFF).toByte()
            out[j++] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** 44-byte RIFF/WAVE header for 16kHz mono 16-bit PCM. */
    private fun wavHeader(dataSize: Int): ByteArray {
        val h = ByteArray(44)
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(h, 0)
        writeLeInt(h, 4, 36 + dataSize)
        "WAVE".toByteArray(Charsets.US_ASCII).copyInto(h, 8)
        "fmt ".toByteArray(Charsets.US_ASCII).copyInto(h, 12)
        writeLeInt(h, 16, 16)     // fmt chunk size
        writeLeShort(h, 20, 1)    // PCM
        writeLeShort(h, 22, 1)    // mono
        writeLeInt(h, 24, 16000)  // sample rate
        writeLeInt(h, 28, 32000)  // byte rate
        writeLeShort(h, 32, 2)    // block align
        writeLeShort(h, 34, 16)   // bits per sample
        "data".toByteArray(Charsets.US_ASCII).copyInto(h, 36)
        writeLeInt(h, 40, dataSize)
        return h
    }

    private fun writeLeInt(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
        b[off + 2] = ((v shr 16) and 0xFF).toByte()
        b[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun writeLeShort(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun log(entry: LogEntry) {
        notifyLog(entry)
    }

    private fun updateNotificationText(text: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Echoguard")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build()
            notificationManager.notify(NOTIF_ID, notification)
        } catch (_: Exception) {}
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Echoguard", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Echoguard")
            .setContentText("Monitoring active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
}
