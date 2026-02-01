package com.github.nyxiereal

import android.annotation.SuppressLint
import android.text.InputType
import android.view.View
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage
import com.aliucord.views.Button
import com.aliucord.views.TextInput
import com.discord.views.CheckedSetting

class PluginSettings(
    private val settingsAPI: SettingsAPI
) : SettingsPage() {

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
            "Markdown [name](url)" to FORMAT_MD,
            "Zero-width space [\u200b](url)" to FORMAT_ZW_SPACE
        )
        
        var selectedFormat = settingsAPI.getString(FORMAT_TYPE_KEY, FORMAT_TYPE_DEFAULT)
        
        val radioButtons = mutableListOf<CheckedSetting>()
        val createRadioListener: (MutableList<CheckedSetting>) -> (Boolean) -> Unit = { buttons ->
            { isChecked ->
                if (isChecked) {
                    buttons.forEach { button ->
                        if (button.isChecked) {
                            button.isChecked = false
                        }
                    }
                }
            }
        }

        formatOptions.forEach { (label, value) ->
            val radio = Utils.createCheckedSetting(
                context,
                CheckedSetting.ViewType.RADIO,
                label,
                null
            ).apply {
                isChecked = value == selectedFormat
                setOnCheckedListener { isChecked ->
                    if (isChecked) {
                        selectedFormat = value
                        createRadioListener(radioButtons)(true)
                    }
                }
            }

            radioButtons.add(radio)
        }

        // Realmoji toggle
        val realmojiToggle = Utils.createCheckedSetting(
            context,
            CheckedSetting.ViewType.CHECK,
            "Enable Realmojis",
            "Converts markdown emoji back to Discord format for proper display"
        ).apply {
            isChecked = settingsAPI.getBool(REALMOJI_KEY, REALMOJI_DEFAULT)
        }

        // Compound sentences toggle (only relevant when realmojis are enabled)
        val compoundSentencesToggle = Utils.createCheckedSetting(
            context,
            CheckedSetting.ViewType.CHECK,
            "Allow emoji in compound sentences",
            "When enabled, emoji will work in sentences. When disabled, only standalone emoji messages will work"
        ).apply {
            isChecked = settingsAPI.getBool(COMPOUND_SENTENCES_KEY, COMPOUND_SENTENCES_DEFAULT)
        }

        val saveButton = Button(context).apply {
            text = "Save"
            setOnClickListener {
                settingsAPI.setString(EMOTE_SIZE_KEY, textInput.editText.text.toString())
                settingsAPI.setString(FORMAT_TYPE_KEY, selectedFormat)
                settingsAPI.setBool(REALMOJI_KEY, realmojiToggle.isChecked)
                settingsAPI.setBool(COMPOUND_SENTENCES_KEY, compoundSentencesToggle.isChecked)
                Utils.showToast("Successfully saved!")
                close()
            }
        }

        addView(textInput)
        radioButtons.forEach { addView(it) }
        addView(realmojiToggle)
        addView(compoundSentencesToggle)
        addView(saveButton)
    }
}
