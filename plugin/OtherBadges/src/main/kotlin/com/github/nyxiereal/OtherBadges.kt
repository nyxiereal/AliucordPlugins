package com.github.nyxiereal

import android.content.Context
import android.net.Uri
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.entities.Plugin
import com.aliucord.patcher.*
import com.aliucord.settings.delegate
import com.aliucord.utils.lazyField
import com.discord.databinding.UserProfileHeaderBadgeBinding
import com.discord.utilities.views.SimpleRecyclerAdapter
import com.discord.widgets.user.Badge
import com.discord.widgets.user.profile.UserProfileHeaderView
import com.discord.widgets.user.profile.UserProfileHeaderViewModel
import java.util.concurrent.CountDownLatch
import kotlin.time.*
import org.json.JSONObject

@AliucordPlugin
class OtherBadges : Plugin() {
    private var badgeMap = mutableMapOf<Long, List<Map<String, String>>>()

    // Cache settings
    private var cacheExpiration by settings.delegate(0L)
    private var cachedBadges by settings.delegate<Map<Long, List<Map<String, String>>>>(emptyMap())

    // Cached fields
    private val f_badgesAdapter by lazyField<UserProfileHeaderView>("badgesAdapter")
    private val f_recyclerAdapterData by lazyField<SimpleRecyclerAdapter<*, *>>("data")
    private val f_badgeViewHolderBinding by
            lazyField<UserProfileHeaderView.BadgeViewHolder>("binding")

    @Suppress("UNCHECKED_CAST", "DEPRECATION")
    @OptIn(ExperimentalTime::class)
    override fun start(context: Context) {
        // Load from cache or fetch new
        if (!isCacheExpired()) {
            badgeMap = cachedBadges.toMutableMap()
        } else {
            fetchBadges()
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

    @OptIn(ExperimentalTime::class)
    private fun fetchBadges() {
        val sources =
                listOf("https://badges.vencord.dev/badges.json", "https://equicord.org/badges.json")

        val latch = CountDownLatch(sources.size)

        for (source in sources) {
            Utils.threadPool.execute {
                try {
                    val json = Http.simpleGet(source)
                    val jsonObject = JSONObject(json)
                    val keys = jsonObject.keys()
                    val tempBadges = mutableMapOf<Long, MutableList<Map<String, String>>>()

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
                        tempBadges[userId] = badges
                    }

                    // Merge into main map
                    synchronized(badgeMap) {
                        for ((userId, badges) in tempBadges) {
                            val existing = badgeMap[userId]?.toMutableList() ?: mutableListOf()
                            existing.addAll(badges)
                            badgeMap[userId] = existing
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Failed to fetch or parse badges from $source", e)
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for all sources to complete, then update cache
        Utils.threadPool.execute {
            latch.await()
            synchronized(badgeMap) {
                cachedBadges = badgeMap.toMap()
                cacheExpiration = System.currentTimeMillis() + Duration.days(1).inWholeMilliseconds
            }
        }
    }

    private fun isCacheExpired(): Boolean = cacheExpiration <= System.currentTimeMillis()

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }
}
