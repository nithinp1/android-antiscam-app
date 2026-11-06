package com.echoguard.companion

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SMS Threat Monitor Fragment - Displays all incoming SMS messages analyzed by the server
 * with multi-engine fraud detection (DLT check, rule engine, XGBoost, and LLM reasoning).
 */
class SmsAlertsFragment : Fragment() {

    private lateinit var rootLayout: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var components: DesignComponents
    private lateinit var totalTv: TextView
    private lateinit var threatsTv: TextView
    private lateinit var safeTv: TextView

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    private val smsListener: (LogEntry) -> Unit = { _ ->
        if (isAdded) {
            activity?.runOnUiThread {
                refreshList()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        components = DesignComponents(requireContext())

        rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(DesignTheme.Colors.BACKGROUND))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val scroll = ScrollView(requireContext()).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpi(DesignTheme.Spacing.MD),
                dpi(DesignTheme.Spacing.MD),
                dpi(DesignTheme.Spacing.MD),
                dpi(DesignTheme.Spacing.LG)
            )
        }

        // Header Title
        val headerTitle = components.createTitle(
            "SMS Threat Intelligence",
            DesignTheme.Colors.ON_BACKGROUND,
            true
        ).apply {
            textSize = 20f
        }
        content.addView(headerTitle)

        val headerSubtitle = components.createBody(
            "Real-time smishing, phishing link & sender spoofing analysis",
            "sm",
            DesignTheme.Colors.ON_SURFACE_VARIANT
        ).apply {
            setPadding(0, dpi(2), 0, dpi(DesignTheme.Spacing.MD))
        }
        content.addView(headerSubtitle)

        // Metrics Summary Banner
        val metricsCard = buildMetricsBanner()
        content.addView(metricsCard)
        content.addView(components.createSpacer(DesignTheme.Spacing.MD))

        // Action Row (Clear history + Sample Test)
        val actionsRow = buildActionsRow()
        content.addView(actionsRow)
        content.addView(components.createSpacer(DesignTheme.Spacing.MD))

        // List Container for SMS Cards
        listContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        content.addView(listContainer)

        scroll.addView(content)
        rootLayout.addView(scroll)

        // Initial populate
        refreshList()

        // Register listener for live SMS events
        ScamDetectionService.addSmsListener(smsListener)

