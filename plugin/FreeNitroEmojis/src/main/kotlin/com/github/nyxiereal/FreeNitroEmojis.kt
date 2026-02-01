package com.github.nyxiereal

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.aliucord.patcher.InsteadHook
import com.aliucord.patcher.PreHook
import com.discord.models.domain.emoji.ModelEmojiCustom
import com.discord.models.message.Message
import com.discord.restapi.RestAPIParams
import com.discord.api.message.embed.MessageEmbed
import com.discord.stores.StoreStream
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Field
import java.net.URL

@AliucordPlugin
class FreeNitroEmojis : Plugin() {

    private val reflectionCache = HashMap<String, Field>()
    private val emojiRegex by lazy { Regex("""<(a)?:(F_)?([a-zA-Z0-9_]+):(\d+)>""") }
    private val markdownRegexCompound by lazy {
        Regex("""\[[a-zA-Z0-9_~]+?\]\((https:\/\/cdn\.discordapp\.com\/emojis\/(\d+)\.([a-z]{3,4})?[^\)\(\[\]]*?)\)""")
    }
    private val markdownRegexSingle by lazy {
        Regex("""^\[[a-zA-Z0-9_~]+?\]\((https:\/\/cdn\.discordapp\.com\/emojis\/(\d+)\.([a-z]{3,4})?[^\)\(\[\]]*?)\)$""")
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun start(context: Context) {
        val emojiClass = ModelEmojiCustom::class.java
        val getChatInputTextMethod = emojiClass.getDeclaredMethod("getChatInputText")
        val getMessageContentReplacementMethod = emojiClass.getDeclaredMethod("getMessageContentReplacement")
        val isUsableMethod = emojiClass.getDeclaredMethod("isUsable")
        val isAvailableMethod = emojiClass.getDeclaredMethod("isAvailable")

        patcher.patch(getChatInputTextMethod, Hook { getChatReplacement(it) })
        patcher.patch(getMessageContentReplacementMethod, Hook { getChatReplacement(it) })
        patcher.patch(isUsableMethod, InsteadHook { true })
        patcher.patch(isAvailableMethod, InsteadHook { true })

        // Realmoji support: Convert markdown emoji back to Discord format
        if (settings.getBool(REALMOJI_KEY, REALMOJI_DEFAULT)) {
            val messageCtor = Message::class.java.declaredConstructors.firstOrNull {
                !it.isSynthetic
            } ?: throw IllegalStateException("Didn't find Message ctor")

            val markdownRegex = if (settings.getBool(COMPOUND_SENTENCES_KEY, COMPOUND_SENTENCES_DEFAULT)) {
                markdownRegexCompound
            } else {
                markdownRegexSingle
            }

            patcher.patch(messageCtor, PreHook { param ->
                if (param.args[4] != null) {
                    @Suppress("UNCHECKED_CAST") val oldEmbeds = param.args[12] as List<MessageEmbed>
                    val newEmbeds = ArrayList<MessageEmbed>(oldEmbeds)

                    param.args[4] = markdownRegex.replace(param.args[4] as String) {
                        val url = it.groupValues[1]
                        val emojiId = it.groupValues[2]
                        val extension = it.groupValues[3]

                        var animated = if (extension == "gif") "a" else ""
                        var emojiName = "UNKNOWN_FAKE_EMOJI"

                        try {
                            URL(url).query?.split("&")?.forEach { queryPair ->
                                val pair = queryPair.split("=")
                                when {
                                    extension == "webp" && pair.getOrNull(0) == "animated" && pair.getOrNull(1) == "true" -> animated = "a"
                                    pair.getOrNull(0) == "name" -> emojiName = pair.getOrNull(1)?.takeWhile { c -> c.isLetterOrDigit() || c == '_' } ?: emojiName
                                }
                            }
                        } catch (e: Exception) {
                            // Silently ignore URL parsing errors
                        }

                        newEmbeds.removeIf {
                            it.l().startsWith("https://cdn.discordapp.com/emojis/$emojiId")
                        }

                        "<$animated:$emojiName:$emojiId>"
                    }
                    param.args[12] = newEmbeds
                }
            })

            // Convert F_ emoji back to markdown in outgoing messages
            val restApiMessageCtor =
                RestAPIParams.Message::class.java.declaredConstructors.firstOrNull {
                    !it.isSynthetic
                } ?: throw IllegalStateException("Didn't find RestAPIParams.Message ctor")
            val restApiMessageContent =
                RestAPIParams.Message::class.java.getDeclaredField("content")
            restApiMessageContent.isAccessible = true

            patcher.patch(restApiMessageCtor, Hook { param ->
                var content = restApiMessageContent.get(param.thisObject) as? String ?: return@Hook

                content = emojiRegex.replace(content) {
                    val isFake = it.groupValues[2] == "F_"
                    if (!isFake) return@replace it.value

                    val emojiName = it.groupValues[3]
                    val emojiId = it.groupValues[4]
                    val emojiExtension = "webp"
                    val emoteSize = settings.getString(EMOTE_SIZE_KEY, EMOTE_SIZE_DEFAULT).toIntOrNull()
                    "[$emojiName](https://cdn.discordapp.com/emojis/$emojiId.$emojiExtension?name=$emojiName&size=$emoteSize)"
                }

                restApiMessageContent.set(param.thisObject, content)
            })
        }

        val experiments = StoreStream.getExperiments()
        experiments.setOverride("2021-03_nitro_emoji_autocomplete_upsell_android", 1)
    }

    private fun getChatReplacement(callFrame: XC_MethodHook.MethodHookParam) {
        val thisObject = callFrame.thisObject as ModelEmojiCustom
        val isUsable = thisObject.getCachedField<Boolean>("isUsable")
        val available = thisObject.getCachedField<Boolean>("available")

        if (isUsable && available) {
            callFrame.result = callFrame.result
            return
        }

        var finalUrl = "https://cdn.discordapp.com/emojis/"

        val idStr = thisObject.getCachedField<String>("idStr")
        val isAnimated = thisObject.getCachedField<Boolean>("isAnimated")
        val emoteName = thisObject.getCachedField<String>("name")

        // If realmojis are enabled, use Discord format with F_ prefix
        if (settings.getBool(REALMOJI_KEY, REALMOJI_DEFAULT)) {
            val animated = if (isAnimated) "a" else ""
            callFrame.result = "<$animated:F_$emoteName:$idStr>"
            return
        }

        finalUrl += idStr
        val emoteSize = settings.getString(EMOTE_SIZE_KEY, EMOTE_SIZE_DEFAULT).toIntOrNull()

        finalUrl += ".webp?name=$emoteName"

        if (emoteSize != null) {
            finalUrl += "&size=${emoteSize}"
        }
        
        val formatType = settings.getString(FORMAT_TYPE_KEY, FORMAT_TYPE_DEFAULT)
        callFrame.result = when (formatType) {
            FORMAT_EXT_MD -> "[\u2236$emoteName\u2236]($finalUrl)" // Using Unicode colon U+2236, `∶`
            FORMAT_MD -> "[$emoteName]($finalUrl)"
            FORMAT_ZW_SPACE -> "[\u200b]($finalUrl)"
            else -> finalUrl
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        val experiments = StoreStream.getExperiments()
        experiments.setOverride("2021-03_nitro_emoji_autocomplete_upsell_android", 0)
    }

    /**
     * Get a reflected field from cache or compute it if cache is absent
     * @param V type of the field value
     */
    @RequiresApi(Build.VERSION_CODES.N)
    private inline fun <reified V> Any.getCachedField(
        name: String,
        instance: Any? = this,
    ): V {
        val clazz = this::class.java
        return reflectionCache.computeIfAbsent(clazz.name + name) {
            clazz.getDeclaredField(name).also {
                it.isAccessible = true
            }
        }.get(instance) as V
    }

    init {
        settingsTab = SettingsTab(
            PluginSettings::class.java,
            SettingsTab.Type.PAGE
        ).withArgs(settings)
    }
}
