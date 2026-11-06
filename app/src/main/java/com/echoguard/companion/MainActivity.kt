package com.echoguard.companion

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

/**
 * Echoguard - Main Host Activity
 *
 * Hosts a ViewPager2 with 4 swipeable tabs (Dashboard, History, SMS, Vault)
 * and a shared bottom navigation bar that stays in sync with the pager.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: LinearLayout
    private lateinit var designComponents: DesignComponents
    private val navItems = mutableListOf<NavItemViews>()

    // Shared Preferences
    private val prefsName = "app"
    private val onboardingDoneKey = "onboarded"

    private data class NavItemViews(
        val root: LinearLayout,
        val icon: ImageView,
        val label: android.widget.TextView
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check onboarding
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        if (!prefs.getBoolean(onboardingDoneKey, false)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // Load saved theme preference
        DesignTheme.isDarkMode = prefs.getBoolean("is_dark_mode", true)

        designComponents = DesignComponents(this)
        setContentView(buildHostUI())
    }

    private fun buildHostUI(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor(DesignTheme.Colors.BACKGROUND))
        }

        // ── Header bar with theme toggle icon ──
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = designComponents.getGlassDrawable()
            setPadding(dpi(DesignTheme.Spacing.LG), dpi(DesignTheme.Spacing.SM),
                       dpi(DesignTheme.Spacing.SM), dpi(DesignTheme.Spacing.SM))
        }

        val titleTv = TextView(this).apply {
            text = "Echoguard (Low Latency)"
            setTextColor(Color.parseColor(DesignTheme.Colors.ON_BACKGROUND))
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(titleTv)

        // Sun (☀) in dark mode → taps to switch to light; Moon (☾) in light mode → taps to switch to dark
        val themeIcon = TextView(this).apply {
            text = if (DesignTheme.isDarkMode) "☀" else "☾"
            textSize = 22f
            setPadding(dpi(12), 0, 0, 0)
            setOnClickListener {
                DesignTheme.isDarkMode = !DesignTheme.isDarkMode
                saveThemePreference(DesignTheme.isDarkMode)
                recreate()
            }
        }
        header.addView(themeIcon)

        root.addView(header)

        viewPager = ViewPager2(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
            offscreenPageLimit = 3
        }
        viewPager.adapter = ViewPagerAdapter(this)
        root.addView(viewPager)

        bottomNav = buildBottomNav()
        root.addView(bottomNav)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateNavSelection(position)
            }
        })

        return root
    }

    private fun buildBottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = designComponents.getGlassDrawable()
            setPadding(dpi(DesignTheme.Spacing.MD), dpi(DesignTheme.Spacing.SM),
                dpi(DesignTheme.Spacing.MD), dpi(DesignTheme.Spacing.SM))
        }

        val items = listOf(
            Pair("Dashboard", android.R.drawable.ic_menu_compass),
            Pair("History", android.R.drawable.ic_menu_recent_history),
            Pair("SMS", android.R.drawable.ic_menu_send),
            Pair("Vault", android.R.drawable.ic_menu_save)
        )

        items.forEachIndexed { idx, (label, icon) ->
            val iconView = ImageView(this).apply {
                setImageResource(icon)
                layoutParams = LinearLayout.LayoutParams(dpi(24), dpi(24))
            }
            val labelView = designComponents.createLabel(label, "sm")
            val itemRoot = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(dpi(8), dpi(8), dpi(8), dpi(8))
                setOnClickListener { viewPager.currentItem = idx }
            }
            itemRoot.addView(iconView)
            itemRoot.addView(labelView)
            nav.addView(itemRoot)
            navItems.add(NavItemViews(itemRoot, iconView, labelView))
        }

        updateNavSelection(0)
        return nav
    }

    private fun updateNavSelection(position: Int) {
        navItems.forEachIndexed { idx, item ->
            val selected = idx == position
            val color = if (selected) DesignTheme.Colors.SECONDARY
                        else DesignTheme.Colors.ON_SURFACE_VARIANT
            item.icon.setColorFilter(Color.parseColor(color))
            item.label.setTextColor(Color.parseColor(color))
        }
    }

    private fun saveThemePreference(isDarkMode: Boolean) {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        prefs.edit().putBoolean("is_dark_mode", isDarkMode).apply()
    }

    private fun dp(v: Float): Float =
        (v * resources.displayMetrics.density)
    private fun dpi(v: Number): Int = dp(v.toFloat()).toInt()
}
