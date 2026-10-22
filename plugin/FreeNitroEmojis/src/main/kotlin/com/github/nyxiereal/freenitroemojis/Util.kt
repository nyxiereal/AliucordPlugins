package com.github.nyxiereal.freenitroemojis

import java.lang.ref.WeakReference

// Default settings
const val EMOTE_SIZE = 48
const val FORMAT_TYPE_DEFAULT = "markdown"

// Keys
const val FORMAT_TYPE_KEY = "format_type"
const val REALMOJI_KEY = "realmoji"
const val COMPOUND_SENTENCES_KEY = "compound_sentences"

// Format types
const val FORMAT_URL = "url"
const val FORMAT_EXT_MD = "markdown_ext"
const val FORMAT_MD = "markdown"
// Keep the saved value used by the old zero-width-space option.
const val FORMAT_BRAILLE = "zerowidth"

// WebP settings
const val USE_WEBP_KEY = "use_webp"
const val USE_WEBP_DEFAULT = false

// Realmoji defaults
const val REALMOJI_DEFAULT = false
const val COMPOUND_SENTENCES_DEFAULT = false

internal fun formatEmojiLink(name: String, url: String, format: String?): String = when (format) {
    FORMAT_EXT_MD -> "[\u2236$name\u2236]($url)"
    FORMAT_MD -> "[$name]($url)"
    FORMAT_BRAILLE -> "[\u2800]($url)"
    else -> url
}

private const val EMOJI_URL_PATTERN =
    """https://cdn\.discordapp\.com/emojis/(\d+)\.(gif|png|webp)"""
// Keep sizes numeric, but accept other query parameters without swallowing punctuation or adjacent URLs.
private const val EMOJI_QUERY_PARAMETER =
    """(?:size=\d+|(?!size=)(?:(?!https?://)[^=\s)&<>])+(?<![.,!?;:])(?:=(?:(?!https?://)[^\s)&<>])*(?<![.,!?;:]))?)"""

// Groups 1-3: wrapped URL, ID, extension; groups 4-6: bare URL, ID, extension.
internal val markdownRegexCompound = Regex(
    """(?:\[(?:[a-zA-Z0-9_~]+|\u2236[a-zA-Z0-9_~]+\u2236|[\u2800\u200B])]\(($EMOJI_URL_PATTERN(?:\?[^)\s]*)?)\)|($EMOJI_URL_PATTERN(?:\?$EMOJI_QUERY_PARAMETER(?:&$EMOJI_QUERY_PARAMETER)*)?))"""
)
internal val markdownRegexSingle = Regex("^$markdownRegexCompound$")

internal class ReactionPickerState {
    @Volatile private var picker: WeakReference<Any>? = null
    val isOpen: Boolean get() = picker?.get() != null

    fun onOpen(picker: Any, isReaction: Boolean) {
        this.picker = if (isReaction) WeakReference(picker) else null
    }

    fun onClose(picker: Any) {
        if (this.picker?.get() === picker) clear()
    }

    fun clear() {
        picker = null
    }
}
