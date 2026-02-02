package com.github.nyxiereal.viewquests

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.aliucord.Http
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import com.aliucord.utils.SerializedName
import com.discord.utilities.time.TimeUtils
import com.lytefast.flexinput.R

data class QuestsResponse(val quests: List<Quest>)

data class Quest(
        val config: QuestConfig,
        @SerializedName("user_status") val userStatus: QuestUserStatus? = null
)

data class QuestConfig(
        @SerializedName("starts_at") val startsAt: String,
        @SerializedName("expires_at") val expiresAt: String,
        val messages: QuestMessages,
        @SerializedName("task_config") val taskConfig: QuestTaskConfig,
        @SerializedName("rewards_config") val rewardsConfig: QuestRewardsConfig
)

data class QuestMessages(
        @SerializedName("quest_name") val questName: String,
        @SerializedName("game_title") val gameTitle: String,
        @SerializedName("game_publisher") val gamePublisher: String
)

data class QuestTaskConfig(val tasks: Map<String, QuestTask>)

data class QuestTask(
        @SerializedName("event_name") val eventName: String,
        val target: Int,
        val title: String? = null
)

data class QuestRewardsConfig(val rewards: List<QuestReward>)

data class QuestReward(val type: Int, val messages: QuestRewardMessages)

data class QuestRewardMessages(@SerializedName("name_with_article") val nameWithArticle: String)

data class QuestUserStatus(
        @SerializedName("enrolled_at") val enrolledAt: String? = null,
        @SerializedName("completed_at") val completedAt: String? = null,
        @SerializedName("claimed_at") val claimedAt: String? = null,
        val progress: Map<String, QuestTaskProgress>? = null
)

data class QuestTaskProgress(
        @SerializedName("event_name") val eventName: String,
        val value: Int,
        @SerializedName("completed_at") val completedAt: String? = null
)

class QuestsPage : SettingsPage() {
    private val logger = Logger("ViewQuests")

