package com.example.scamdetection

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.util.TypedValue
import android.view.Gravity
import android.view.Gravity as AndroidGravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView

/**
 * Reusable UI Components for EchoGuard Design System
 * All components follow Material Design 3 guidelines with Obsidian Shield theme
 */
class DesignComponents(val context: Context) {
    
    private fun dpi(dp: Float): Float = (dp * context.resources.displayMetrics.density)
private fun dp(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
    
    // ========== HELPER: Convert hex to Int Color ==========
    private fun toColor(hex: String): Int = Color.parseColor(hex)
    
    // ========== GLASS EFFECT BACKGROUND ==========
    fun getGlassDrawable(
        fillColor: String = DesignTheme.Colors.SURFACE_CONTAINER,
        borderColor: String = DesignTheme.Colors.OUTLINE_VARIANT,
        strokeWidth: Int = 1
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(toColor(fillColor))
            setStroke(strokeWidth, toColor(borderColor))
            cornerRadius = dpi(DesignTheme.Radius.MEDIUM)
        }
    }
    
    // ========== CARD CONTAINER ==========
    fun createCard(
        width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        backgroundColor: String = DesignTheme.Colors.SURFACE_CONTAINER_HIGH,
        paddingDp: Float = DesignTheme.Spacing.MD,
        cornerRadiusDp: Float = DesignTheme.Radius.MEDIUM,
        action: (LinearLayout) -> Unit = {}
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(width, height).apply {
                setMargins(
                    dp(DesignTheme.Spacing.SM),
                    dp(DesignTheme.Spacing.SM),
                    dp(DesignTheme.Spacing.SM),
                    dp(DesignTheme.Spacing.SM)
                )
            }
            setPadding(
                dp(paddingDp),
                dp(paddingDp),
                dp(paddingDp),
                dp(paddingDp)
            )
            background = GradientDrawable().apply {
                setColor(toColor(backgroundColor))
                cornerRadius = dpi(cornerRadiusDp)
            }
            action(this)
        }
    }
    
    // ========== TEXT: HEADLINE ==========
    fun createHeadline(
        text: String,
        sizeLevel: String = "lg",
        colorHex: String = DesignTheme.Colors.ON_BACKGROUND,
        bold: Boolean = true
    ): TextView {
        val textView = TextView(context).apply {
            this.text = text
            setTextColor(toColor(colorHex))
            typeface = Typeface.create(DesignTheme.Typography.FONT_HEADLINE, 
                if (bold) Typeface.BOLD else Typeface.NORMAL)
            
            val (size, weight) = when (sizeLevel) {
                "xl" -> Pair(DesignTheme.Typography.HeadlineXL.SIZE, 
                            DesignTheme.Typography.HeadlineXL.WEIGHT)
                "lg-mobile" -> Pair(DesignTheme.Typography.HeadlineLgMobile.SIZE,
                                  DesignTheme.Typography.HeadlineLgMobile.WEIGHT)
                else -> Pair(DesignTheme.Typography.HeadlineLg.SIZE,
                           DesignTheme.Typography.HeadlineLg.WEIGHT)
            }
            textSize = size
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return textView
    }
    
    // ========== TEXT: TITLE ==========
    fun createTitle(
        text: String,
        colorHex: String = DesignTheme.Colors.ON_BACKGROUND,
        bold: Boolean = true
    ): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(toColor(colorHex))
            textSize = DesignTheme.Typography.TitleMd.SIZE
            typeface = Typeface.create(DesignTheme.Typography.FONT_BODY,
                if (bold) Typeface.BOLD else Typeface.NORMAL)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
    
    // ========== TEXT: BODY ==========
    fun createBody(
        text: String,
        sizeLevel: String = "md",
        colorHex: String = DesignTheme.Colors.ON_SURFACE_VARIANT,
        maxLines: Int = Int.MAX_VALUE
    ): TextView {
        val size = if (sizeLevel == "lg") DesignTheme.Typography.BodyLg.SIZE 
                   else DesignTheme.Typography.BodyMd.SIZE
        
        return TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(toColor(colorHex))
            typeface = Typeface.create(DesignTheme.Typography.FONT_BODY, Typeface.NORMAL)
            this.maxLines = maxLines
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
    
    // ========== TEXT: LABEL ==========
    fun createLabel(
        text: String,
        sizeLevel: String = "md",
        colorHex: String = DesignTheme.Colors.ON_SURFACE_VARIANT
    ): TextView {
        val size = if (sizeLevel == "sm") DesignTheme.Typography.LabelSm.SIZE
                   else DesignTheme.Typography.LabelMd.SIZE
        
        return TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(toColor(colorHex))
            typeface = Typeface.create(DesignTheme.Typography.FONT_LABEL, Typeface.BOLD)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
    
    // ========== RISK SCORE INDICATOR (Large Gauge) ==========
    fun createRiskGauge(
        riskScore: Int,
        maxWidth: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        height: Int = dp(180f)
    ): FrameLayout {
        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(maxWidth, height)
            setBackgroundColor(toColor(DesignTheme.Colors.SURFACE_CONTAINER_LOW))
        }
        
        // Background ring (outer circle)
        val ringBg = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(height, height).apply {
                gravity = Gravity.CENTER
            }
            setBackgroundColor(toColor(DesignTheme.Colors.SURFACE_CONTAINER))
        }
        
        // Central gauge with risk color
        val riskColor = DesignTheme.getRiskColor(riskScore)
        val gaugeCenter = TextView(context).apply {
            text = riskScore.toString()
            textSize = 64f
            setTextColor(riskColor)
            typeface = Typeface.create(DesignTheme.Typography.FONT_HEADLINE, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(height, height).apply {
                gravity = Gravity.CENTER
            }
            setBackgroundColor(toColor(DesignTheme.Colors.SURFACE_CONTAINER_LOW))
        }
        
        // Risk label below
        val riskLabel = createLabel(
            DesignTheme.getRiskLabel(riskScore),
            "md",
            DesignTheme.Colors.ON_BACKGROUND
        ).apply {
            setTextColor(riskColor)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                bottomMargin = dp(16f)
            }
        }
        
        container.addView(ringBg)
        container.addView(gaugeCenter)
        container.addView(riskLabel)
        return container
    }
    
    // ========== STATUS BADGE (Small pill-shaped) ==========
    fun createStatusBadge(
        status: String,
        backgroundColor: String = DesignTheme.Colors.SURFACE_CONTAINER_HIGH,
        textColor: String = DesignTheme.Colors.ON_BACKGROUND
    ): TextView {
        return TextView(context).apply {
            text = status
            textSize = DesignTheme.Typography.LabelSm.SIZE
            setTextColor(toColor(textColor))
            typeface = Typeface.create(DesignTheme.Typography.FONT_LABEL, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                setColor(toColor(backgroundColor))
                cornerRadius = dpi(16f)
            }
        }
    }
    
    // ========== BUTTON (Filled & Outlined) ==========
    fun createButton(
        text: String,
        width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
        height: Int = dp(48f),
        backgroundColor: String = DesignTheme.Colors.SECONDARY,
        textColor: String = DesignTheme.Colors.ON_SECONDARY,
        outlined: Boolean = false,
        onClick: (() -> Unit)? = null
    ): TextView {
        val button = TextView(context).apply {
            this.text = text
            textSize = DesignTheme.Typography.LabelMd.SIZE
            setTextColor(toColor(textColor))
            typeface = Typeface.create(DesignTheme.Typography.FONT_LABEL, Typeface.BOLD)
            gravity = Gravity.CENTER
            
            layoutParams = ViewGroup.LayoutParams(width, height)
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))

            val bgDrawable = if (outlined) {
                GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(2f), toColor(backgroundColor))
                    cornerRadius = dpi(DesignTheme.Radius.MEDIUM)
                }
            } else {
                GradientDrawable().apply {
                    setColor(toColor(backgroundColor))
                    cornerRadius = dpi(DesignTheme.Radius.MEDIUM)
                }
            }
            background = bgDrawable
            
            if (onClick != null) {
                setOnClickListener { onClick() }
            }
        }
        return button
    }
    
    // ========== STAT ROW (Label + Value) ==========
    fun createStatRow(
        label: String,
        value: String,
        accentColor: String = DesignTheme.Colors.SECONDARY
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(8f), 0, dp(8f))
            gravity = Gravity.CENTER_VERTICAL

            val labelTv = createLabel(label, "sm")
            labelTv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

            val valueTv = createLabel(value, "md", accentColor)
            valueTv.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            addView(labelTv)
            addView(valueTv)
        }
    }
    
    // ========== DIVIDER ==========
    fun createDivider(
        color: String = DesignTheme.Colors.OUTLINE_VARIANT,
        heightDp: Float = 1f,
        marginTopDp: Float = DesignTheme.Spacing.MD,
        marginBottomDp: Float = DesignTheme.Spacing.MD
    ): View {
        return View(context).apply {
            setBackgroundColor(toColor(color))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(heightDp)
            ).apply {
                setMargins(0, dp(marginTopDp), 0, dp(marginBottomDp))
            }
        }
    }
    
    // ========== HORIZONTAL SEPARATOR SPACER ==========
    fun createSpacer(heightDp: Float = DesignTheme.Spacing.MD): View {
        return View(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(heightDp)
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
    }
    
    // ========== NAVIGATION ITEM (Bottom Nav) ==========
    fun createNavItem(
        icon: Int,
        label: String,
        selected: Boolean = false,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val item = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
            
            if (onClick != null) {
                setOnClickListener { onClick() }
            }
            
            val iconView = ImageView(context).apply {
                setImageResource(icon)
                val iconColor = if (selected) toColor(DesignTheme.Colors.SECONDARY)
                               else toColor(DesignTheme.Colors.ON_SURFACE_VARIANT)
                setColorFilter(iconColor)
                layoutParams = LinearLayout.LayoutParams(dp(24f), dp(24f))
            }
            
            val labelView = createLabel(label, "sm").apply {
                setTextColor(
                    if (selected) toColor(DesignTheme.Colors.SECONDARY)
                    else toColor(DesignTheme.Colors.ON_SURFACE_VARIANT)
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(4f)
                }
            }
            
            addView(iconView)
            addView(labelView)
        }
        return item
    }
}
