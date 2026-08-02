package com.github.nyxiereal.viewquests

import com.aliucord.Http
import com.aliucord.utils.GsonUtils
import com.aliucord.utils.SerializedName

data class QuestsResponse(
    val quests: List<Quest>,
    @SerializedName("quest_enrollment_blocked_until") val enrollmentBlockedUntil: String? = null
)

data class Quest(
    val id: String,
    val config: QuestConfig,
    @SerializedName("user_status") var userStatus: QuestUserStatus? = null,
    @SerializedName("traffic_metadata_raw") val trafficMetadataRaw: String? = null,
    @SerializedName("traffic_metadata_sealed") val trafficMetadataSealed: String? = null
)

data class QuestConfig(
    @SerializedName("starts_at") val startsAt: String,
    @SerializedName("expires_at") val expiresAt: String,
    val messages: QuestMessages,
    @SerializedName("task_config") val taskConfig: QuestTaskConfig? = null,
    @SerializedName("task_config_v2") val taskConfigV2: QuestTaskConfig? = null,
    @SerializedName("rewards_config") val rewardsConfig: QuestRewardsConfig,
    val assets: QuestAssets? = null
)

data class QuestAssets(
    val hero: String? = null,
    @SerializedName("quest_bar_hero") val questBarHero: String? = null,
    @SerializedName("logotype_light") val logotypeLight: String? = null,
    @SerializedName("logotype_dark") val logotypeDark: String? = null
)

data class QuestMessages(
    @SerializedName("quest_name") val questName: String,
    @SerializedName("game_title") val gameTitle: String,
    @SerializedName("game_publisher") val gamePublisher: String
)

data class QuestTaskConfig(
    val tasks: Map<String, QuestTask>,
    @SerializedName("join_operator") val joinOperator: String? = null
)

data class QuestTask(
    @SerializedName("event_name") val eventName: String? = null,
    val type: String? = null,
    val target: Int,
    val title: String? = null,
    val messages: QuestTaskMessages? = null,
    val assets: QuestTaskAssets? = null
)

data class QuestTaskAssets(
    val video: QuestMediaAsset? = null,
    @SerializedName("video_low_res") val videoLowRes: QuestMediaAsset? = null
)

data class QuestMediaAsset(val url: String)

data class QuestTaskMessages(
    @SerializedName("task_title") val taskTitle: String? = null
)

data class QuestRewardsConfig(
    val rewards: List<QuestReward>,
    val platforms: List<Int> = emptyList()
)

data class QuestReward(val type: Int, val messages: QuestRewardMessages)

data class QuestRewardMessages(
    @SerializedName("name_with_article") val nameWithArticle: String
)

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

fun Quest.findVideoTask(): Pair<String, QuestTask>? {
    val tasks = (config.taskConfigV2 ?: config.taskConfig)?.tasks.orEmpty()
    return listOf("WATCH_VIDEO_ON_MOBILE", "WATCH_VIDEO")
        .firstNotNullOfOrNull { type -> tasks[type]?.let { type to it } }
}

fun questAssetUrl(asset: String): String =
    if (asset.startsWith("http")) asset else "https://cdn.discordapp.com/${asset.removePrefix("/")}"

private data class QuestApiError(
    val message: String? = null,
    @SerializedName("captcha_key") val captchaKey: List<String>? = null,
    @SerializedName("captcha_sitekey") val captchaSiteKey: String? = null,
    @SerializedName("captcha_rqdata") val captchaRqdata: String? = null,
    @SerializedName("captcha_rqtoken") val captchaRqtoken: String? = null,
    @field:SerializedName("captcha_session_id") val captchaSessionId: String? = null
)

data class QuestCaptchaChallenge(
    val siteKey: String,
    val rqdata: String,
    val rqtoken: String,
    val sessionId: String
)

data class QuestCaptchaSolution(
    val key: String,
    val rqtoken: String,
    val sessionId: String
)

