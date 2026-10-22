version = "2.0.1"

description =
        "Makes you able to view and do quests in the settings"

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.aliucord)
    testImplementation(libs.discord)
    testImplementation(libs.kotlin.stdlib)
}

aliucord {
    author("nyxiereal", 1242567443742986373L)
    changelog.set("""
        # 2.0.1
        - Fixed a crash when quests omit the legacy task configuration (#3)

        # 2.0.0
        - Added the ability to do mobile video quests
        - Added a proper scrollable page with images for quests
        - Collectibles now look nicer
        
        # 1.0.1:
        - Minor fixes
        - Added a collectibles page, idk what it really does, but i found it and added it
        - WARNING: the quests page is broken since aliucord superprops havent been updated for a while

        # 1.0.0:
        - Initial release
        """.trimIndent())
}

