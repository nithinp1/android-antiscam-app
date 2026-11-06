package com.echoguard.companion

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * SYSTEM_ALERT_WINDOW overlay showing live risk score.
 * - Small badge (collapsed) + expanded panel on tap when risk >= 70.
 * - Draggable, dismissable (long-press or drag off-screen).
 * - Permission granted via root appops (RootHelper) with Settings fallback.
 * - Uses Obsidian Shield design language (glass cards, accent colors).
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var collapsedBadge: FrameLayout? = null
    private var expandedPanel: FrameLayout? = null
    private var isExpanded = false
    private var currentRisk = -1

    private val mainHandler = Handler(Looper.getMainLooper())

    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var dragThreshold = 20f

    private data class ExpandedPanelViews(
        val scoreTv: TextView,
        val labelTv: TextView,
        val descTv: TextView
    )

    private val riskListener: (LogEntry) -> Unit = { entry ->
        updateRisk(entry.risk, entry.text)
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildOverlay()
        addOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ScamDetectionService.addRiskListener(riskListener)
        return START_STICKY
    }

    override fun onDestroy() {
        ScamDetectionService.removeRiskListener(riskListener)
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildOverlay() {
        val dm = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(dm)
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - dpi(72) // top-right corner initially
            y = dpi(120)
        }

        rootView = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addView(buildCollapsedBadge())
            addView(buildExpandedPanel().apply { visibility = View.GONE })
            setOnTouchListener(touchListener)
        }
    }

    private fun buildCollapsedBadge(): View {
        val size = dpi(56)
        collapsedBadge = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
        }

        // Glass card background with accent border
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#CC000000")) // dark glass
            setStroke(dpi(2), DesignTheme.Colors.SECONDARY.toInt())
        }
        collapsedBadge?.background = bg

        // Shield icon instead of exclamation
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_shield_logo)
            setColorFilter(DesignTheme.Colors.SECONDARY.toInt(), PorterDuff.Mode.SRC_IN)
            layoutParams = FrameLayout.LayoutParams(
                dpi(28), dpi(28), Gravity.CENTER
            )
        }
        collapsedBadge?.addView(icon)

        // Risk number inside the badge (small)
        val riskTv = TextView(this).apply {
            text = "—"
            textSize = 11f
            setTextColor(Color.WHITE)
            setShadowLayer(2f, 0f, 0f, Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { setMargins(dp(0f).toInt(), dp(0f).toInt(), dp(0f).toInt(), dp(6f).toInt()) }
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        collapsedBadge?.addView(riskTv)
        collapsedBadge?.tag = riskTv
        return collapsedBadge!!
    }

    private fun buildExpandedPanel(): View {
        expandedPanel = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dpi(260), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }

        // Glass card container
        val card = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16f)
            setColor(Color.parseColor("#E6000000")) // dark glass
            setStroke(dpi(1.5), DesignTheme.Colors.SECONDARY.toInt())
        }
        card.background = bg
        card.setPadding(dp(20f).toInt(), dp(20f).toInt(), dp(20f).toInt(), dp(20f).toInt())

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Title
        val title = TextView(this).apply {
            text = "SCAM RISK"
            textSize = 12f
            setTextColor(DesignTheme.Colors.ON_SURFACE_VARIANT.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        }
        content.addView(title)

        // Spacer
        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(8))
        })

        // RISK SCORE - BOXED IN MIDDLE (prominent, centered)
        val scoreBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpi(100), dpi(100)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        val scoreBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#33000000"))
            setStroke(dpi(3), DesignTheme.Colors.SECONDARY.toInt())
        }
        scoreBox.background = scoreBg

        val scoreTv = TextView(this).apply {
            text = "—"
            textSize = 36f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER
            )
            id = View.generateViewId()
        }
        scoreBox.addView(scoreTv)
        content.addView(scoreBox)

        // Risk label below score
        val labelTv = TextView(this).apply {
            text = "IDLE"
            textSize = 14f
            setTextColor(DesignTheme.Colors.SECONDARY.toInt())
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(8)
            }
        }
        content.addView(labelTv)

        // Detail text
        val desc = TextView(this).apply {
            text = "Monitoring active call…"
            textSize = 11f
            setTextColor(Color.parseColor("#AAFFFFFF"))
            gravity = Gravity.CENTER
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(4)
            }
        }
        content.addView(desc)

        // Spacer
        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpi(8))
        })

        // Dismiss button - collapses to badge, doesn't remove overlay
        val dismissBtn = TextView(this).apply {
            text = "✕ Minimize"
            textSize = 11f
            setTextColor(Color.parseColor("#88FFFFFF"))
            gravity = Gravity.CENTER
            setPadding(dp(16f).toInt(), dp(8f).toInt(), dp(16f).toInt(), dp(8f).toInt())
            setOnClickListener { collapse() }
        }
        content.addView(dismissBtn)

        card.addView(content)
        expandedPanel?.addView(card)

        // Store references
        expandedPanel?.tag = ExpandedPanelViews(scoreTv, labelTv, desc)

        return expandedPanel!!
    }

    private val touchListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params?.x?.toFloat() ?: 0f
                initialY = params?.y?.toFloat() ?: 0f
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (Math.hypot(dx.toDouble(), dy.toDouble()) > dragThreshold) {
                    params?.x = (initialX + dx).toInt()
                    params?.y = (initialY + dy).toInt()
                    windowManager?.updateViewLayout(rootView!!, params!!)
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (Math.hypot(dx.toDouble(), dy.toDouble()) < dragThreshold) {
                    // tap — toggle expand if risk >= 70
                    if (currentRisk >= 70 && !isExpanded) expand()
                    else if (isExpanded) collapse()
                } else if (isOffScreen(params!!.x, params!!.y)) {
                    collapse() // drag off-screen just collapses, doesn't dismiss
                }
                true
            }
        }
        false
    }

    private fun isOffScreen(x: Int, y: Int): Boolean {
        val dm = DisplayMetrics()
        windowManager?.defaultDisplay?.getMetrics(dm)
        return x < -dpi(50) || y < -dpi(50) || x > dm.widthPixels - dpi(20) || y > dm.heightPixels - dpi(20)
    }

    private fun expand() {
        mainHandler.post {
            collapsedBadge?.visibility = View.GONE
            expandedPanel?.visibility = View.VISIBLE
            isExpanded = true
        }
    }

    private fun collapse() {
        mainHandler.post {
            expandedPanel?.visibility = View.GONE
            collapsedBadge?.visibility = View.VISIBLE
            isExpanded = false
        }
    }

    private fun updateRisk(risk: Int, detail: String) {
        mainHandler.post {
            currentRisk = risk
            val (color, label) = when {
                risk >= 70 -> Pair(DesignTheme.Colors.RISK_HIGH.toInt(), "CRITICAL")
                risk >= 40 -> Pair(DesignTheme.Colors.RISK_MEDIUM.toInt(), "ELEVATED")
                risk >= 0 -> Pair(DesignTheme.Colors.RISK_LOW.toInt(), "LOW")
                else -> Pair(DesignTheme.Colors.SECONDARY.toInt(), "IDLE")
            }

            val collapsedTv = collapsedBadge?.tag as? TextView
            val expandedViews = expandedPanel?.tag as? ExpandedPanelViews

            val cleanDetail = cleanOverlayText(detail)
            collapsedTv?.text = if (risk >= 0) risk.toString() else "—"
            collapsedTv?.setTextColor(color)
            expandedViews?.scoreTv?.text = if (risk >= 0) risk.toString() else "—"
            expandedViews?.scoreTv?.setTextColor(color)
            expandedViews?.labelTv?.text = label
            expandedViews?.labelTv?.setTextColor(color)
            expandedViews?.descTv?.text = cleanDetail

            // Auto-expand on high risk
            if (risk >= 70 && !isExpanded) expand()
            else if (risk < 70 && isExpanded) collapse()
        }
    }

    private fun cleanOverlayText(raw: String): String {
        if (raw.isBlank() || raw == "silence") return "Monitoring active call…"
        var s = raw.trim()

        if (s.startsWith("{") && s.endsWith("}")) {
            try {
                val json = org.json.JSONObject(s)
                val t = json.optString("transcript", "").trim()
                val r = json.optString("reason", "").trim()
                if (t.isNotEmpty() && r.isNotEmpty()) return "\"$t\" — $r"
                if (t.isNotEmpty()) return "\"$t\""
                if (r.isNotEmpty()) return r
            } catch (_: Exception) {}
        }

        s = s.replace(Regex("^risk=\\d+\\s*\\|?\\s*"), "")
        s = s.trim('"').trim()
        return if (s.isNotEmpty()) s.take(120) else "Monitoring active call…"
    }

    private fun addOverlay() {
        rootView?.let { windowManager?.addView(it, params!!) }
        if (!hasOverlayPermission()) requestOverlayPermission()
    }

    private fun removeOverlay() {
        rootView?.let { windowManager?.removeView(it) }
        rootView = null
    }

    private fun hasOverlayPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 23) return true
        return Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        if (RootHelper.hasRoot()) {
            if (RootHelper.grantOverlayPermRoot(this)) return
        }
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:$packageName"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dpi(v: Number): Int = dp(v.toFloat()).toInt()

    // Extension to parse color hex string
    private fun String.toInt(): Int = Color.parseColor(this)
}