private data class EnrollQuestRequest(
    val location: Int = 12,
    @SerializedName("is_targeted") val isTargeted: Boolean = false,
    @SerializedName("metadata_sealed") val metadataSealed: String? = null,
    @SerializedName("traffic_metadata_raw") val trafficMetadataRaw: String? = null,
    @SerializedName("traffic_metadata_sealed") val trafficMetadataSealed: String? = null
)

private data class VideoProgressRequest(val timestamp: Double)

private data class ClaimRewardRequest(
    val platform: Int,
    val location: Int = 12,
    @SerializedName("is_targeted") val isTargeted: Boolean = false,
    @SerializedName("metadata_sealed") val metadataSealed: String? = null,
    @SerializedName("traffic_metadata_sealed") val trafficMetadataSealed: String? = null
)

class QuestApiException(
    val statusCode: Int,
    val captchaChallenge: QuestCaptchaChallenge?,
    message: String
) : Exception(message) {
    val captchaRequired: Boolean
        get() = captchaChallenge != null
}

object QuestsApi {
    fun getQuests(): QuestsResponse =
        Http.Request.newDiscordRNRequest("/quests/@me", "GET").execute().readJson()

    fun enroll(quest: Quest): QuestUserStatus {
        val body = EnrollQuestRequest(
            trafficMetadataRaw = quest.trafficMetadataRaw,
            trafficMetadataSealed = quest.trafficMetadataSealed
        )
        return post("/quests/${quest.id}/enroll", body)
    }

    fun reportVideoProgress(questId: String, timestamp: Double): QuestUserStatus {
        return post("/quests/$questId/video-progress", VideoProgressRequest(timestamp))
    }

    fun claimReward(quest: Quest, captchaSolution: QuestCaptchaSolution? = null): QuestUserStatus {
        val body = ClaimRewardRequest(
            platform = quest.config.rewardsConfig.platforms.firstOrNull() ?: 0,
            trafficMetadataSealed = quest.trafficMetadataSealed
        )
        val request = Http.Request.newDiscordRNRequest(
            "/quests/${quest.id}/claim-reward",
            "POST"
        )
        if (captchaSolution != null) {
            request
                .setHeader("x-captcha-key", captchaSolution.key)
                .setHeader("x-captcha-rqtoken", captchaSolution.rqtoken)
                .setHeader("x-captcha-session-id", captchaSolution.sessionId)
        }
        return request
            .executeWithJson(GsonUtils.gsonRestApi, body)
            .readJson()
    }

    private inline fun <reified T> post(path: String, body: Any): T = Http.Request
        .newDiscordRNRequest(path, "POST")
        .executeWithJson(GsonUtils.gsonRestApi, body)
        .readJson()

    private inline fun <reified T> Http.Response.readJson(): T = use { response ->
        if (!response.ok()) {
            val httpException = runCatching { response.assertOk() }.exceptionOrNull()
            val errorBody = httpException?.message?.substringAfter('\n', "").orEmpty()
            val error = runCatching {
                GsonUtils.fromJson(errorBody, QuestApiError::class.java)
            }.getOrNull()
            val challenge = error?.let {
                if (
                    !it.captchaKey.isNullOrEmpty() &&
                    it.captchaSiteKey != null &&
                    it.captchaRqdata != null &&
                    it.captchaRqtoken != null &&
                    it.captchaSessionId != null
                ) {
                    QuestCaptchaChallenge(
                        it.captchaSiteKey,
                        it.captchaRqdata,
                        it.captchaRqtoken,
                        it.captchaSessionId
                    )
                } else {
                    null
                }
            }
            val message = if (challenge != null) {
                "Discord requires a captcha"
            } else {
                error?.message ?: "Discord returned HTTP ${response.statusCode}"
            }
            throw QuestApiException(response.statusCode, challenge, message)
        }
        response.json(GsonUtils.gsonRestApi, T::class.java)
    }
}