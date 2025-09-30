package com.github.nyxiereal

import android.content.Context
import android.net.Uri
import com.aliucord.Http
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.*
import com.aliucord.utils.lazyField
import com.discord.databinding.UserProfileHeaderBadgeBinding
import com.discord.utilities.views.SimpleRecyclerAdapter
import com.discord.widgets.user.Badge
import com.discord.widgets.user.profile.UserProfileHeaderView
import com.discord.widgets.user.profile.UserProfileHeaderViewModel
import org.json.JSONObject

@AliucordPlugin
class VencordAndEquicordBadges : Plugin() {
    private val badgeMap = mutableMapOf<Long, List<Map<String, String>>>()

    // Cached fields
    private val f_badgesAdapter by lazyField<UserProfileHeaderView>("badgesAdapter")
    private val f_recyclerAdapterData by lazyField<SimpleRecyclerAdapter<*, *>>("data")
    private val f_badgeViewHolderBinding by
            lazyField<UserProfileHeaderView.BadgeViewHolder>("binding")

    @Suppress("UNCHECKED_CAST")
    override fun start(context: Context) {
        val sources =
                listOf("https://badges.vencord.dev/badges.json", "https://equicord.org/badges.json")

        for (source in sources) {
            Thread {
                        try {
                            val json = Http.simpleGet(source)
                            val jsonObject = JSONObject(json)
                            val keys = jsonObject.keys()
                            while (keys.hasNext()) {
                                val userIdStr = keys.next()
                                val userId = userIdStr.toLongOrNull() ?: continue
                                val badgesArray = jsonObject.getJSONArray(userIdStr)
                                val badges = mutableListOf<Map<String, String>>()
                                for (i in 0 until badgesArray.length()) {
                                    val badgeObj = badgesArray.getJSONObject(i)
                                    val tooltip = badgeObj.getString("tooltip")
                                    val badgeUrl = badgeObj.getString("badge")
                                    badges.add(mapOf("tooltip" to tooltip, "badge" to badgeUrl))
                                }
                                badgeMap[userId] = badges
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to fetch or parse badges from $source", e)
                        }
                    }
                    .start()
        }

        // Add badges to the RecyclerView data for badges in the user profile header
        patcher.after<UserProfileHeaderView>(
                "updateViewState",
                UserProfileHeaderViewModel.ViewState.Loaded::class.java
        ) { (_, state: UserProfileHeaderViewModel.ViewState.Loaded) ->
            val userBadges = badgeMap[state.user.id] ?: return@after
            val customBadges =
                    userBadges.map { Badge(0, null, it["tooltip"]!!, false, it["badge"]) }

            val adapter =
                    f_badgesAdapter[this] as
                            SimpleRecyclerAdapter<Badge, UserProfileHeaderView.BadgeViewHolder>
            val data = f_recyclerAdapterData[adapter] as MutableList<Badge>
            data.addAll(customBadges)
        }

        // Set image url for badge ImageViews
        patcher.after<UserProfileHeaderView.BadgeViewHolder>("bind", Badge::class.java) {
                (_, badge: Badge) ->
            // Image URL is smuggled through the objectType property
            val url = badge.objectType

            // Check that badge is ours (icon == 0 means custom)
            if (badge.icon != 0 || url == null) return@after

            val binding = f_badgeViewHolderBinding[this] as UserProfileHeaderBadgeBinding
            val imageView = binding.b
            imageView.setImageURI(Uri.parse(url))
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}
