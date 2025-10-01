#!/usr/bin/env fish

# Check if a plugin name is provided
if test (count $argv) -eq 0
    echo "Usage: ./createplugin.fish <PluginName>"
    exit 1
end

set plugin_name $argv[1]

# Create the plugin directory
mkdir -p "$plugin_name/src/main/kotlin/com/github/nyxiereal"

# Create the plugin Kotlin file
echo "package com.github.nyxiereal

import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin

@AliucordPlugin
class $plugin_name : Plugin() {
    @Suppress(\"UNCHECKED_CAST\")
    override fun start(context: Context) {
        // Plugin start logic here
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}" > "$plugin_name/src/main/kotlin/com/github/nyxiereal/$plugin_name.kt"

# Create the build file for the plugin
echo "version = \"1.0.0\"

description =
        \"TODO: Fill this in\"

aliucord {
    changelog.set(\"\"\"
        Initial release
        \"\"\".trimIndent())
}
" > "$plugin_name/build.gradle.kts"

echo "Plugin '$plugin_name' created successfully."