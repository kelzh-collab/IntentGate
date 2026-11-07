package com.intentgate.app.data

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.graphics.toArgb
import com.intentgate.app.ui.GateColors

/** Shared opaque, touch-modal confirmation surface. System bars remain controlled by Android. */
internal class FullScreenConfirmation(context: Context, private val onBack: () -> Unit) : LinearLayout(context) {
    private val ink = GateColors.Ink.toArgb()
    private val secondary = GateColors.Secondary.toArgb()
    private var backDispatcher: OnBackInvokedDispatcher? = null
    private val backCallback = if (Build.VERSION.SDK_INT >= 33) OnBackInvokedCallback { onBack() } else null
    val card = object : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            val width = if (mode == MeasureSpec.UNSPECIFIED) dp(480)
                else minOf(MeasureSpec.getSize(widthMeasureSpec), dp(480))
            super.onMeasure(MeasureSpec.makeMeasureSpec(width,
                if (mode == MeasureSpec.UNSPECIFIED) MeasureSpec.AT_MOST else mode), heightMeasureSpec)
        }
    }.apply {
        orientation = VERTICAL
        setPadding(dp(24), dp(24), dp(24), dp(24))
        background = rounded(Color.WHITE, 28)
        elevation = dp(2).toFloat()
    }

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusableInTouchMode = true
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            GateColors.Background.map { it.toArgb() }.toIntArray())
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(24), dp(20), dp(24))
            addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            addView(container, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
    }

    fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    fun header(name: String, packageName: String) {
        card.addView(TextView(context).apply {
            text = "IntentGate"
            textSize = 14f
            setTextColor(secondary)
            gravity = Gravity.CENTER
        })
        card.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = name
            val icon = try { context.packageManager.getApplicationIcon(packageName) }
                catch (_: RuntimeException) { null }
                catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
            setImageDrawable(icon ?: context.getDrawable(android.R.drawable.sym_def_app_icon))
        }, LayoutParams(dp(56), dp(56)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(20)
        })
        card.addView(TextView(context).apply {
            text = name
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink)
            gravity = Gravity.CENTER
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
    }

    fun label(value: String, prominent: Boolean = false) {
        card.addView(TextView(context).apply {
            text = value
            textSize = if (prominent) 23f else 16f
            setTextColor(ink)
            if (prominent) setTypeface(typeface, Typeface.BOLD)
            gravity = if (prominent) Gravity.CENTER else Gravity.START
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(24) })
    }

    fun input(view: TextView) {
        view.textSize = 16f
        view.setTextColor(ink)
        view.setHintTextColor(secondary)
        view.setPadding(dp(14), dp(14), dp(14), dp(14))
        view.background = rounded(GateColors.Background.first().toArgb(), 14)
        card.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })
    }

    fun action(value: String, primary: Boolean, onClick: () -> Unit) {
        card.addView(Button(context).apply {
            text = value
            textSize = 16f
            isAllCaps = false
            maxLines = 3
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(52)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setTextColor(ink)
            backgroundTintList = ColorStateList.valueOf(
                if (primary) GateColors.Mint.toArgb() else GateColors.Background.last().toArgb())
            background = rounded(if (primary) GateColors.Mint.toArgb()
                else GateColors.Background.last().toArgb(), 18)
            setOnClickListener { onClick() }
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(if (primary) 24 else 10)
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                backDispatcher = findOnBackInvokedDispatcher()
                backCallback?.let { backDispatcher?.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, it) }
            } catch (_: RuntimeException) { backDispatcher = null }
        }
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 33) {
            try { backCallback?.let { backDispatcher?.unregisterOnBackInvokedCallback(it) } }
                catch (_: RuntimeException) {}
            backDispatcher = null
        }
        try { context.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(windowToken, 0) } catch (_: RuntimeException) {}
        super.onDetachedFromWindow()
    }
}

internal fun fullScreenConfirmationParams(type: Int, title: String) = WindowManager.LayoutParams(
    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
    type, 0, PixelFormat.OPAQUE
).apply {
    gravity = Gravity.TOP or Gravity.START
    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
    this.title = title
}
