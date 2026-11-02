package com.example.scamdetection

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** SMS-specific alerts screen (cleaner than the raw log). */
class SmsAlertsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private val components by lazy { DesignComponents(this) }
    private var hasContent = false
    private val smsListener: (LogEntry) -> Unit = { entry ->
        runOnUiThread { addEntry(entry) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = "SMS Alerts"

        val root = buildSmsUI()
        setContentView(root)

        // Load persisted SMS history
        loadSmsHistory()

        // Show empty state initially
        if (!hasContent) {
            showEmptyState()
        }

        ScamDetectionService.addSmsListener(smsListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        ScamDetectionService.removeSmsListener(smsListener)
    }

    /** Builds the SMS UI for the activity or fragment. */
    fun buildSmsUIForFragment(): View {
        return buildSmsUI()
    }

    private fun buildSmsUI(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor(DesignTheme.Colors.BACKGROUND))
        }

        val scroll = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpi(DesignTheme.Spacing.MD),
                dpi(DesignTheme.Spacing.MD),
                dpi(DesignTheme.Spacing.MD),
                dpi(DesignTheme.Spacing.MD)
            )
        }
        scroll.addView(container)
        root.addView(scroll)
        return root
    }

    private fun loadSmsHistory() {
        val history = ScamDetectionService.loadSmsLog(this)
        if (history.length() > 0) {
            // Show newest first (reverse iteration)
            for (i in history.length() - 1 downTo 0) {
                val obj = history.getJSONObject(i)
                val senderType = obj.optString("sender_type", "")
                val text = obj.optString("text", "")
                val risk = obj.optInt("risk", -1)
                addEntry(LogEntry("$senderType | $text", risk))
            }
        }
    }

    private fun showEmptyState() {
        val emptyView = components.createBody(
            "No SMS alerts yet. Incoming messages will be analyzed here.",
            "md",
            DesignTheme.Colors.ON_SURFACE_VARIANT
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dpi(DesignTheme.Spacing.XL * 2), 0, 0)
        }
        container.addView(emptyView)
    }

    private fun addEntry(entry: LogEntry) {
        if (!hasContent) {
            container.removeAllViews()
            hasContent = true
        }
        val riskColor = DesignTheme.getRiskColor(entry.risk)
        val riskLabel = DesignTheme.getRiskLabel(entry.risk)

        val card = components.createCard(
            backgroundColor = DesignTheme.Colors.SURFACE_CONTAINER_HIGH,
            paddingDp = DesignTheme.Spacing.MD,
            cornerRadiusDp = DesignTheme.Radius.MEDIUM
        ) { cardLayout: LinearLayout ->
            val riskTv = components.createLabel(
                "$riskLabel: ${entry.risk}",
                "md",
                DesignTheme.Colors.ON_SURFACE_VARIANT
            ).apply {
                setTextColor(riskColor)
            }
            cardLayout.addView(riskTv)

            val bodyTv = components.createBody(
                entry.text,
                "md",
                DesignTheme.Colors.ON_SURFACE_VARIANT
            ).apply {
                setPadding(0, dpi(DesignTheme.Spacing.SM), 0, 0)
            }
            cardLayout.addView(bodyTv)
        }

        container.addView(card, 0) // newest on top

        if (container.childCount > 20) container.removeViewAt(container.childCount - 1)
    }

    private fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dpi(v: Number): Int = dp(v.toFloat()).toInt()
}
