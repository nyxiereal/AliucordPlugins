package com.github.nyxiereal

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.aliucord.Constants
import com.aliucord.utils.DimenUtils
import com.discord.utilities.time.TimeUtils
import com.lytefast.flexinput.R

private val cachedFont = mutableMapOf<Int, android.graphics.Typeface?>()

fun getCachedFont(context: Context, fontRes: Int): android.graphics.Typeface? {
    return cachedFont.getOrPut(fontRes) {
        ResourcesCompat.getFont(context, fontRes)
    }
}

fun createTextView(
    context: Context,
    textStyleRes: Int,
    text: String,
    paddingDp: Padding = Padding()
): TextView = TextView(context, null, 0, textStyleRes).apply {
    this.text = text
    setPadding(
        DimenUtils.dpToPx(paddingDp.left),
        DimenUtils.dpToPx(paddingDp.top),
        DimenUtils.dpToPx(paddingDp.right),
        DimenUtils.dpToPx(paddingDp.bottom)
    )
}

fun createHeaderTextView(
    context: Context,
    text: String,
    paddingDp: Padding = Padding()
): TextView = createTextView(context, R.i.UiKit_Settings_Item_Header, text, paddingDp).apply {
    typeface = getCachedFont(context, Constants.Fonts.whitney_semibold)
}

fun createSubTextView(
    context: Context,
    text: String,
    paddingDp: Padding = Padding(),
    color: Int? = null
): TextView = createTextView(context, R.i.UiKit_Settings_Item_SubText, text, paddingDp).apply {
    if (color != null) setTextColor(color)
}

fun createLabelTextView(
    context: Context,
    text: String,
    paddingDp: Padding = Padding()
): TextView = createTextView(context, R.i.UiKit_Settings_Item_Label, text, paddingDp).apply {
    typeface = getCachedFont(context, Constants.Fonts.whitney_semibold)
}

fun createCard(context: Context, padding: Int = 8): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    background = android.graphics.drawable.GradientDrawable().apply {
        setColor(Color.parseColor("#2F3136"))
        cornerRadius = DimenUtils.dpToPx(8).toFloat()
    }
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        setMargins(
            DimenUtils.dpToPx(4),
            DimenUtils.dpToPx(2),
            DimenUtils.dpToPx(4),
            DimenUtils.dpToPx(2)
        )
    }
    setPadding(0, 0, 0, DimenUtils.dpToPx(padding))
}

fun formatDate(context: Context, isoDate: String): String = try {
    val timestamp = TimeUtils.parseUTCDate(isoDate)
    if (timestamp == 0L) isoDate else TimeUtils.INSTANCE.renderUtcDate(timestamp, context, 2)
} catch (e: Exception) {
    isoDate
}

data class Padding(
    val left: Int = 16,
    val top: Int = 8,
    val right: Int = 16,
    val bottom: Int = 8
)
