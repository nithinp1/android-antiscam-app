package com.example.scamdetection

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.Switch

import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.scamdetection.R

/** Dashboard fragment - contains the main monitoring UI. */
class DashboardFragment : Fragment() {

    private lateinit var gaugeTitleTv: TextView
    private lateinit var serverStatusTv: TextView
    private lateinit var permStatusTv: TextView
    private lateinit var systemSecureTv: TextView
    private lateinit var lastCallRiskTv: TextView
    private lateinit var lastCallScoreTv: TextView
    private lateinit var networkStatusTv: TextView
    private lateinit var voiceToggle: Switch
    private lateinit var forceMicToggle: Switch
    private lateinit var audioSourceStatusTv: TextView
    private lateinit var packageTimeValueTv: TextView
    private lateinit var pulseDot: View
    private lateinit var designComponents: DesignComponents
    private lateinit var innerCircle: LinearLayout

    private val prefsName = "app"

    private val riskListener: (LogEntry) -> Unit = { entry ->
        if (isAdded && activity != null) {
            activity?.runOnUiThread { updateRisk(entry.risk, entry.text) }
        }
    }
    private val logListener: (LogEntry) -> Unit = { entry ->
        if (isAdded && activity != null) {
            activity?.runOnUiThread { updateLog(entry.text) }
        }
    }
    private val audioSourceListener: (String) -> Unit = { sourceName ->
        if (isAdded && activity != null) {
            activity?.runOnUiThread {
                if (::audioSourceStatusTv.isInitialized) {
                    audioSourceStatusTv.text = "ACTIVE SOURCE: $sourceName"
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        designComponents = DesignComponents(requireContext())
        val view = buildDashboardUI()

        ScamDetectionService.addRiskListener(riskListener)
        ScamDetectionService.addLogListener(logListener)
        ScamDetectionService.addAudioSourceListener(audioSourceListener)
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ScamDetectionService.removeRiskListener(riskListener)
        ScamDetectionService.removeLogListener(logListener)
        ScamDetectionService.removeAudioSourceListener(audioSourceListener)
    }

    private fun buildDashboardUI(): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(DesignTheme.Colors.BACKGROUND))
        }

        val scroll = ScrollView(requireContext()).apply {
            isVerticalScrollBarEnabled = false
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dpi(DesignTheme.Spacing.LG), dpi(DesignTheme.Spacing.LG),
                       dpi(DesignTheme.Spacing.LG), dpi(DesignTheme.Spacing.LG))
        }
        scroll.addView(content)
        root.addView(scroll)

