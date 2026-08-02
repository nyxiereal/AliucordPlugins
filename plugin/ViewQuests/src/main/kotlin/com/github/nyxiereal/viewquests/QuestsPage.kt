package com.github.nyxiereal.viewquests

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import com.aliucord.utils.RxUtils.subscribe
import com.discord.utilities.captcha.CaptchaHelper
import com.discord.utilities.time.TimeUtils
import com.facebook.drawee.view.SimpleDraweeView
import com.lytefast.flexinput.R
import rx.Subscriber

class QuestsPage : SettingsPage() {
    private val logger = Logger("ViewQuests")

    private fun addCollectiblesButton(context: Context) {
        headerBar.menu.add("Collectibles").apply {
            icon = Utils.tintToTheme(context.getDrawable(R.e.ic_gift_24dp))
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                Utils.openPageWithProxy(context, CollectiblesPage())
                true
            }
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
                val questsResponse = QuestsApi.getQuests()

                // Update UI on main thread
                Utils.mainThread.post {
                    setActionBarTitle("Quests")

                    if (questsResponse.quests.isEmpty()) {
                        addNoQuestsView(context)
                    } else {
                        questsResponse.quests
                            .sortedWith(questComparator())
                            .forEach { quest -> addQuestCard(context, quest) }
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
        val cardContainer = createCard(context, 12)

        addHero(context, config, config.taskConfigV2 ?: config.taskConfig, cardContainer)

        createHeaderTextView(
            context,
            config.messages.questName,
            Padding(16, 14, 16, 2)
        ).apply { cardContainer.addView(this) }

        createSubTextView(
            context,
            "${config.messages.gameTitle}  •  Promoted by ${config.messages.gamePublisher}",
            Padding(16, 2, 16, 12)
        ).apply { cardContainer.addView(this) }

        addRewardSummary(context, config.rewardsConfig, cardContainer)
        addTaskSummary(context, config.taskConfigV2 ?: config.taskConfig, cardContainer)
        addStatusSummary(context, quest, cardContainer)
        linearLayout.addView(cardContainer)
    }

    private fun addHero(
        context: Context,
        config: QuestConfig,
        taskConfig: QuestTaskConfig?,
        container: LinearLayout
    ) {
        val assets = config.assets ?: return
        val heroAsset = assets.questBarHero ?: assets.hero ?: return
        val heroHeight = DimenUtils.dpToPx(156)
        val hero = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                heroHeight
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#232428"))
                cornerRadius = DimenUtils.dpToPx(8).toFloat()
            }
            clipToOutline = true
        }

        val artwork = SimpleDraweeView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setImageURI(questAssetUrl(heroAsset))
        }
        hero.addView(artwork)

