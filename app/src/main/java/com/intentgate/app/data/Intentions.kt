package com.intentgate.app.data
import com.intentgate.app.R
import com.intentgate.app.loc
import com.intentgate.app.UiStrings




data class IntentionRecord(val id: String, val purpose: String, val appName: String, val usedSeconds: Int, val iconRes: Int, val isDemo: Boolean = false, val packageName: String? = null, val startedAt: Long? = null) {
    val durationLabel: String get()=if(usedSeconds%60==0) loc(R.string.ig_min,usedSeconds/60)
        else loc(R.string.ig_minutes_seconds,usedSeconds/60,usedSeconds%60)
}

/** Replace with a local repository when target-app tracking is connected. */
object MockIntentions {
    val records get()=listOf(
        IntentionRecord("drawing", loc(R.string.ig_learn_a_drawing_technique), "YouTube", 720, R.drawable.ic_app_icon_placeholder),
        IntentionRecord("friends", loc(R.string.ig_reply_to_friends), "Instagram", 360, R.drawable.ic_app_icon_placeholder),
        IntentionRecord("recipe", loc(R.string.ig_find_a_dinner_recipe), "Chrome", 240, R.drawable.ic_app_icon_placeholder),
        IntentionRecord("lesson", loc(R.string.ig_watch_a_drawing_lesson), "YouTube", 1080, R.drawable.ic_app_icon_placeholder),
        IntentionRecord("messages", loc(R.string.ig_reply_to_messages), "Instagram", 960, R.drawable.ic_app_icon_placeholder),
        IntentionRecord("research", loc(R.string.ig_research_a_new_sketchbook), "Chrome", 600, R.drawable.ic_app_icon_placeholder),
        IntentionRecord("reference", loc(R.string.ig_find_drawing_references), "Chrome", 756, R.drawable.ic_app_icon_placeholder),
    ).map { it.copy(isDemo=true) }
}