        return rootLayout
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ScamDetectionService.removeSmsListener(smsListener)
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    /** Builds the top 3-metric statistics banner (Total, Threats, Safe). */
    private fun buildMetricsBanner(): View {
        return components.createCard(
            backgroundColor = DesignTheme.Colors.SURFACE_CONTAINER,
            paddingDp = DesignTheme.Spacing.MD,
            cornerRadiusDp = DesignTheme.Radius.MEDIUM
        ) { cardLayout ->
            cardLayout.orientation = LinearLayout.HORIZONTAL
            cardLayout.weightSum = 3f

            fun createStatColumn(title: String, initialValue: String, colorHex: String): Pair<LinearLayout, TextView> {
                val col = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val valTv = TextView(requireContext()).apply {
                    text = initialValue
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor(colorHex))
                    gravity = Gravity.CENTER
                }
                val lblTv = components.createLabel(title, "sm", DesignTheme.Colors.ON_SURFACE_VARIANT).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dpi(2), 0, 0)
                }
                col.addView(valTv)
                col.addView(lblTv)
                return Pair(col, valTv)
            }

            val totalCol = createStatColumn("Total SMS", "0", DesignTheme.Colors.ON_SURFACE)
            val threatsCol = createStatColumn("Threats", "0", DesignTheme.Colors.RISK_HIGH)
            val safeCol = createStatColumn("Safe", "0", DesignTheme.Colors.SECONDARY)

            totalTv = totalCol.second
            threatsTv = threatsCol.second
            safeTv = safeCol.second

            cardLayout.addView(totalCol.first)
            cardLayout.addView(threatsCol.first)
            cardLayout.addView(safeCol.first)
        }
    }

    /** Builds the action row with Clear and Test buttons. */
    private fun buildActionsRow(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val sectionTitle = components.createLabel(
                "RECENT MESSAGES",
                "md",
                DesignTheme.Colors.ON_SURFACE_VARIANT
            ).apply {
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(sectionTitle)

            // Simulate Test SMS button
            val testBtn = TextView(requireContext()).apply {
                text = "Simulate SMS"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(DesignTheme.Colors.SECONDARY))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(DesignTheme.Colors.SURFACE_CONTAINER_HIGH))
                    cornerRadius = dp(12f)
                    setStroke(dpi(1), Color.parseColor(DesignTheme.Colors.SECONDARY))
                }
                setPadding(dpi(10), dpi(6), dpi(10), dpi(6))
                setOnClickListener { showSimulateDialog() }
            }
            addView(testBtn)

            val spacer = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dpi(8), 1)
            }
            addView(spacer)

            // Clear button
            val clearBtn = TextView(requireContext()).apply {
                text = "Clear All"
                textSize = 12f
                setTextColor(Color.parseColor(DesignTheme.Colors.ERROR))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(DesignTheme.Colors.SURFACE_CONTAINER_HIGH))
                    cornerRadius = dp(12f)
                    setStroke(dpi(1), Color.parseColor(DesignTheme.Colors.OUTLINE_VARIANT))
                }
                setPadding(dpi(10), dpi(6), dpi(10), dpi(6))
                setOnClickListener { confirmClearLog() }
            }
            addView(clearBtn)
        }
    }

    /** Re-reads SMS log from persistence and renders each SMS message. */
    private fun refreshList() {
        val history = ScamDetectionService.loadSmsLog(requireContext())
        listContainer.removeAllViews()

        var totalCount = 0
        var threatCount = 0
        var safeCount = 0

        val items = mutableListOf<JSONObject>()
        for (i in 0 until history.length()) {
            val obj = history.getJSONObject(i)
            items.add(obj)
            val risk = obj.optInt("risk", -1)
            totalCount++
            if (risk >= 40) {
                threatCount++
            } else if (risk in 0..39) {
                safeCount++
            }
        }

        // Update top counter metrics
        if (::totalTv.isInitialized) {
            totalTv.text = totalCount.toString()
            threatsTv.text = threatCount.toString()
            safeTv.text = safeCount.toString()
        }

        if (items.isEmpty()) {
            showEmptyState()
            return
        }

        // Show newest first (reverse order)
        for (i in items.size - 1 downTo 0) {
            val item = items[i]
            val card = buildSmsCard(item)
            listContainer.addView(card)
            listContainer.addView(components.createSpacer(DesignTheme.Spacing.SM))
        }
    }

    /** Creates a detailed card for a single SMS message. */
    private fun buildSmsCard(obj: JSONObject): View {
        val sender = obj.optString("sender", "Unknown Sender")
        val senderType = obj.optString("sender_type", "unknown")
        val body = obj.optString("body", obj.optString("text", ""))
        val risk = obj.optInt("risk", -1)
        val reason = obj.optString("reason", obj.optString("text", ""))
        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())

        val (riskColorHex, riskText) = when {
            risk < 0 -> Pair(DesignTheme.Colors.OUTLINE, "PENDING")
            risk < 30 -> Pair(DesignTheme.Colors.RISK_LOW, "$risk SAFE")
            risk < 70 -> Pair(DesignTheme.Colors.TERTIARY, "$risk SUSPICIOUS")
            else -> Pair(DesignTheme.Colors.RISK_HIGH, "$risk SCAM")
        }

        val (senderLabel, senderColorHex) = when (senderType) {
            "dlt_registered" -> Pair("DLT VERIFIED", DesignTheme.Colors.SECONDARY)
            "plain_number" -> Pair("MOBILE NUMBER", DesignTheme.Colors.TERTIARY_FIXED_DIM)
            else -> Pair("UNKNOWN SENDER", DesignTheme.Colors.ERROR)
        }

        val timeStr = try {
            val date = Date(timestamp)
            val now = System.currentTimeMillis()
            if (now - timestamp < 86400000L) timeFormat.format(date) else dateFormat.format(date)
        } catch (_: Exception) { "" }

        val card = components.createCard(
            backgroundColor = DesignTheme.Colors.SURFACE_CONTAINER_HIGH,
            paddingDp = DesignTheme.Spacing.MD,
            cornerRadiusDp = DesignTheme.Radius.MEDIUM
        ) { cardLayout ->
            cardLayout.orientation = LinearLayout.VERTICAL

            // Top row: Sender + Badges + Risk Score
            val topRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            // Sender column
            val senderCol = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val senderTv = TextView(requireContext()).apply {
                text = sender
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(DesignTheme.Colors.ON_SURFACE))
            }
            senderCol.addView(senderTv)

            // Sender badge + Timestamp row
            val metaRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpi(2), 0, 0)
            }

            val badgeTv = TextView(requireContext()).apply {
                text = senderLabel
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(senderColorHex))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(DesignTheme.Colors.SURFACE_CONTAINER_LOW))
                    cornerRadius = dp(4f)
                    setStroke(dpi(1), Color.parseColor(senderColorHex))
                }
                setPadding(dpi(6), dpi(2), dpi(6), dpi(2))
            }
            metaRow.addView(badgeTv)

            if (timeStr.isNotEmpty()) {
                val timeTv = TextView(requireContext()).apply {
                    text = " • $timeStr"
                    textSize = 11f
                    setTextColor(Color.parseColor(DesignTheme.Colors.ON_SURFACE_VARIANT))
                }
                metaRow.addView(timeTv)
            }
            senderCol.addView(metaRow)
            topRow.addView(senderCol)

            // Risk Pill on Right
            val riskPill = TextView(requireContext()).apply {
                text = riskText
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(riskColorHex))
                    cornerRadius = dp(12f)
                }
                setPadding(dpi(10), dpi(4), dpi(10), dpi(4))
            }
            topRow.addView(riskPill)
            cardLayout.addView(topRow)

            // Message Body Bubble
            val bodyContainer = FrameLayout(requireContext()).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(DesignTheme.Colors.SURFACE_CONTAINER))
                    cornerRadius = dp(8f)
                    setStroke(dpi(1), Color.parseColor(DesignTheme.Colors.OUTLINE_VARIANT))
                }
                setPadding(dpi(10), dpi(8), dpi(10), dpi(8))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dpi(10), 0, 0)
                }
            }

            val bodyTv = TextView(requireContext()).apply {
                text = if (body.isNotBlank()) body else "(Empty SMS Body)"
                textSize = 13.5f
                setTextColor(Color.parseColor(DesignTheme.Colors.ON_SURFACE))
                setLineSpacing(dp(2f), 1f)
            }
            bodyContainer.addView(bodyTv)
            cardLayout.addView(bodyContainer)

            // Threat Analysis / Reason Box
            if (reason.isNotBlank()) {
                val reasonLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        val reasonBgColor = if (risk >= 70) DesignTheme.Colors.TERTIARY_CONTAINER else DesignTheme.Colors.SURFACE_CONTAINER_LOW
                        setColor(Color.parseColor(reasonBgColor))
                        cornerRadius = dp(6f)
                    }
                    setPadding(dpi(8), dpi(6), dpi(8), dpi(6))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, dpi(8), 0, 0)
                    }
                }

                val iconTv = TextView(requireContext()).apply {
                    text = when {
                        risk >= 70 -> "🚨 "
                        risk >= 30 -> "⚠️ "
                        risk >= 0 -> "🛡️ "
                        else -> "ℹ️ "
                    }
                    textSize = 13f
                }
                reasonLayout.addView(iconTv)

                val reasonTv = TextView(requireContext()).apply {
                    text = reason
                    textSize = 12f
                    typeface = if (risk >= 70) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    val textColorHex = if (risk >= 70) DesignTheme.Colors.TERTIARY else DesignTheme.Colors.ON_SURFACE_VARIANT
                    setTextColor(Color.parseColor(textColorHex))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                reasonLayout.addView(reasonTv)
                cardLayout.addView(reasonLayout)
            }
        }

        if (risk >= 70) {
            card.background = GradientDrawable().apply {
                setColor(Color.parseColor(DesignTheme.Colors.SURFACE_CONTAINER_HIGH))
                cornerRadius = dp(DesignTheme.Radius.MEDIUM)
                setStroke(dpi(2), Color.parseColor(DesignTheme.Colors.ERROR))
            }
        }

        return card
    }

    /** Empty state view when no SMS messages have been recorded yet. */
    private fun showEmptyState() {
        val emptyCard = components.createCard(
            backgroundColor = DesignTheme.Colors.SURFACE_CONTAINER,
            paddingDp = DesignTheme.Spacing.LG,
            cornerRadiusDp = DesignTheme.Radius.MEDIUM
        ) { cardLayout ->
            cardLayout.gravity = Gravity.CENTER
            cardLayout.orientation = LinearLayout.VERTICAL

            val iconTv = TextView(requireContext()).apply {
                text = "📩"
                textSize = 36f
                gravity = Gravity.CENTER
            }
            cardLayout.addView(iconTv)

            val emptyTitle = components.createTitle(
                "No SMS Analyzed Yet",
                DesignTheme.Colors.ON_SURFACE,
                true
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dpi(8), 0, dpi(4))
            }
            cardLayout.addView(emptyTitle)

            val emptyDesc = components.createBody(
                "Incoming text messages received on this device are automatically forwarded to the server and analyzed in real time for fraud, smishing links, and sender spoofing.",
                "sm",
                DesignTheme.Colors.ON_SURFACE_VARIANT
            ).apply {
                gravity = Gravity.CENTER
                setPadding(dpi(16), 0, dpi(16), dpi(12))
            }
            cardLayout.addView(emptyDesc)
        }
        listContainer.addView(emptyCard)
    }

    /** Confirmation dialog before clearing all stored SMS logs. */
    private fun confirmClearLog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear SMS History")
            .setMessage("Do you want to clear all analyzed SMS messages from device history?")
            .setPositiveButton("Clear") { _, _ ->
                ScamDetectionService.clearSmsLog(requireContext())
                refreshList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Interactive simulator dialog allowing the user to test both phishing and safe SMS analysis. */
    private fun showSimulateDialog() {
        val options = arrayOf(
            "Phishing: Bank KYC / Account Block (High Risk)",
            "Lottery / Prize Scam (High Risk)",
            "Legitimate Bank OTP (DLT Verified - Safe)",
            "Personal Message (Plain Number - Safe)"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Simulate Incoming SMS")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> ScamDetectionService.handleIncomingSms(
                        requireContext(),
                        sender = "AX-HDFCBK",
                        body = "Dear customer, your bank account has been suspended due to pending KYC. Click http://hdfc-verify-kyc.com/update immediately to unblock."
                    )
                    1 -> ScamDetectionService.handleIncomingSms(
                        requireContext(),
                        sender = "+919876543210",
                        body = "Congratulations! You won 25,00,000 in lucky lottery. Send 5000 processing fee to UPI id lottery@upi to claim now."
                    )
                    2 -> ScamDetectionService.handleIncomingSms(
                        requireContext(),
                        sender = "VM-HDFCBK",
                        body = "Your OTP for login to HDFC NetBanking is 591832. Valid for 10 minutes. Do not share your OTP with anyone, including bank staff."
                    )
                    3 -> ScamDetectionService.handleIncomingSms(
                        requireContext(),
                        sender = "+919845012345",
                        body = "Hey Praveen, are we still meeting for lunch at 1 PM today? Let me know!"
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dpi(v: Number): Int = dp(v.toFloat()).toInt()
}
