package com.echoguard.companion

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Technical log fragment - self-contained raw log UI with persisted history. */
class TechnicalLogFragment : Fragment() {

    private lateinit var logView: TextView
    private lateinit var components: DesignComponents
    private var hasContent = false

    private val logListener: (LogEntry) -> Unit = { entry ->
        if (isAdded) {
            activity?.runOnUiThread { append(entry) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        components = DesignComponents(requireContext())

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor(DesignTheme.Colors.BACKGROUND))
        }

        val scroll = ScrollView(requireContext()).apply {
            setPadding(
                dpi(DesignTheme.Spacing.MD),
                dpi(DesignTheme.Spacing.MD),
                dpi(DesignTheme.Spacing.MD),
                dpi(DesignTheme.Spacing.MD)
            )
        }

        logView = components.createBody(
            "No logs yet. Start monitoring to see real-time analysis.",
            "md",
            DesignTheme.Colors.ON_SURFACE_VARIANT
        ).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        scroll.addView(logView)
        root.addView(scroll)

        // Register log listener
        ScamDetectionService.addLogListener(logListener)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ScamDetectionService.removeLogListener(logListener)
    }

    private fun append(entry: LogEntry) {
        if (!hasContent) {
            logView.text = ""
            logView.gravity = Gravity.START
            hasContent = true
        }
        val line = android.text.SpannableString(entry.text + "\n")
        if (entry.risk > 70) {
            line.setSpan(
                android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor(DesignTheme.Colors.RISK_HIGH)),
                0, line.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        logView.append(line)
        (logView.parent as? ScrollView)?.post { (logView.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dpi(v: Number): Int = dp(v.toFloat()).toInt()
}