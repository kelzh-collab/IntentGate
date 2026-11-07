package com.intentgate.app.data

import android.accessibilityservice.AccessibilityService
import android.text.InputFilter
import android.text.InputType
import android.view.ContextThemeWrapper
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import com.intentgate.app.R
import com.intentgate.app.loc

/** A full-screen confirmation form. It never launches or controls the target app. */
internal class DesktopIntentOverlay(private val service: AccessibilityService) {
    private var window: View? = null
    private val manager get() = service.getSystemService(WindowManager::class.java)

    fun visible(): Boolean = window?.let { it.isAttachedToWindow && it.windowVisibility == View.VISIBLE } == true

    fun show(app: AppDefinition, onCancel: () -> Unit, onStart: (String, Int) -> Boolean): Boolean {
        if (window != null) return false
        return try {
            val context = ContextThemeWrapper(service, android.R.style.Theme_Material_Light)
            val purpose = EditText(context).apply {
                hint = loc(R.string.ig_your_purpose)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                filters = arrayOf(InputFilter.LengthFilter(200))
                minLines = 2
            }
            val minutes = EditText(context).apply {
                hint = loc(R.string.ig_minutes_1_1440)
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(4))
                setSingleLine(true)
            }
            val screen = FullScreenConfirmation(context, onCancel).apply {
                header(app.name, app.packageName)
                label(loc(R.string.ig_what_are_you_here_to_do), prominent = true)
                input(purpose)
                label(loc(R.string.ig_how_much_time_do_you_need))
                input(minutes)
                action(loc(R.string.ig_start_session), primary = true) {
                    val answer = purpose.text.toString().trim()
                    val duration = minutes.text.toString().toIntOrNull()
                    when {
                        answer.isEmpty() -> purpose.error = loc(R.string.ig_choose_a_purpose_before_you_begin)
                        duration == null || duration !in 1..1440 ->
                            minutes.error = loc(R.string.ig_choose_1_1440_minutes)
                        !onStart(answer, duration) -> purpose.error =
                            loc(R.string.ig_unable_to_start_check_usage_access_and_app_management_then_try)
                    }
                }
                action(loc(R.string.ig_cancel), primary = false, onClick = onCancel)
            }
            val params = fullScreenConfirmationParams(
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, "IntentGate Intent Check")
            screen.requestFocus()
            window = screen
            manager.addView(screen, params)
            true
        } catch (_: RuntimeException) {
            dismiss()
            false
        }
    }

    fun dismiss() {
        val card = window ?: return
        window = null
        try { manager.removeViewImmediate(card) } catch (_: RuntimeException) {}
    }
}
