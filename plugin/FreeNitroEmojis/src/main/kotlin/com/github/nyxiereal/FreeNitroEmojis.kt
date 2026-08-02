package com.github.nyxiereal

import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.aliucord.patcher.InsteadHook
import com.aliucord.patcher.PreHook
import com.discord.api.message.embed.MessageEmbed
import com.discord.models.domain.emoji.ModelEmojiCustom
import com.discord.models.message.Message
import com.discord.restapi.RestAPIParams
import com.discord.stores.StoreStream
import com.github.nyxiereal.freenitroemojis.*
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Field
import java.net.URL

@AliucordPlugin
class FreeNitroEmojis : Plugin() {
    private val reflectionCache = HashMap<String, Field>()
    private val emojiRegex by lazy { Regex("""<(a)?:(F_)?([a-zA-Z0-9_]+):(\d+)>""") }
    private val markdownRegexCompound by lazy {
        // Matches emoji URLs both with [name](url) wrapper and plain URLs
        // Group 1: Full URL, Group 2: Emoji ID, Group 3: Extension
        Regex(
            """(?:\[(?:[a-zA-Z0-9_~]+|\u2236[a-zA-Z0-9_~]+\u2236)]\()?(https://cdn\.discordapp\.com/emojis/(\d+)\.(gif|png|webp)(?:\?[^)\s]*)?)\)?"""
        )
    }
    private val markdownRegexSingle by lazy {
        Regex(
            """^(?:\[(?:[a-zA-Z0-9_~]+|\u2236[a-zA-Z0-9_~]+\u2236)]\()?(https://cdn\.discordapp\.com/emojis/(\d+)\.(gif|png|webp)(?:\?[^)\s]*)?)\)?$"""
        )
    }

