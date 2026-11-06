package com.echoguard.companion

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

/**
 * First-run permission walkthrough — explains each permission before asking.
 * Steps through: RECORD_AUDIO, RECEIVE_SMS, READ_PHONE_STATE, POST_NOTIFICATIONS, SYSTEM_ALERT_WINDOW.
 * Skips permissions already granted.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private lateinit var components: DesignComponents
    private var step = 0

    private val steps = listOf(
        OnboardStep(
            "Microphone Access",
            "We record the call audio to analyze it for scam patterns in real time. Nothing is stored unless risk is high.",
            Manifest.permission.RECORD_AUDIO
        ),
        OnboardStep(
            "SMS Access",
            "We read incoming SMS messages to check them for smishing (phishing via text). You can also test via the simulator.",
            Manifest.permission.RECEIVE_SMS
        ),
        OnboardStep(
            "Phone State",
            "We detect when a call starts/ends to auto-start monitoring. No call logs or contacts are read.",
            Manifest.permission.READ_PHONE_STATE
        ),
        OnboardStep(
            "Notifications",
            "A persistent notification keeps the monitoring service alive in the background (Android requirement).",
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
        ),
        OnboardStep(
            "Overlay Permission",
            "Shows a small risk badge over the call screen. On this rooted device we'll grant it via root; otherwise you'll allow it in Settings.",
            null // handled specially
        )
    )

    data class OnboardStep(
        val title: String,
        val desc: String,
        val permission: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already onboarded, skip straight to MainActivity
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("onboarded", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        components = DesignComponents(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor(DesignTheme.Colors.BACKGROUND))
        }

        val scroll = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpi(DesignTheme.Spacing.LG),
                dpi(DesignTheme.Spacing.LG),
                dpi(DesignTheme.Spacing.LG),
                dpi(DesignTheme.Spacing.LG)
            )
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(container)
        root.addView(scroll)
        setContentView(root)

        showStep()
    }

    private fun showStep() {
        container.removeAllViews()
        if (step >= steps.size) {
            finishOnboarding()
            return
        }
        val s = steps[step]

        val title = components.createHeadline(
            "${step + 1}/${steps.size}  ${s.title}",
            sizeLevel = "lg",
            colorHex = DesignTheme.Colors.ON_BACKGROUND
        ).apply {
            gravity = Gravity.CENTER
            val lp = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.bottomMargin = dpi(DesignTheme.Spacing.MD)
            }
            layoutParams = lp
        }
        container.addView(title)

        val desc = components.createBody(
            s.desc,
            "md",
            DesignTheme.Colors.ON_SURFACE_VARIANT
        ).apply {
            gravity = Gravity.CENTER
            val lp = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            if (lp is ViewGroup.MarginLayoutParams) {
                lp.bottomMargin = dpi(DesignTheme.Spacing.XL)
            }
            layoutParams = lp
        }
        container.addView(desc)

        val btnText = if (s.permission == null && step == steps.size - 1) "Finish" else "Allow & Continue"
        val btn = components.createButton(
            btnText,
            backgroundColor = DesignTheme.Colors.SECONDARY,
            textColor = DesignTheme.Colors.ON_SECONDARY
        ) { requestCurrent() }
        btn.setPadding(dpi(32f), dpi(12f), dpi(32f), dpi(12f))
        container.addView(btn)
    }

    private fun requestCurrent() {
        val s = steps[step]
        if (s.permission != null) {
            if (checkSelfPermission(s.permission) == PackageManager.PERMISSION_GRANTED) {
                nextStep()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(s.permission), 100 + step)
            }
        } else if (step == steps.size - 1) {
            // overlay step — check if already granted, else try root, else settings
            if (android.provider.Settings.canDrawOverlays(this)) {
                nextStep()
            } else if (RootHelper.hasRoot() && RootHelper.grantOverlayPermRoot(this)) {
                nextStep()
            } else {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName"))
                startActivityForResult(intent, 999)
            }
        } else {
            nextStep()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode >= 100 && requestCode < 100 + steps.size) {
            // permission was granted or denied — either way move on (user can re-enable in settings)
            nextStep()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 999) {
            nextStep()
        }
    }

    private fun nextStep() {
        step++
        showStep()
    }

    private fun finishOnboarding() {
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        prefs.edit().putBoolean("onboarded", true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dpi(v: Number): Int = dp(v.toFloat()).toInt()
}
