package com.github.nyxiereal

import android.content.Context
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.aliucord.utils.ReflectUtils
import com.discord.databinding.WidgetChannelsListItemChannelBinding
import com.discord.databinding.WidgetHomeBinding
import com.discord.widgets.channels.list.WidgetChannelsListAdapter
import com.discord.widgets.channels.list.items.ChannelListItem
import com.discord.widgets.home.WidgetHome
import com.discord.widgets.home.WidgetHomeHeaderManager
import com.discord.widgets.home.WidgetHomeModel

@AliucordPlugin
class Unobstructor : Plugin() {
    override fun start(context: Context) {
        // Patch channel list
        patcher.patch(
            WidgetChannelsListAdapter.ItemChannelText::class.java,
            "onConfigure",
            arrayOf<Class<*>>(Int::class.javaPrimitiveType!!, ChannelListItem::class.java),
            Hook { callFrame ->
                val binding = ReflectUtils.getField(
                    callFrame.thisObject,
                    "binding"
                ) as? WidgetChannelsListItemChannelBinding ?: return@Hook

                val channelName = binding.root.findViewById<TextView>(
                    Utils.getResId("channels_item_channel_name", "id")
                )

                val originalText = channelName.text.toString()
                channelName.text = filterChannelName(originalText)
            }
        )

        // Patch top bar
        patcher.patch(
            WidgetHomeHeaderManager::class.java,
            "configure",
            arrayOf<Class<*>>(
                WidgetHome::class.java,
                WidgetHomeModel::class.java,
                WidgetHomeBinding::class.java
            ),
            Hook { callFrame ->
                val widgetHome = callFrame.args[0] as WidgetHome
                val widgetHomeModel = callFrame.args[1] as WidgetHomeModel

                val channel = widgetHomeModel.channel
                val originalName = channel.p() // p() returns the channel name
                widgetHome.setActionBarTitle(filterChannelName(originalName))
            }
        )
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }

    private fun filterChannelName(name: String): String {
        return name.filter { char ->
            char.isLetter() || char.isDigit() || char == ' ' || char == '-'
        }
            .trim()
    }
}
