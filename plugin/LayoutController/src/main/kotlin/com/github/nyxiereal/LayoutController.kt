package com.github.nyxiereal

import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.github.nyxiereal.layoutcontroller.PluginSettings
import com.github.nyxiereal.layoutcontroller.util.PREFERENCE_DEFAULT_VALUE
import com.github.nyxiereal.layoutcontroller.util.patches

@AliucordPlugin
class LayoutController : Plugin() {
    override fun start(context: Context) {
        for (bloatPatcher in patches) {
            bloatPatcher.patch(patcher, settings)
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }

    override fun requiresRestart(): Boolean =
        patches.any { it.requiresRestart && settings.getBool(it.key, PREFERENCE_DEFAULT_VALUE) }


    init {
        settingsTab = SettingsTab(PluginSettings::class.java, SettingsTab.Type.PAGE)
            .withArgs(settings)
    }
}