        // Toggle Card
        val toggleCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = designComponents.getGlassDrawable()
            setPadding(dpi(DesignTheme.Spacing.MD), dpi(DesignTheme.Spacing.SM),
                       dpi(DesignTheme.Spacing.MD), dpi(DesignTheme.Spacing.SM))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpi(DesignTheme.Spacing.MD)
            }
        }

        val liveBadge = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = designComponents.getGlassDrawable(
                fillColor = DesignTheme.Colors.SECONDARY_CONTAINER,
                borderColor = DesignTheme.Colors.SECONDARY
            )
            setPadding(dpi(10), dpi(4), dpi(10), dpi(4))
        }

        pulseDot = View(requireContext()).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(DesignTheme.Colors.SECONDARY))
            }
            layoutParams = LinearLayout.LayoutParams(dpi(8), dpi(8)).apply {
                rightMargin = dpi(6)
            }
            startPulseAnimation(this)
        }

        val liveTv = designComponents.createLabel("LIVE", "sm", DesignTheme.Colors.ON_SECONDARY_CONTAINER)
        liveBadge.addView(pulseDot)
        liveBadge.addView(liveTv)

        val voiceTv = designComponents.createTitle("Voice Monitoring").apply {
            setPadding(dpi(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        voiceToggle = Switch(requireContext()).apply {
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) startMonitoring() else stopMonitoring()
            }
        }

        toggleCard.addView(liveBadge)
        toggleCard.addView(voiceTv)
        toggleCard.addView(voiceToggle)
        content.addView(toggleCard)

        // Audio Source Selection & Live Badge Card
        val prefs = requireContext().getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
        val initialForceMic = prefs.getString("audio_source_mode", "HARDWARE_CALL") == "FORCE_MIC"

        val audioSourceCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = designComponents.getGlassDrawable()
            setPadding(dpi(DesignTheme.Spacing.MD), dpi(DesignTheme.Spacing.SM),
                       dpi(DesignTheme.Spacing.MD), dpi(DesignTheme.Spacing.SM))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpi(DesignTheme.Spacing.MD)
            }
        }

        val sourceHeader = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val micIcon = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.parseColor(DesignTheme.Colors.SECONDARY))
            layoutParams = LinearLayout.LayoutParams(dpi(24), dpi(24))
        }

        val sourceTitle = designComponents.createTitle("Audio Source Mode").apply {
            setPadding(dpi(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        forceMicToggle = Switch(requireContext()).apply {
            isChecked = initialForceMic
            setOnCheckedChangeListener { _, isChecked ->
                val newMode = if (isChecked) "FORCE_MIC" else "HARDWARE_CALL"
                prefs.edit().putString("audio_source_mode", newMode).apply()
                val modeLabel = if (isChecked) "Force Mic Mode" else "Hardware VOICE_CALL Mode"
                android.widget.Toast.makeText(requireContext(), "Audio Source: $modeLabel", android.widget.Toast.LENGTH_SHORT).show()
                ScamDetectionService.notifyLog(LogEntry("[SETTING] Audio Source Mode set to: $modeLabel"))
            }
        }

        sourceHeader.addView(micIcon)
        sourceHeader.addView(sourceTitle)
        sourceHeader.addView(forceMicToggle)

        val sourceSubtitle = designComponents.createBody(
            "Toggle ON to force Microphone input, or OFF for Hardware Call capture (VOICE_CALL).",
            "sm", DesignTheme.Colors.ON_SURFACE_VARIANT
        ).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(4)
                bottomMargin = dpi(6)
            }
        }

        audioSourceStatusTv = designComponents.createLabel(
            "ACTIVE SOURCE: ${ScamDetectionService.activeAudioSource}",
            "sm", DesignTheme.Colors.SECONDARY
        ).apply {
            typeface = Typeface.DEFAULT_BOLD
        }

        audioSourceCard.addView(sourceHeader)
        audioSourceCard.addView(sourceSubtitle)
        audioSourceCard.addView(audioSourceStatusTv)

        // Silence Filter Row
        val initialSilenceFilter = prefs.getBoolean("silence_filter_enabled", true)
        val silenceRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(10)
            }
        }

        val silenceTitle = designComponents.createTitle("In-App Silence Filter (RMS < 50)").apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val silenceToggle = Switch(requireContext()).apply {
            isChecked = initialSilenceFilter
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("silence_filter_enabled", isChecked).apply()
                val status = if (isChecked) "Enabled (RMS < 50 skipped)" else "Disabled (All audio sent)"
                android.widget.Toast.makeText(requireContext(), "Silence Filter: $status", android.widget.Toast.LENGTH_SHORT).show()
                ScamDetectionService.notifyLog(LogEntry("[SETTING] In-App Silence Filter: $status"))
            }
        }

        silenceRow.addView(silenceTitle)
        silenceRow.addView(silenceToggle)
        audioSourceCard.addView(silenceRow)
        content.addView(audioSourceCard)

        // Server IP Settings Card
        val serverCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = designComponents.getGlassDrawable()
            setPadding(dpi(DesignTheme.Spacing.MD), dpi(DesignTheme.Spacing.SM),
                       dpi(DesignTheme.Spacing.MD), dpi(DesignTheme.Spacing.SM))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpi(DesignTheme.Spacing.MD)
            }
            setOnClickListener { showServerUrlDialog() }
        }

        val serverIcon = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setColorFilter(Color.parseColor(DesignTheme.Colors.SECONDARY))
            layoutParams = LinearLayout.LayoutParams(dpi(24), dpi(24))
        }

        val serverTv = designComponents.createTitle("Server IP").apply {
            setPadding(dpi(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val serverArrow = designComponents.createLabel("Change", "sm", DesignTheme.Colors.ON_SURFACE_VARIANT)

        serverCard.addView(serverIcon)
        serverCard.addView(serverTv)
        serverCard.addView(serverArrow)
        content.addView(serverCard)

        // Package Time Settings Card
        val currentPackageTime = ScamDetectionService.getPackageTimeMs(requireContext())
        val packageCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = designComponents.getGlassDrawable()
            setPadding(dpi(DesignTheme.Spacing.MD), dpi(DesignTheme.Spacing.SM),
                       dpi(DesignTheme.Spacing.MD), dpi(DesignTheme.Spacing.SM))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpi(DesignTheme.Spacing.MD)
            }
            setOnClickListener { showPackageTimeDialog() }
        }

        val packageIcon = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_recent_history)
            setColorFilter(Color.parseColor(DesignTheme.Colors.SECONDARY))
            layoutParams = LinearLayout.LayoutParams(dpi(24), dpi(24))
        }

        val packageTextCol = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val packageTitle = designComponents.createTitle("Package Time")
        val packageSubtitle = designComponents.createBody(
            "Audio chunk interval for streaming (min 80ms)",
            "sm", DesignTheme.Colors.ON_SURFACE_VARIANT
        )
        packageTextCol.addView(packageTitle)
        packageTextCol.addView(packageSubtitle)

        packageTimeValueTv = designComponents.createLabel(
            formatPackageTimeLabel(currentPackageTime),
            "sm", DesignTheme.Colors.SECONDARY
        ).apply {
            typeface = Typeface.DEFAULT_BOLD
        }

        packageCard.addView(packageIcon)
        packageCard.addView(packageTextCol)
        packageCard.addView(packageTimeValueTv)
        content.addView(packageCard)

        // Gauge Container
        gaugeTitleTv = designComponents.createHeadline("IDLE", "lg-mobile", DesignTheme.Colors.ON_SURFACE_VARIANT)

        val gaugeContainer = FrameLayout(requireContext()).apply {
            val size = dpi(220)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                topMargin = dpi(DesignTheme.Spacing.LG)
                bottomMargin = dpi(DesignTheme.Spacing.LG)
            }
            setBackgroundColor(Color.parseColor(DesignTheme.Colors.SURFACE_CONTAINER_LOW))
        }

        innerCircle = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = designComponents.getGlassDrawable(fillColor = DesignTheme.Colors.SURFACE_CONTAINER_HIGH)
            val margin = dpi(16)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { setMargins(margin, margin, margin, margin) }
        }

        val gaugeIcon = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_shield_logo)
            layoutParams = LinearLayout.LayoutParams(dpi(36), dpi(36))
        }

        serverStatusTv = designComponents.createLabel("Server: Connecting...", "sm").apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(DesignTheme.Spacing.SM)
            }
        }

        permStatusTv = designComponents.createLabel("Permissions: Ready", "sm").apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(DesignTheme.Spacing.XS)
            }
        }

        systemSecureTv = designComponents.createStatusBadge("SYSTEM SECURE",
            backgroundColor = DesignTheme.Colors.SECONDARY_CONTAINER,
            textColor = DesignTheme.Colors.ON_SECONDARY_CONTAINER
        ).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(DesignTheme.Spacing.SM)
            }
        }

        innerCircle.addView(gaugeIcon)
        innerCircle.addView(gaugeTitleTv)
        innerCircle.addView(serverStatusTv)
        innerCircle.addView(permStatusTv)
        innerCircle.addView(systemSecureTv)
        gaugeContainer.addView(innerCircle)
        content.addView(gaugeContainer)

        // Last Call Card
        val lastCallCard = designComponents.createCard(
            backgroundColor = DesignTheme.Colors.SURFACE_CONTAINER_HIGH,
            paddingDp = DesignTheme.Spacing.MD
        ) {
            it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpi(DesignTheme.Spacing.MD)
            }
        }

        val cardHeader = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val analyticsIcon = ImageView(requireContext()).apply {
            setImageResource(android.R.drawable.ic_menu_info_details)
            setColorFilter(Color.parseColor(DesignTheme.Colors.SECONDARY))
            layoutParams = LinearLayout.LayoutParams(dpi(22), dpi(22))
        }

        val cardTitle = designComponents.createTitle("Last Call Analysis").apply {
            setPadding(dpi(DesignTheme.Spacing.SM), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        cardHeader.addView(analyticsIcon)
        cardHeader.addView(cardTitle)

        lastCallScoreTv = designComponents.createHeadline("Score: —", "lg", DesignTheme.Colors.SECONDARY).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(DesignTheme.Spacing.SM)
            }
        }

        lastCallRiskTv = designComponents.createBody("No recent scam threat detected", "md", DesignTheme.Colors.ON_SURFACE_VARIANT).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(DesignTheme.Spacing.XS)
            }
        }

        lastCallCard.addView(cardHeader)
        lastCallCard.addView(lastCallScoreTv)
        lastCallCard.addView(lastCallRiskTv)
        content.addView(lastCallCard)

        // Status Grid
        val grid = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpi(DesignTheme.Spacing.MD)
            }
            weightSum = 2f
        }

        val card1 = designComponents.createCard(width = 0, backgroundColor = DesignTheme.Colors.SURFACE_CONTAINER_HIGH, paddingDp = DesignTheme.Spacing.MD) {
            it.gravity = Gravity.CENTER
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dpi(DesignTheme.Spacing.SM) }
        }
        val label1 = designComponents.createLabel("LAST CALL", "sm")
        val val1 = designComponents.createBody("Just Now", "md", DesignTheme.Colors.ON_BACKGROUND).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(DesignTheme.Spacing.XS) }
        }
        card1.addView(label1)
        card1.addView(val1)

        val card2 = designComponents.createCard(width = 0, backgroundColor = DesignTheme.Colors.SURFACE_CONTAINER_HIGH, paddingDp = DesignTheme.Spacing.MD) {
            it.gravity = Gravity.CENTER
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dpi(DesignTheme.Spacing.SM) }
        }
        val label2 = designComponents.createLabel("NETWORK", "sm")
        networkStatusTv = designComponents.createBody("Server Online", "md", DesignTheme.Colors.SECONDARY).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dpi(DesignTheme.Spacing.XS) }
        }
        card2.addView(label2)
        card2.addView(networkStatusTv)

        grid.addView(card1)
        grid.addView(card2)
        content.addView(grid)

        // Quick Action Navigation Cards
        val navSection = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val cardsData = listOf(
            Triple("Call Technical Logs", "Full websocket & transcription trace", TechnicalLogActivity::class.java),
            Triple("SMS Alerts & Protection", "Smishing risk analysis history", SmsAlertsActivity::class.java),
            Triple("Evidence Vault Log", "Call audio recordings with SHA-256 integrity", EvidenceLogActivity::class.java)
        )

        cardsData.forEach { (title, desc, targetClass) ->
            val navCard = designComponents.createCard(backgroundColor = DesignTheme.Colors.SURFACE_CONTAINER_HIGH, paddingDp = DesignTheme.Spacing.MD) {
                it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpi(DesignTheme.Spacing.SM)
                }
                it.setOnClickListener {
                    startActivity(Intent(requireContext(), targetClass))
                }
            }
            val navTitle = designComponents.createTitle(title).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val navDesc = designComponents.createBody(desc, "md", DesignTheme.Colors.ON_SURFACE_VARIANT).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dpi(DesignTheme.Spacing.XS)
                }
            }
            navCard.addView(navTitle)
            navCard.addView(navDesc)
            navSection.addView(navCard)
        }

        content.addView(navSection)
        return root
    }

    private fun showServerUrlDialog() {
        val prefs = requireContext().getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
        val currentUrl = prefs.getString("server_url", "ws://100.x.x.x:8000/audio-stream") ?: "ws://100.x.x.x:8000/audio-stream"

        val input = EditText(requireContext()).apply {
            hint = "WebSocket URL (e.g., ws://100.x.x.x:8000/audio-stream)"
            setText(currentUrl)
            setSelection(currentUrl.length)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Server Configuration")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isNotEmpty()) {
                    prefs.edit().putString("server_url", newUrl).apply()
                    serverStatusTv.text = "Server: ${newUrl.take(24)}..."
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatPackageTimeLabel(ms: Int): String {
        return if (ms >= 1000 && ms % 1000 == 0) {
            "${ms / 1000}s (${ms}ms)"
        } else {
            "${ms} ms"
        }
    }

    private fun showPackageTimeDialog() {
        val currentMs = ScamDetectionService.getPackageTimeMs(requireContext())
        val dialogView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20), dpi(16), dpi(20), dpi(8))
        }

        val descTv = TextView(requireContext()).apply {
            text = "Set audio package (packet) duration streamed to Server v3. Lower values (80ms, 100ms) enable ultra-low latency real-time transcription."
            setTextColor(Color.parseColor(DesignTheme.Colors.ON_SURFACE_VARIANT))
            textSize = 13f
            setPadding(0, 0, 0, dpi(12))
        }
        dialogView.addView(descTv)

        val input = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Package duration in ms (min 80)"
            setText(currentMs.toString())
            setSelection(text.length)
        }
        dialogView.addView(input)

        val minNoteTv = TextView(requireContext()).apply {
            text = "Minimum: 80 ms"
            setTextColor(Color.parseColor(DesignTheme.Colors.ON_SURFACE_VARIANT))
            textSize = 12f
            setPadding(0, dpi(4), 0, dpi(12))
        }
        dialogView.addView(minNoteTv)

        val presetsLabel = TextView(requireContext()).apply {
            text = "Quick Presets:"
            setTextColor(Color.parseColor(DesignTheme.Colors.ON_BACKGROUND))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpi(8))
        }
        dialogView.addView(presetsLabel)

        val presetsScroll = android.widget.HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
        }
        val presetsRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val presets = listOf(80, 100, 200, 500, 1000, 3000)
        presets.forEach { ms ->
            val btn = android.widget.Button(requireContext()).apply {
                text = if (ms >= 1000) "${ms / 1000}s" else "${ms}ms"
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dpi(38)
                ).apply { rightMargin = dpi(8) }
                setOnClickListener {
                    input.setText(ms.toString())
                    input.setSelection(input.text.length)
                }
            }
            presetsRow.addView(btn)
        }
        presetsScroll.addView(presetsRow)
        dialogView.addView(presetsScroll)

        AlertDialog.Builder(requireContext())
            .setTitle("Set Package Time")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val str = input.text.toString().trim()
                val parsed = str.toIntOrNull()
                if (parsed == null || parsed < ScamDetectionService.MIN_PACKAGE_TIME_MS) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Package time must be at least ${ScamDetectionService.MIN_PACKAGE_TIME_MS}ms",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    ScamDetectionService.setPackageTimeMs(requireContext(), parsed)
                    packageTimeValueTv.text = formatPackageTimeLabel(parsed)
                    val label = "${parsed}ms"
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Package Time set to: $label",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    ScamDetectionService.notifyLog(LogEntry("[SETTING] Audio Package Time set to: $label"))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startMonitoring() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            voiceToggle.isChecked = false
            requestPermissions(missing.toTypedArray(), 42)
            return
        }
        val url = requireContext().getSharedPreferences(prefsName, android.content.Context.MODE_PRIVATE)
            .getString("server_url", "ws://100.x.x.x:8000/audio-stream") ?: "ws://100.x.x.x:8000/audio-stream"
        activity?.startForegroundService(Intent(requireContext(), ScamDetectionService::class.java)
            .putExtra(ScamDetectionService.EXTRA_URL, url))
        CallStateManager.register(requireContext())
        CallStateManager.noteManual()
        gaugeTitleTv.text = "ACTIVE"
        gaugeTitleTv.setTextColor(Color.parseColor(DesignTheme.Colors.SECONDARY))
        systemSecureTv.text = "SYSTEM SECURE"
        systemSecureTv.setTextColor(Color.parseColor(DesignTheme.Colors.SECONDARY))
        if (!voiceToggle.isChecked) voiceToggle.isChecked = true
    }

    private fun stopMonitoring() {
        activity?.stopService(Intent(requireContext(), ScamDetectionService::class.java))
        gaugeTitleTv.text = "IDLE"
        gaugeTitleTv.setTextColor(Color.parseColor(DesignTheme.Colors.ON_SURFACE_VARIANT))
        systemSecureTv.text = "STANDBY"
        systemSecureTv.setTextColor(Color.parseColor(DesignTheme.Colors.ON_SURFACE_VARIANT))
        lastCallScoreTv.text = "Score: —"
        lastCallRiskTv.text = "Monitoring stopped"
        if (voiceToggle.isChecked) voiceToggle.isChecked = false
    }

    private fun missingPermissions(): java.util.ArrayList<String> {
        val perms = java.util.ArrayList<String>().apply {
            add(android.Manifest.permission.RECORD_AUDIO)
            add(android.Manifest.permission.RECEIVE_SMS)
            add(android.Manifest.permission.READ_PHONE_STATE)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        return perms.filter { activity?.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED } as java.util.ArrayList<String>
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
            startMonitoring()
        }
    }

    private fun updateRisk(risk: Int, detail: String) {
        if (risk < 0) {
            gaugeTitleTv.text = "IDLE"
            gaugeTitleTv.setTextColor(Color.parseColor(DesignTheme.Colors.ON_SURFACE_VARIANT))
            lastCallScoreTv.text = "Score: —"
            lastCallRiskTv.text = "Monitoring idle"
            return
        }
        val color = DesignTheme.getRiskColor(risk)
        val label = DesignTheme.getRiskLabel(risk)
        gaugeTitleTv.text = label
        gaugeTitleTv.setTextColor(color)
        systemSecureTv.text = "RISK SCORE: $risk"
        systemSecureTv.setTextColor(color)
        lastCallScoreTv.text = "Score: $risk / 100"
        lastCallScoreTv.setTextColor(color)
        lastCallRiskTv.text = detail.take(120)
    }

    private fun updateLog(text: String) {
        when {
            text.contains("Connected", ignoreCase = true) -> {
                networkStatusTv.text = "Server Online"
                networkStatusTv.setTextColor(Color.parseColor(DesignTheme.Colors.SECONDARY))
            }
            text.contains("Error", ignoreCase = true) || text.contains("Closed", ignoreCase = true) -> {
                networkStatusTv.text = "Disconnected"
                networkStatusTv.setTextColor(Color.parseColor(DesignTheme.Colors.ERROR))
            }
        }
    }

    private fun startPulseAnimation(view: View) {
        val anim = AlphaAnimation(0.4f, 1.0f).apply {
            duration = 1000
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
        view.startAnimation(anim)
    }

    private fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dpi(v: Number): Int = dp(v.toFloat()).toInt()
}