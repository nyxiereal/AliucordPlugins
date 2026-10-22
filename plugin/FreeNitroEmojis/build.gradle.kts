version = "1.2.2"

description =
    "Use custom emojis without Nitro"

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.kotlin.stdlib)
}

aliucord {
    author("xinto", 423915768191647755L)

    changelog.set(
        """
        # 1.2.2
        - Restored invisible markdown using blank braille (U+2800), including saved zero-width selections
        - Fixed realmoji conversion for invisible links and selected outgoing formats
        - Disabled emoji spoofing in reaction pickers while keeping it enabled in the message picker

        # 1.2.1
        - Fixed invalid custom emoji sizes by always requesting 48px emotes
        - Fixed extended markdown realmoji parsing and live settings updates
        - Improved realmoji hook safety and cleanup

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
        """.trimIndent()
    )
}