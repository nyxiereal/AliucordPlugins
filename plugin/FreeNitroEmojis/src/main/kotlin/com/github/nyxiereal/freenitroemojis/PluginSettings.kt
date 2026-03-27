package com.github.nyxiereal.freenitroemojis

import android.annotation.SuppressLint
import android.text.InputType
import android.view.View
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage
import com.aliucord.views.Button
import com.aliucord.views.TextInput
import com.discord.views.CheckedSetting

class PluginSettings(private val settingsAPI: SettingsAPI) : SettingsPage() {
    @SuppressLint("SetTextI18n")
    override fun onViewBound(view: View) {
        super.onViewBound(view)

        val context = requireContext()
        setActionBarTitle("FreeNitroEmojis")

        val textInput = TextInput(context).apply {
            setHint("Fallback emote size (48 is the default)")
            editText.setText(settingsAPI.getString(EMOTE_SIZE_KEY, EMOTE_SIZE_DEFAULT).toString())
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            editText.maxLines = 1
        }

        // Format type selection
        val formatOptions = listOf(
            "URL" to FORMAT_URL,
            "Extended markdown [\u2236name\u2236](url)" to FORMAT_EXT_MD,
            "Markdown [name](url)" to FORMAT_MD
        )

        var selectedFormat = settingsAPI.getString(FORMAT_TYPE_KEY, FORMAT_TYPE_DEFAULT)

        val radioButtons = mutableListOf<CheckedSetting>()

        formatOptions.forEach { (label, value) ->
            val radio = Utils
                .createCheckedSetting(
                    context,
                    CheckedSetting.ViewType.RADIO,
                    label,
                    null
                ).apply {
                    isChecked = value == selectedFormat
                }

            radio.setOnCheckedListener { isChecked ->
                if (isChecked) {
                    selectedFormat = value
                    // Uncheck all other radio buttons
                    radioButtons.forEach { button ->
                        if (button != radio) {
                            button.isChecked = false
                        }
                    }
                }
            }

            radioButtons.add(radio)
        }

        val realmojiToggle = Utils
            .createCheckedSetting(
                context,
                CheckedSetting.ViewType.CHECK,
                "Enable realmojis",
                "Makes your Discord client think freenitro emojis are nitro emojis"
            ).apply {
                isChecked = settingsAPI.getBool(REALMOJI_KEY, REALMOJI_DEFAULT)
            }

        val compoundSentencesToggle = Utils
            .createCheckedSetting(
                context,
                CheckedSetting.ViewType.CHECK,
                "Enable realmojis in compound sentences",
                "Self explanatory, allows for messages like 'hello :sogged: meow' to display properly"
            ).apply {
                isChecked = settingsAPI.getBool(COMPOUND_SENTENCES_KEY, COMPOUND_SENTENCES_DEFAULT)
            }

        val webpToggle = Utils
            .createCheckedSetting(
                context,
                CheckedSetting.ViewType.CHECK,
                "Use WebP format",
                "Use WebP for all emojis instead of GIF for animated emojis and PNG for static emojis. Disable if you experience issues with animated emojis not animating."
            ).apply {
                isChecked = settingsAPI.getBool(USE_WEBP_KEY, USE_WEBP_DEFAULT)
            }

        val saveButton = Button(context).apply {
            text = "Save"
            setOnClickListener {
                settingsAPI.setString(EMOTE_SIZE_KEY, textInput.editText.text.toString())
                settingsAPI.setString(FORMAT_TYPE_KEY, selectedFormat)
                settingsAPI.setBool(REALMOJI_KEY, realmojiToggle.isChecked)
                settingsAPI.setBool(COMPOUND_SENTENCES_KEY, compoundSentencesToggle.isChecked)
                settingsAPI.setBool(USE_WEBP_KEY, webpToggle.isChecked)
                Utils.showToast("Settings saved!")
                close()
            }
        }

        addView(textInput)
        radioButtons.forEach { addView(it) }
        addView(realmojiToggle)
        addView(compoundSentencesToggle)
        addView(webpToggle)
        addView(saveButton)
    }
}