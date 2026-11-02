package com.example.scamdetection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

/** Lists evidence recordings with metadata; play, share (file + hash). */
class EvidenceLogActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private val components by lazy { DesignComponents(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = buildEvidenceUI()
        setContentView(root)
        supportActionBar?.title = "Evidence Log"
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    /** Builds the evidence UI for the activity or fragment. */
    fun buildEvidenceUIForFragment(): View {
        return buildEvidenceUI()
    }

    private fun buildEvidenceUI(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor(DesignTheme.Colors.BACKGROUND))
        }

        val scroll = android.widget.ScrollView(this).apply {
            setPadding(
                dpi(DesignTheme.Spacing.MD),
                dpi(DesignTheme.Spacing.MD),
                dpi(DesignTheme.Spacing.MD),
                dpi(DesignTheme.Spacing.MD)
            )
        }

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        scroll.addView(container)
        root.addView(scroll)
        return root
    }

    private fun reload() {
        container.removeAllViews()
        val entries = EvidenceRecorder.loadLog(this)
        if (entries.length() == 0) {
            addEmpty("No recordings yet. Evidence is captured when risk ≥ 60 during a call.")
            return
        }
        // show newest first
        for (i in entries.length() - 1 downTo 0) {
            val o = entries.getJSONObject(i)
            addEntry(o)
        }
    }

    private fun addEntry(o: JSONObject) {
        val file = java.io.File(o.optString("path"))
        val name = o.optString("file")
        val ts = o.optLong("timestamp")
        val num = o.optString("phone_number")
        val risk = o.optInt("max_risk")
        val hash = o.optString("sha256")
        val date = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", ts)
        val riskColor = DesignTheme.getRiskColor(risk)
        val riskLabel = DesignTheme.getRiskLabel(risk)

        val card = components.createCard(
            backgroundColor = DesignTheme.Colors.SURFACE_CONTAINER_HIGH,
            paddingDp = DesignTheme.Spacing.MD,
            cornerRadiusDp = DesignTheme.Radius.MEDIUM
        ) { cardLayout: LinearLayout ->
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val riskTv = components.createLabel(
                "$riskLabel: $risk",
                "md",
                DesignTheme.Colors.ON_SURFACE_VARIANT
            ).apply {
                setTextColor(riskColor)
            }
            val timeTv = components.createBody(
                date.toString(),
                "sm",
                DesignTheme.Colors.ON_SURFACE_VARIANT
            ).apply {
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            top.addView(riskTv)
            top.addView(timeTv)
            cardLayout.addView(top)

            val numTv = components.createBody(
                "Number: $num",
                "md",
                DesignTheme.Colors.ON_SURFACE_VARIANT
            )
            cardLayout.addView(numTv)

            val hashTv = components.createBody(
                "SHA-256: ${hash.take(16)}…",
                "sm",
                DesignTheme.Colors.ON_SURFACE_VARIANT
            ).apply {
                setTextIsSelectable(true)
            }
            cardLayout.addView(hashTv)
            // Fix: hashTv is already mutable from apply, need to cast or use var

            // spacer
            cardLayout.addView(components.createSpacer(DesignTheme.Spacing.MD))

            val btns = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val playBtn = components.createButton(
                "▶ Play",
                outlined = true,
                backgroundColor = DesignTheme.Colors.SECONDARY,
                textColor = DesignTheme.Colors.SECONDARY,
                height = dpi(36f)
            ) { play(file) }
            playBtn.setPadding(dpi(16f), dpi(8f), dpi(16f), dpi(8f))
            val shareBtn = components.createButton(
                "⤳ Share",
                outlined = true,
                backgroundColor = DesignTheme.Colors.SECONDARY,
                textColor = DesignTheme.Colors.SECONDARY,
                height = dpi(36f)
            ) { share(file, o.toString(2)) }
            shareBtn.setPadding(dpi(16f), dpi(8f), dpi(16f), dpi(8f))
            btns.addView(playBtn)
            btns.addView(shareBtn)
            cardLayout.addView(btns)
        }

        container.addView(card)
    }

    private fun addEmpty(msg: String) {
        val emptyView = components.createBody(
            msg,
            "md",
            DesignTheme.Colors.ON_SURFACE_VARIANT
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, dpi(DesignTheme.Spacing.XL * 2), 0, 0)
        }
        container.addView(emptyView)
    }

    private fun play(file: java.io.File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/wav")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivity(intent) } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(this, "No audio player found", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun share(file: java.io.File, metaJson: String) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Evidence metadata:\n$metaJson")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share evidence"))
    }

    private fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun dpi(v: Number): Int = dp(v.toFloat()).toInt()
}