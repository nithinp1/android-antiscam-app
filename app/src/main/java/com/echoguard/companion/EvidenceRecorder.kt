package com.echoguard.companion

import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Records raw call audio when risk crosses a threshold (default 60).
 * Chain-of-custody: SHA-256 hash stored in evidence_log.json alongside metadata.
 * Requires root on this H930DS (Magisk) — uses RootHelper to access VOICE_CALL source.
 */
object EvidenceRecorder {
    private const val TAG = "EvidenceRecorder"
    private const val THRESHOLD = 60
    private const val LOG_NAME = "evidence_log.json"

    private var ctx: Context? = null
    private var recording = AtomicBoolean(false)
    private var pid = AtomicReference<Int?>(null)
    private var currentFile = AtomicReference<File?>(null)
    private var maxRisk = AtomicInteger(0)
    private var callNumber = AtomicReference<String?>(null)

    fun init(context: Context) {
        ctx = context.applicationContext
    }

    fun onRisk(risk: Int, number: String?) {
        if (risk < THRESHOLD && !recording.get()) return // below threshold, not recording
        if (risk >= THRESHOLD && !recording.get()) {
            start(number, risk)
        }
        if (recording.get()) {
            maxRisk.accumulateAndGet(risk) { a, b -> maxOf(a, b) }
        }
    }

    private fun start(number: String?, initialRisk: Int) {
        val c = ctx ?: return
        maxRisk.set(initialRisk)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val safeNum = number?.replace("[^0-9+]".toRegex(), "") ?: "unknown"
        val name = "rec_${stamp}_${safeNum}_risk${initialRisk}.wav"
        val dir = File(c.filesDir, "evidence")
        dir.mkdirs()
        val f = File(dir, name)
        currentFile.set(f)
        callNumber.set(safeNum)
        // Start recording via root
        val started = RootHelper.startCallRecord(c, f.absolutePath)?.also { pid.set(it) } != null
        if (started) {
            recording.set(true)
            Log.i(TAG, "Started evidence recording: ${f.absolutePath}")
        } else {
            Log.w(TAG, "Failed to start evidence recording (root/arecord missing?)")
        }
    }

    fun stop() {
        if (!recording.getAndSet(false)) return
        val p = pid.getAndSet(null)
        p?.let { RootHelper.stopCallRecord(it) }
        val f = currentFile.getAndSet(null)
        if (f != null && f.exists()) {
            // finalise: add hash + metadata to log
            finalizeRecording(f)
        }
        Log.i(TAG, "Stopped evidence recording")
    }

    private fun finalizeRecording(wavFile: File) {
        val c = ctx ?: return
        val hash = sha256(wavFile)
        val entry = JSONObject()
            .put("file", wavFile.name)
            .put("path", wavFile.absolutePath)
            .put("timestamp", System.currentTimeMillis())
            .put("phone_number", callNumber.get())
            .put("max_risk", maxRisk.get())
            .put("sha256", hash)
        appendLog(c, entry)
        Log.i(TAG, "Evidence saved: ${wavFile.name} hash=$hash")
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val fis = file.inputStream()
        val buffer = ByteArray(8192)
        var n: Int
        while (fis.read(buffer).also { n = it } > 0) {
            digest.update(buffer, 0, n)
        }
        fis.close()
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    private fun appendLog(context: Context, entry: JSONObject) {
        val logFile = File(context.filesDir, LOG_NAME)
        val list = if (logFile.exists()) {
            try {
                val text = logFile.readText(StandardCharsets.UTF_8)
                org.json.JSONArray(text)
            } catch (_: Exception) {
                org.json.JSONArray()
            }
        } else {
            org.json.JSONArray()
        }
        list.put(entry)
        // atomic write
        val tmp = File(context.filesDir, "${LOG_NAME}.tmp")
        tmp.writeText(list.toString(2), StandardCharsets.UTF_8)
        tmp.renameTo(logFile)
    }

    fun loadLog(context: Context): org.json.JSONArray {
        val logFile = File(context.filesDir, LOG_NAME)
        return if (logFile.exists()) {
            try { org.json.JSONArray(logFile.readText(StandardCharsets.UTF_8)) }
            catch (_: Exception) { org.json.JSONArray() }
        } else {
            org.json.JSONArray()
        }
    }
}
