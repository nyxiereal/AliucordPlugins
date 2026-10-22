package com.github.nyxiereal.freenitroemojis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilTest {
    private val url = "https://cdn.discordapp.com/emojis/123.png?name=wave&size=48"

    @Test
    fun `default markdown stays unchanged and legacy selection uses braille`() {
        assertEquals("[wave]($url)", formatEmojiLink("wave", url, FORMAT_TYPE_DEFAULT))
        assertEquals("[\u2236wave\u2236]($url)", formatEmojiLink("wave", url, FORMAT_EXT_MD))
        assertEquals("[\u2800]($url)", formatEmojiLink("wave", url, "zerowidth"))
        assertEquals(url, formatEmojiLink("wave", url, FORMAT_URL))
    }

    @Test
    fun `all generated formats are fully consumed in single and compound mode`() {
        for (extension in listOf("png", "gif", "webp")) {
            val emojiUrl = "https://cdn.discordapp.com/emojis/123.$extension?name=wave&size=48" +
                if (extension == "webp") "&lossless=true&animated=true" else ""
            for (format in listOf(FORMAT_MD, FORMAT_EXT_MD, FORMAT_BRAILLE, FORMAT_URL)) {
                val link = formatEmojiLink("wave", emojiUrl, format)
                val match = markdownRegexSingle.matchEntire(link)!!
                val urlGroup = if (match.groupValues[1].isNotEmpty()) 1 else 4
                assertEquals(emojiUrl, match.groupValues[urlGroup])
                assertEquals("123", match.groupValues[urlGroup + 1])
                assertEquals(extension, match.groupValues[urlGroup + 2])
                assertEquals(":emoji:", markdownRegexSingle.replace(link, ":emoji:"))
                assertEquals(
                    "hello :emoji: :emoji: bye",
                    markdownRegexCompound.replace("hello $link $link bye", ":emoji:")
                )
            }
        }
    }

    @Test
    fun `old zero width links and disambiguated names leave no wrappers`() {
        for (label in listOf("\u200B", "wave~1", "\u2236wave~1\u2236")) {
            val link = "[$label]($url)"
            assertEquals(":emoji:", markdownRegexSingle.replace(link, ":emoji:"))
            assertEquals("hi :emoji:!", markdownRegexCompound.replace("hi $link!", ":emoji:"))
        }
    }

    @Test
    fun `closing parentheses outside emoji links are preserved`() {
        for (format in listOf(FORMAT_MD, FORMAT_EXT_MD, FORMAT_BRAILLE, FORMAT_URL)) {
            val link = formatEmojiLink("wave", url, format)
            assertEquals("(:emoji:)", markdownRegexCompound.replace("($link)", ":emoji:"))
            assertEquals("((:emoji:))", markdownRegexCompound.replace("(($link))", ":emoji:"))
            assertEquals("$link)", markdownRegexSingle.replace("$link)", ":emoji:"))
        }
    }

    @Test
    fun `bare links preserve following punctuation and adjacent emoji links`() {
        for (emojiUrl in listOf(
            url,
            url.replace(".png", ".gif"),
            url.replace(".png", ".webp").replace("&size=48", "&lossless=true&animated=true&size=48")
        )) {
            val link = formatEmojiLink("wave", emojiUrl, FORMAT_URL)
            for (suffix in listOf(",", ".", "!", ";", "?", "text", ":wave:", "<:other:456>")) {
                assertEquals(
                    ":emoji:$suffix",
                    markdownRegexCompound.replace("$link$suffix", ":emoji:")
                )
                assertEquals("$link$suffix", markdownRegexSingle.replace("$link$suffix", ":emoji:"))
            }
            assertEquals(":emoji::emoji:", markdownRegexCompound.replace("$link$link", ":emoji:"))
            assertEquals("$link$link", markdownRegexSingle.replace("$link$link", ":emoji:"))
        }
    }

    @Test
    fun `bare links consume complete queries with other CDN parameters`() {
        for (emojiUrl in listOf(
            "https://cdn.discordapp.com/emojis/123.png?quality=lossless",
            "$url&quality=lossless",
            "https://cdn.discordapp.com/emojis/123.webp?quality=0.8&format=webp&name=wave&animated=true",
            "$url&custom-key=some%20value+text&empty=&flag",
            "$url&custom-key=a/b:c,d?x=y"
        )) {
            val match = markdownRegexSingle.matchEntire(emojiUrl)!!
            assertEquals(emojiUrl, match.groupValues[4])
            assertEquals(":emoji:", markdownRegexSingle.replace(emojiUrl, ":emoji:"))
            assertEquals("hi :emoji:!", markdownRegexCompound.replace("hi $emojiUrl!", ":emoji:"))
            assertEquals(":emoji:.", markdownRegexCompound.replace("$emojiUrl.", ":emoji:"))
            assertEquals(
                ":emoji::emoji:",
                markdownRegexCompound.replace("$emojiUrl$url", ":emoji:")
            )
        }
    }

    @Test
    fun `wrapped links still accept other CDN query parameters`() {
        val emojiUrl = "$url&quality=lossless"
        val link = formatEmojiLink("wave", emojiUrl, FORMAT_BRAILLE)
        assertEquals(":emoji:", markdownRegexSingle.replace(link, ":emoji:"))
        assertEquals(":emoji:.", markdownRegexCompound.replace("$link.", ":emoji:"))
    }

    @Test
    fun `single mode does not replace links inside a sentence`() {
        val content = "hello ${formatEmojiLink("wave", url, FORMAT_BRAILLE)}"
        assertEquals(content, markdownRegexSingle.replace(content, ":emoji:"))
        assertEquals("hello :emoji:", markdownRegexCompound.replace(content, ":emoji:"))
    }

    @Test
    fun `reaction picker remains active until its own view closes`() {
        val state = ReactionPickerState()
        val longPressPicker = Any()
        val quickAddPicker = Any()
        assertFalse(state.isOpen)
        state.onOpen(longPressPicker, isReaction = true)
        assertTrue(state.isOpen)
        state.onClose(Any())
        assertTrue(state.isOpen)
        state.onOpen(quickAddPicker, isReaction = true)
        state.onClose(longPressPicker)
        assertTrue(state.isOpen)
        state.onClose(quickAddPicker)
        assertFalse(state.isOpen)
    }

    @Test
    fun `composer picker and plugin stop restore spoofing`() {
        val state = ReactionPickerState()
        val picker = Any()
        state.onOpen(picker, isReaction = true)
        state.onOpen(Any(), isReaction = false)
        assertFalse(state.isOpen)
        state.onOpen(picker, isReaction = true)
        state.clear()
        assertFalse(state.isOpen)
    }
}