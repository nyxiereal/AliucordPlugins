version = "1.2.0"

description =
        "Use custom emojis without Nitro"

aliucord {
    author("xinto", 423915768191647755L)
    author("nyxiereal", 1242567443742986373L)

    changelog.set("""
        # 1.2.0
        - Added support for WebP emojis (animated+static). DISABLE IF YOU ENCOUNTER ISSUES!!!
        - Removed support for zero-width space format due to it being very buggy
        - Improved wording in settings
        - Improved realmoji support
        thanks fwt for testing!

        # 1.1.2
        - Fixed animated emojis not working

        # 1.1.1
        - Fixed plugin settings not working

        # 1.1.0
        - Added multiple format types - URL (original), markdown, extended markdown, zero-width space
        - Added support for realmojis (fake official emojis)

        # 1.0.0
        - Initial release
        - Added emote size customization
        """.trimIndent())
}