    private fun addCollectiblesButton(context: Context) {
        createHeaderTextView(
            context,
            "View Collectibles",
            Padding(0, 0, 0, 16)
        ).apply {
            setCompoundDrawablesWithIntrinsicBounds(
                Utils.tintToTheme(context.getDrawable(R.e.ic_gift_24dp)),
                null,
                null,
                null
            )
            setOnClickListener { Utils.openPageWithProxy(context, CollectiblesPage()) }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, DimenUtils.dpToPx(16))
            }
            linearLayout.addView(this)
        }
    }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Loading Quests...")

        val context = view.context

        // Add button to navigate to collectibles page
        addCollectiblesButton(context)

        // Fetch quests from discord in a background thread
        Utils.threadPool.execute {
            try {
                val req = Http.Request.newDiscordRNRequest("/quests/@me", "GET")
                val res = req.execute()
                val questsResponse = res.json(QuestsResponse::class.java)

                // Update UI on main thread
                Utils.mainThread.post {
                    setActionBarTitle("Quests")

                    if (questsResponse.quests.isEmpty()) {
                        addNoQuestsView(context)
                    } else {
                        questsResponse.quests.forEach { quest -> addQuestCard(context, quest) }
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to fetch quests", e)
                Utils.mainThread.post {
                    setActionBarTitle("Quests")
                    addErrorView(context, e.message ?: "Unknown error")
                }
            }
        }
    }

    private fun addNoQuestsView(context: Context) {
        createSubTextView(
            context,
            "No quests available at the moment",
            Padding(16, 32, 16, 32)
        ).apply {
            gravity = Gravity.CENTER
            linearLayout.addView(this)
        }
    }

    private fun addErrorView(context: Context, error: String) {
        createSubTextView(
            context,
            "Failed to load quests:\n$error",
            Padding(16, 32, 16, 32),
            Color.parseColor("#ED4245")
        ).apply {
            gravity = Gravity.CENTER
            linearLayout.addView(this)
        }
    }

    private fun addQuestCard(context: Context, quest: Quest) {
        val config = quest.config
        val cardContainer = createCard(context, 8)

        createHeaderTextView(
            context,
            config.messages.questName,
            Padding(16, 16, 16, 8)
        ).apply { cardContainer.addView(this) }

        createSubTextView(
            context,
            "${config.messages.gameTitle}\nPromoted by ${config.messages.gamePublisher}",
            Padding(16, 4, 16, 12)
        ).apply { cardContainer.addView(this) }

        addTaskInfo(context, config.taskConfig, cardContainer)
        addRewardInfo(context, config.rewardsConfig, cardContainer)
        quest.userStatus?.let { addStatusInfo(context, it, cardContainer) }
        addDateInfo(context, config.startsAt, config.expiresAt, cardContainer)
        linearLayout.addView(cardContainer)
    }

    private fun addTaskInfo(
            context: Context,
            taskConfig: QuestTaskConfig,
            container: LinearLayout
    ) {
        createLabelTextView(
            context,
            "Tasks:",
            Padding(16, 8, 16, 4)
        ).apply { container.addView(this) }

        taskConfig.tasks.values.forEach { task ->
            createSubTextView(
                context,
                "- ${getTaskDescription(task)}",
                Padding(24, 2, 16, 2)
            ).apply { container.addView(this) }
        }
    }

    private fun addRewardInfo(
            context: Context,
            rewardsConfig: QuestRewardsConfig,
            container: LinearLayout
    ) {
        createLabelTextView(
            context,
            "Rewards:",
            Padding(16, 12, 16, 4)
        ).apply { container.addView(this) }

        rewardsConfig.rewards.forEach { reward ->
            val rewardName = reward.messages.nameWithArticle.removePrefix("a ").removePrefix("an ")
            createSubTextView(
                context,
                "- $rewardName (${getRewardType(reward.type)})",
                Padding(24, 2, 16, 2)
            ).apply { container.addView(this) }
        }
    }

    private fun addStatusInfo(
            context: Context,
            userStatus: QuestUserStatus,
            container: LinearLayout
    ) {
        val statusText = when {
            userStatus.claimedAt != null -> "Status: Claimed"
            userStatus.completedAt != null -> "Status: Completed"
            userStatus.enrolledAt != null -> "Status: In Progress"
            else -> "Status: Unknown"
        }
        
        createLabelTextView(
            context,
            statusText,
            Padding(16, 12, 16, 4)
        ).apply { container.addView(this) }

        if (userStatus.progress != null && userStatus.completedAt == null) {
            userStatus.progress.values.forEach { progress ->
                val progressText = if (progress.completedAt != null) {
                    "- ${progress.eventName}: Completed"
                } else {
                    "- ${progress.eventName}: ${formatProgressValue(progress.value)}"
                }
                createSubTextView(
                    context,
                    progressText,
                    Padding(24, 2, 16, 2)
                ).apply { container.addView(this) }
            }
        }
    }

    private fun addDateInfo(
            context: Context,
            startsAt: String,
            expiresAt: String,
            container: LinearLayout
    ) {
        createSubTextView(
            context,
            "Starts: ${formatDate(context, startsAt)}\nExpires: ${formatDate(context, expiresAt)}",
            Padding(16, 12, 16, 8)
        ).apply { container.addView(this) }
    }

    private fun getTaskDescription(task: QuestTask): String =
            when (task.eventName) {
                "STREAM_ON_DESKTOP" -> "Stream for ${formatDuration(task.target)}"
                "PLAY_ON_DESKTOP", "PLAY_ON_DESKTOP_V2" -> "Play for ${formatDuration(task.target)}"
                "PLAY_ON_XBOX" -> "Play on Xbox for ${formatDuration(task.target)}"
                "PLAY_ON_PLAYSTATION" -> "Play on PlayStation for ${formatDuration(task.target)}"
                "WATCH_VIDEO" -> "Watch video for ${formatDuration(task.target)}"
                "WATCH_VIDEO_ON_MOBILE" ->
                        "Watch video on mobile for ${formatDuration(task.target)}"
                "PLAY_ACTIVITY" -> "Play activity for ${formatDuration(task.target)}"
                else -> task.title ?: task.eventName
            }

    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        return when {
            minutes < 60 -> "$minutes minutes"
            else -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes > 0) "$hours hours $remainingMinutes minutes"
                else "$hours hours"
            }
        }
    }

    private fun getRewardType(type: Int): String =
            when (type) {
                1 -> "Reward Code"
                2 -> "In-Game Item"
                3 -> "Collectible"
                4 -> "Discord Orbs"
                5 -> "Fractional Premium"
                else -> "Unknown"
            }

    private fun formatProgressValue(value: Int): String =
            if (value >= 60) "${value / 60} minutes" else "$value seconds"

    private fun formatDate(context: Context, isoDate: String): String {
        return try {
            // Parse the UTC date string to timestamp
            val timestamp = TimeUtils.parseUTCDate(isoDate)
            if (timestamp == 0L) return isoDate

            // Format using device's locale and date format preferences
            TimeUtils.INSTANCE.renderUtcDate(timestamp, context, 2)
        } catch (e: Exception) {
            isoDate
        }
    }
}