    override fun start(context: Context) {
        val emojiClass = ModelEmojiCustom::class.java
        val getChatInputTextMethod = emojiClass.getDeclaredMethod("getChatInputText")
        val getMessageContentReplacementMethod = emojiClass.getDeclaredMethod(
            "getMessageContentReplacement"
        )
        val isUsableMethod = emojiClass.getDeclaredMethod("isUsable")
        val isAvailableMethod = emojiClass.getDeclaredMethod("isAvailable")

        patcher.patch(getChatInputTextMethod, Hook { getChatReplacement(it) })
        patcher.patch(getMessageContentReplacementMethod, Hook { getChatReplacement(it) })
        patcher.patch(isUsableMethod, InsteadHook { true })
        patcher.patch(isAvailableMethod, InsteadHook { true })

        // Realmoji support: Convert markdown emoji back to Discord format
        val messageCtor = Message::class.java.declaredConstructors.firstOrNull {
            !it.isSynthetic &&
                it.parameterTypes.getOrNull(4) == String::class.java &&
                it.parameterTypes.getOrNull(12)?.let(List::class.java::isAssignableFrom) == true
        } ?: throw IllegalStateException("Didn't find Message ctor")

        patcher.patch(
            messageCtor,
            PreHook { param ->
                if (!settings.getBool(REALMOJI_KEY, REALMOJI_DEFAULT)) {
                    return@PreHook
                }

                val content = param.args.getOrNull(4) as? String ?: return@PreHook
                val oldEmbeds = param.args.getOrNull(12) as? List<*>
                val newEmbeds = oldEmbeds
                    ?.takeIf { embeds -> embeds.all { it is MessageEmbed } }
                    ?.mapTo(ArrayList<MessageEmbed>()) { it as MessageEmbed }
                val markdownRegex = if (
                    settings.getBool(COMPOUND_SENTENCES_KEY, COMPOUND_SENTENCES_DEFAULT)
                ) {
                    markdownRegexCompound
                } else {
                    markdownRegexSingle
                }

                param.args[4] = markdownRegex.replace(content) { match ->
                    val url = match.groupValues[1]
                    val emojiId = match.groupValues[2]
                    val extension = match.groupValues[3]

                    var animated = if (extension == "gif") "a" else ""
                    var emojiName = "UNKNOWN_FAKE_EMOJI"

                    try {
                        URL(url).query?.split("&")?.forEach { queryPair ->
                            val pair = queryPair.split("=")
                            when {
                                extension == "webp" &&
                                    pair.getOrNull(0) == "animated" &&
                                    pair.getOrNull(1) == "true" -> {
                                    animated = "a"
                                }

                                pair.getOrNull(0) == "name" -> {
                                    emojiName =
                                        pair.getOrNull(1)?.takeWhile { c ->
                                            c.isLetterOrDigit() || c == '_'
                                        } ?: emojiName
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Keep the fallback name if a malformed URL reaches this hook.
                    }

                    newEmbeds?.removeAll {
                        it.l()?.startsWith("https://cdn.discordapp.com/emojis/$emojiId.") == true
                    }

                    "<$animated:$emojiName:$emojiId>"
                }
                if (newEmbeds != null) {
                    param.args[12] = newEmbeds
                }
            }
        )

        // Convert F_ emoji back to markdown in outgoing messages
        val restApiMessageCtor =
            RestAPIParams.Message::class.java.declaredConstructors.firstOrNull {
                !it.isSynthetic
            } ?: throw IllegalStateException("Didn't find RestAPIParams.Message ctor")
        val restApiMessageContent = RestAPIParams.Message::class.java.getDeclaredField("content")
        restApiMessageContent.isAccessible = true

        patcher.patch(
            restApiMessageCtor,
            Hook { param ->
                if (!settings.getBool(REALMOJI_KEY, REALMOJI_DEFAULT)) return@Hook

                var content =
                    restApiMessageContent.get(param.thisObject) as? String ?: return@Hook

                content = emojiRegex.replace(content) {
                    val isFake = it.groupValues[2] == "F_"
                    if (!isFake) return@replace it.value

                    val isAnimated = it.groupValues[1].isNotEmpty()
                    val emojiName = it.groupValues[3]
                    val emojiId = it.groupValues[4]
                    val useWebp = settings.getBool(USE_WEBP_KEY, USE_WEBP_DEFAULT)

                    val urlBuilder = StringBuilder("https://cdn.discordapp.com/emojis/$emojiId")
                    if (useWebp) {
                        urlBuilder.append(".webp?name=$emojiName&lossless=true")
                        if (isAnimated) urlBuilder.append("&animated=true")
                    } else {
                        urlBuilder.append(if (isAnimated) ".gif" else ".png")
                        urlBuilder.append("?name=$emojiName")
                    }
                    urlBuilder.append("&size=$EMOTE_SIZE")

                    "[$emojiName]($urlBuilder)"
                }

                restApiMessageContent.set(param.thisObject, content)
            }
        )

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
        finalUrl += if (isAnimated) ".gif?name=$emoteName" else ".png?name=$emoteName"
        finalUrl += "&size=$EMOTE_SIZE"

        val formatType = settings.getString(FORMAT_TYPE_KEY, FORMAT_TYPE_DEFAULT)
        callFrame.result = when (formatType) {
            FORMAT_EXT_MD -> "[\u2236$emoteName\u2236]($finalUrl)"

            // Using Unicode colon U+2236, `\u2236`
            FORMAT_MD -> "[$emoteName]($finalUrl)"

            else -> finalUrl
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        val experiments = StoreStream.getExperiments()
        experiments.clearOverride("2021-03_nitro_emoji_autocomplete_upsell_android")
    }

    /**
     * Get a reflected field from cache or compute it if cache is absent
     * @param V type of the field value
     */
    private inline fun <reified V> Any.getCachedField(name: String, instance: Any? = this): V {
        val clazz = this::class.java
        val cacheKey = clazz.name + name
        val field = reflectionCache[cacheKey] ?: clazz.getDeclaredField(name).also {
            it.isAccessible = true
            reflectionCache[cacheKey] = it
        }
        return field.get(instance) as V
    }

    init {
        settingsTab = SettingsTab(
            PluginSettings::class.java,
            SettingsTab.Type.PAGE
        ).withArgs(settings)
    }
}