        hero.addView(
            View(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.TRANSPARENT, Color.argb(220, 20, 21, 24))
                )
            }
        )

        val tasks = taskConfig?.tasks.orEmpty()
        if (tasks.isNotEmpty()) {
            val platformRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            tasks.keys.distinct().forEach { taskType ->
                platformRow.addView(createPlatformChip(context, taskType))
            }
            hero.addView(
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(platformRow)
                    layoutParams = FrameLayout
                        .LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP or Gravity.END
                        ).apply {
                            setMargins(
                                DimenUtils.dpToPx(12),
                                DimenUtils.dpToPx(10),
                                DimenUtils.dpToPx(8),
                                0
                            )
                        }
                }
            )
        }

        val logoAsset = assets.logotypeDark ?: assets.logotypeLight
        if (logoAsset != null) {
            hero.addView(
                SimpleDraweeView(context).apply {
                    layoutParams = FrameLayout
                        .LayoutParams(
                            DimenUtils.dpToPx(132),
                            DimenUtils.dpToPx(56),
                            Gravity.BOTTOM or Gravity.START
                        ).apply {
                            setMargins(
                                DimenUtils.dpToPx(14),
                                0,
                                0,
                                DimenUtils.dpToPx(10)
                            )
                        }
                    setImageURI(questAssetUrl(logoAsset))
                }
            )
        }

        hero.addView(
            createSubTextView(
                context,
                "Ends ${formatDate(context, config.expiresAt)}",
                Padding(8, 4, 8, 4),
                Color.WHITE
            ).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.argb(170, 20, 21, 24))
                    cornerRadius = DimenUtils.dpToPx(10).toFloat()
                }
                layoutParams = FrameLayout
                    .LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.END
                    ).apply {
                        setMargins(0, 0, DimenUtils.dpToPx(12), DimenUtils.dpToPx(12))
                    }
            }
        )

        container.addView(hero)
    }

    private fun addRewardSummary(
        context: Context,
        rewardsConfig: QuestRewardsConfig,
        container: LinearLayout
    ) {
        val rewards = rewardsConfig.rewards.map { reward ->
            reward.messages.nameWithArticle.removePrefix("a ").removePrefix("an ")
        }
        if (rewards.isEmpty()) return

        createLabelTextView(
            context,
            "Reward  •  ${rewards.joinToString("  •  ")}",
            Padding(16, 4, 16, 6)
        ).apply {
            setCompoundDrawablesWithIntrinsicBounds(
                Utils.tintToTheme(context.getDrawable(R.e.ic_gift_24dp)),
                null,
                null,
                null
            )
            compoundDrawablePadding = DimenUtils.dpToPx(8)
            container.addView(this)
        }
    }

    private fun addTaskSummary(
        context: Context,
        taskConfig: QuestTaskConfig?,
        container: LinearLayout
    ) {
        val tasks = taskConfig?.tasks.orEmpty()
        if (tasks.isEmpty()) return
        val descriptions = tasks
            .map { (taskType, task) ->
                getTaskDescription(taskType, task)
            }.distinct()

        createSubTextView(
            context,
            descriptions.joinToString(if (taskConfig?.joinOperator == "and") "\n" else "  •  "),
            Padding(16, 4, 16, 8)
        ).apply { container.addView(this) }
    }

    private fun createPlatformChip(context: Context, taskType: String): TextView {
        val (label, icon) = when (taskType) {
            "WATCH_VIDEO" -> {
                "Video" to R.e.ic_videocam_white_24dp
            }

            "WATCH_VIDEO_ON_MOBILE" -> {
                "Mobile" to R.e.ic_phone_24dp
            }

            "PLAY_ON_DESKTOP", "PLAY_ON_DESKTOP_V2", "STREAM_ON_DESKTOP" -> {
                "Desktop" to R.e.ic_monitor_white_24dp
            }

            "PLAY_ON_XBOX" -> {
                "Xbox" to R.e.ic_account_xbox_white_24dp
            }

            "PLAY_ON_PLAYSTATION" -> {
                "PlayStation" to R.e.ic_account_playstation_white_24dp
            }

            else -> {
                "Activity" to R.e.ic_controller_24dp
            }
        }
        return createSubTextView(
            context,
            label,
            Padding(8, 5, 8, 5),
            Color.parseColor("#DBDEE1")
        ).apply {
            gravity = Gravity.CENTER
            setCompoundDrawablesWithIntrinsicBounds(
                Utils.tintToTheme(context.getDrawable(icon)),
                null,
                null,
                null
            )
            compoundDrawablePadding = DimenUtils.dpToPx(4)
            background = GradientDrawable().apply {
                setColor(Color.argb(205, 35, 36, 40))
                cornerRadius = DimenUtils.dpToPx(12).toFloat()
            }
            layoutParams = LinearLayout
                .LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = DimenUtils.dpToPx(6) }
        }
    }

    private fun addStatusSummary(context: Context, quest: Quest, container: LinearLayout) {
        val statusView = createSubTextView(
            context,
            "",
            Padding(16, 4, 16, 2),
            Color.parseColor("#B5BAC1")
        ).apply {
            typeface = getCachedFont(context, com.aliucord.Constants.Fonts.whitney_semibold)
            container.addView(this)
        }
        val actionView = createLabelTextView(
            context,
            "",
            Padding(16, 10, 16, 10)
        ).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#5865F2"))
                cornerRadius = DimenUtils.dpToPx(6).toFloat()
            }
            layoutParams = LinearLayout
                .LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(
                        DimenUtils.dpToPx(16),
                        DimenUtils.dpToPx(8),
                        DimenUtils.dpToPx(16),
                        0
                    )
                }
            container.addView(this)
        }

        fun updateViews(status: QuestUserStatus?) {
            val (label, color) = when {
                status?.claimedAt != null -> "Reward claimed" to Color.parseColor("#57F287")
                status?.completedAt != null -> "Ready to claim" to Color.parseColor("#FEE75C")
                status?.enrolledAt != null -> "In progress" to Color.parseColor("#5865F2")
                else -> "Available" to Color.parseColor("#B5BAC1")
            }
            statusView.text = "●  $label"
            statusView.setTextColor(color)

            val videoTask = quest.findVideoTask()
            when {
                status?.claimedAt != null -> {
                    actionView.visibility = View.GONE
                }

                status?.completedAt != null -> {
                    actionView.visibility = View.VISIBLE
                    actionView.text = "Claim reward"
                }

                videoTask != null -> {
                    actionView.visibility = View.VISIBLE
                    actionView.text = if (status?.enrolledAt == null) {
                        "Accept & watch"
                    } else {
                        "Continue watching"
                    }
                }

                else -> {
                    actionView.visibility = View.GONE
                }
            }
            actionView.isEnabled = true
            actionView.alpha = 1f
        }

        fun claimReward(captchaSolution: QuestCaptchaSolution? = null) {
            actionView.isEnabled = false
            actionView.alpha = 0.6f
            actionView.text = if (captchaSolution == null) "Claiming..." else "Verifying..."
            Utils.threadPool.execute {
                try {
                    val updated = QuestsApi.claimReward(quest, captchaSolution)
                    quest.userStatus = updated
                    Utils.mainThread.post {
                        updateViews(updated)
                        Utils.showToast("Reward claimed")
                    }
                } catch (e: Exception) {
                    logger.error("Failed to claim quest reward", e)
                    val challenge = (e as? QuestApiException)?.captchaChallenge
                    if (challenge != null && captchaSolution == null) {
                        Utils.mainThread.post {
                            actionView.text = "Complete captcha..."
                            val request = CaptchaHelper.CaptchaRequest.HCaptcha(
                                challenge.siteKey,
                                Utils.appActivity,
                                challenge.rqdata
                            )
                            CaptchaHelper.INSTANCE.tryShowCaptcha(request).subscribe(
                                object : Subscriber<String>() {
                                    override fun onNext(token: String) {
                                        claimReward(
                                            QuestCaptchaSolution(
                                                token,
                                                challenge.rqtoken,
                                                challenge.sessionId
                                            )
                                        )
                                    }

                                    override fun onError(error: Throwable) {
                                        logger.error("Quest captcha failed", error)
                                        updateViews(quest.userStatus)
                                        Utils.showToast(
                                            error.message ?: "Captcha failed",
                                            true
                                        )
                                    }

                                    override fun onCompleted() {}
                                }
                            )
                        }
                    } else {
                        Utils.mainThread.post {
                            updateViews(quest.userStatus)
                            Utils.showToast(e.message ?: "Failed to claim reward", true)
                        }
                    }
                }
            }
        }

        actionView.setOnClickListener {
            val status = quest.userStatus
            if (status?.completedAt != null && status.claimedAt == null) {
                claimReward()
            } else {
                val videoTask = quest.findVideoTask() ?: return@setOnClickListener
                Utils.openPageWithProxy(
                    context,
                    QuestVideoPage(quest, videoTask.first, videoTask.second) { updated ->
                        quest.userStatus = updated
                        updateViews(updated)
                    }
                )
            }
        }
        updateViews(quest.userStatus)
    }

    private fun getTaskDescription(taskType: String, task: QuestTask): String {
        val type = task.type ?: task.eventName ?: taskType
        return when (type) {
            "STREAM_ON_DESKTOP" -> {
                "Stream for ${formatDuration(task.target)}"
            }

            "PLAY_ON_DESKTOP", "PLAY_ON_DESKTOP_V2" -> {
                "Play for ${formatDuration(task.target)}"
            }

            "PLAY_ON_XBOX" -> {
                "Play on Xbox for ${formatDuration(task.target)}"
            }

            "PLAY_ON_PLAYSTATION" -> {
                "Play on PlayStation for ${formatDuration(task.target)}"
            }

            "WATCH_VIDEO" -> {
                "Watch video for ${formatDuration(task.target)}"
            }

            "WATCH_VIDEO_ON_MOBILE" -> {
                "Watch video on mobile for ${formatDuration(task.target)}"
            }

            "PLAY_ACTIVITY" -> {
                "Play activity for ${formatDuration(task.target)}"
            }

            "ACHIEVEMENT_IN_ACTIVITY", "ACHIEVEMENT_IN_GAME" -> {
                task.messages?.taskTitle ?: task.title ?: "Complete achievement"
            }

            else -> {
                task.title ?: type
            }
        }
    }

    private fun questComparator(): Comparator<Quest> {
        val now = System.currentTimeMillis()
        return compareBy<Quest> { quest ->
            val expired = expirationTimestamp(quest) <= now
            when {
                expired || quest.userStatus?.claimedAt != null -> 3
                quest.userStatus?.completedAt != null -> 2
                quest.findVideoTask() != null -> 0
                else -> 1
            }
        }.thenBy(::expirationTimestamp)
    }

    private fun expirationTimestamp(quest: Quest): Long =
        runCatching { TimeUtils.parseUTCDate(quest.config.expiresAt) }
            .getOrDefault(Long.MAX_VALUE)
            .takeIf { it > 0 } ?: Long.MAX_VALUE

    private fun formatDuration(seconds: Int): String {
        if (seconds < 60) return "$seconds ${if (seconds == 1) "second" else "seconds"}"

        val minutes = seconds / 60
        return when {
            minutes < 60 -> {
                "$minutes ${if (minutes == 1) "minute" else "minutes"}"
            }

            else -> {
                val hours = minutes / 60
                val remainingMinutes = minutes % 60
                if (remainingMinutes > 0) {
                    "$hours ${if (hours == 1) "hour" else "hours"} " +
                        "$remainingMinutes ${if (remainingMinutes == 1) "minute" else "minutes"}"
                } else {
                    "$hours ${if (hours == 1) "hour" else "hours"}"
                }
            }
        }
    }

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