package com.github.nyxiereal

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.discord.widgets.settings.WidgetSettings
import com.lytefast.flexinput.R

@AliucordPlugin
class ViewQuests : Plugin() {
    override fun start(context: Context) {
        // Add quest entry to the menu
        patcher.patch(
                WidgetSettings::class.java.getDeclaredMethod("onViewBound", View::class.java),
                Hook { callFrame ->
                    val view = callFrame.args[0] as CoordinatorLayout
                    val layout =
                            (view.getChildAt(1) as NestedScrollView).getChildAt(0) as
                                    LinearLayoutCompat
                    val ctx = layout.context

                    val baseIndex =
                            layout.indexOfChild(
                                    layout.findViewById<TextView>(
                                            Utils.getResId("qr_scanner", "id")
                                    )
                            )

                    // Add the menu entry
                    TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
                        text = "Quests"
                        setCompoundDrawablesWithIntrinsicBounds(
                                Utils.tintToTheme(ctx.getDrawable(R.e.ic_gift_24dp)),
                                null,
                                null,
                                null
                        )
                        setOnClickListener { Utils.openPageWithProxy(ctx, QuestsPage()) }
                        layout.addView(this, baseIndex + 1)
                    }
                }
        )
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}
