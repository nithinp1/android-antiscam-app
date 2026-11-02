package com.example.scamdetection

import android.content.Context
import android.util.Log

/** Small wrapper around root/shell commands for this rooted H930DS device. */
object RootHelper {
    private const val TAG = "RootHelper"

    /** Try to run a command as root via `su -c`. Returns exit code, stdout, stderr. */
    fun runRoot(cmd: String): Pair<Int, String> {
        try {
            val proc = Runtime.getRuntime().exec("su")
            proc.outputStream.use { out ->
                out.write("$cmd\n".toByteArray())
                out.write("exit\n".toByteArray())
                out.flush()
            }
            var stderr = ""
            val errThread = Thread {
                try { stderr = proc.errorStream.bufferedReader().readText() } catch (_: Exception) {}
            }.apply { start() }
            val stdout = proc.inputStream.bufferedReader().readText()
            errThread.join()
            val exit = proc.waitFor()
            Log.d(TAG, "root [$cmd] exit=$exit out=$stdout err=$stderr")
            return exit to stdout.trim()
        } catch (e: Exception) {
            Log.e(TAG, "root [$cmd] failed", e)
            return -1 to ""
        }
    }

    /** Check if root is available (Magisk). */
    fun hasRoot(): Boolean = runRoot("id").first == 0

    /** Grant SYSTEM_ALERT_WINDOW via appops (root path) — works on rooted LineageOS. */
    fun grantOverlayPermRoot(ctx: Context): Boolean {
        val pkg = ctx.packageName
        val (code, _) = runRoot("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        return code == 0
    }

    /** Check if SYSTEM_ALERT_WINDOW is currently granted (root query). */
    fun checkOverlayPermRoot(ctx: Context): Boolean {
        val pkg = ctx.packageName
        val (code, out) = runRoot("appops get $pkg SYSTEM_ALERT_WINDOW")
        return code == 0 && out.contains("allow")
    }

    /** Grant CAPTURE_AUDIO_OUTPUT via pm (root) — ensures VOICE_CALL/VOICE_DOWNLINK audio routing works. */
    fun grantCaptureAudioOutputRoot(ctx: Context): Boolean {
        val pkg = ctx.packageName
        val (code, _) = runRoot("pm grant $pkg android.permission.CAPTURE_AUDIO_OUTPUT")
        return code == 0
    }



    /** Start a background shell process that records call audio from VOICE_CALL source.
     * Returns PID of the started process (for later stop/kill). */
    fun startCallRecord(ctx: Context, outPath: String): Int? {
        // tinycap / arecord path varies; use `su -c "arecord ..."` if present, else fallback
        val cmd = "arecord -f cd -D voice_call -r 8000 -c 1 -t wav $outPath 2>/dev/null & echo \$!"
        val (code, out) = runRoot(cmd)
        return if (code == 0) out.trim().toIntOrNull() else null
    }

    /** Stop a recording by PID. */
    fun stopCallRecord(pid: Int): Boolean {
        val (code, _) = runRoot("kill $pid 2>/dev/null; sleep 0.2; kill -9 $pid 2>/dev/null")
        return code == 0
    }
}
