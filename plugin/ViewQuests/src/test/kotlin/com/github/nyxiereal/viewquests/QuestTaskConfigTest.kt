package com.github.nyxiereal.viewquests

import com.aliucord.utils.GsonUtils
import com.aliucord.utils.GsonUtils.fromJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestTaskConfigTest {
    @Test
    fun legacyConfigSelectsDesktopVideo() {
        val quest = quest(
            """"task_config": {"tasks": {
                "WATCH_VIDEO": {"event_name": "WATCH_VIDEO", "target": 60}
            }, "join_operator": "and"}"""
        )

        assertNull(quest.config.taskConfigV2)
        assertEquals("and", quest.config.taskConfig?.joinOperator)
        val (key, task) = requireNotNull(quest.findVideoTask())
        assertEquals("WATCH_VIDEO", key)
        assertEquals("WATCH_VIDEO", task.eventName)
        assertNull(task.type)
        assertEquals(60, task.target)
    }

    @Test
    fun v2ConfigWithoutLegacyOrEventNameSelectsMobileVideo() {
        val quest = quest(
            """"task_config_v2": {"tasks": {
                "WATCH_VIDEO_ON_MOBILE": {
                    "type": "WATCH_VIDEO_ON_MOBILE", "target": 90,
                    "assets": {"video_low_res": {"url": "https://cdn.discordapp.com/quests/video.mp4"}}
                }
            }}"""
        )

        assertNull(quest.config.taskConfig)
        val (key, task) = requireNotNull(quest.findVideoTask())
        assertEquals("WATCH_VIDEO_ON_MOBILE", key)
        assertEquals("WATCH_VIDEO_ON_MOBILE", task.type)
        assertNull(task.eventName)
        assertEquals(90, task.target)
        assertEquals("https://cdn.discordapp.com/quests/video.mp4", task.assets?.videoLowRes?.url)
    }

    @Test
    fun missingNullAndEmptyConfigsHaveNoTasksOrVideo() {
        val cases = listOf(
            "",
            """"task_config": null, "task_config_v2": null""",
            """"task_config": {}""",
            """"task_config": {"tasks": null}""",
            """"task_config": {"tasks": {}}""",
            """"task_config_v2": {}""",
            """"task_config_v2": {"tasks": null}""",
            """"task_config_v2": {"tasks": {}}"""
        )
        for (fields in cases) {
            val quest = quest(fields)
            val config = quest.config.taskConfigV2 ?: quest.config.taskConfig
            // Gson can leave even the non-null tasks property null; the page uses this safe access.
            assertTrue(fields, config?.tasks.orEmpty().isEmpty())
            assertNull(fields, quest.findVideoTask())
        }
    }

    @Test
    fun v2ConfigTakesPrecedenceOverLegacy() {
        val quest = quest(
            """"task_config": {"tasks": {"WATCH_VIDEO_ON_MOBILE": {"target": 60}}},
                "task_config_v2": {"tasks": {"WATCH_VIDEO": {"target": 90}}}"""
        )

        val (key, task) = requireNotNull(quest.findVideoTask())
        assertEquals("WATCH_VIDEO", key)
        assertEquals(90, task.target)
        assertSame(quest.config.taskConfigV2?.tasks?.get(key), task)
    }

    @Test
    fun presentButEmptyV2ConfigDoesNotFallBackToLegacy() {
        for (v2 in listOf("{}", """{"tasks": null}""", """{"tasks": {}}""")) {
            val quest = quest(
                """"task_config": {"tasks": {"WATCH_VIDEO": {"target": 60}}},
                    "task_config_v2": $v2"""
            )

            assertEquals(1, quest.config.taskConfig?.tasks?.size)
            assertNull(v2, quest.findVideoTask())
        }
    }

    @Test
    fun nullV2ConfigFallsBackToLegacy() {
        val quest = quest(
            """"task_config": {"tasks": {"WATCH_VIDEO": {"target": 60}}},
                "task_config_v2": null"""
        )

        val (key, task) = requireNotNull(quest.findVideoTask())
        assertEquals("WATCH_VIDEO", key)
        assertSame(quest.config.taskConfig?.tasks?.get(key), task)
    }

    @Test
    fun mobileVideoIsPreferredRegardlessOfTaskOrderOrMissingTypeFields() {
        for (field in listOf("task_config", "task_config_v2")) {
            val quest = quest(
                """"$field": {"tasks": {
                    "WATCH_VIDEO": {"target": 60},
                    "WATCH_VIDEO_ON_MOBILE": {"target": 90}
                }}"""
            )

            val (key, task) = requireNotNull(quest.findVideoTask())
            assertEquals(field, "WATCH_VIDEO_ON_MOBILE", key)
            assertEquals(90, task.target)
            assertNull(task.eventName)
            assertNull(task.type)
        }
    }

    @Test
    fun achievementTitleDeserializesFromMessagesWithoutEnablingVideo() {
        val quest = quest(
            """"task_config_v2": {"tasks": {
                "ACHIEVEMENT_IN_ACTIVITY": {
                    "type": "ACHIEVEMENT_IN_ACTIVITY", "target": 1,
                    "messages": {"task_title": "Finish a match"}
                }
            }}"""
        )

        val task = requireNotNull(quest.config.taskConfigV2?.tasks?.get("ACHIEVEMENT_IN_ACTIVITY"))
        assertEquals("ACHIEVEMENT_IN_ACTIVITY", task.type)
        assertNull(task.eventName)
        assertNull(task.title)
        assertEquals("Finish a match", task.messages?.taskTitle)
        assertNull(quest.findVideoTask())
    }

    private fun quest(taskConfigs: String): Quest = GsonUtils.gsonRestApi
        .fromJson(
            """{
            "quests": [{
                "id": "123456789012345678",
                "config": {
                    ${if (taskConfigs.isEmpty()) "" else "$taskConfigs,"}
                    "starts_at": "2026-01-01T00:00:00+00:00",
                    "expires_at": "2026-12-31T23:59:59+00:00",
                    "messages": {
                        "quest_name": "Example Quest",
                        "game_title": "Example Game",
                        "game_publisher": "Example Publisher"
                    },
                    "rewards_config": {
                        "rewards": [{"type": 4, "messages": {"name_with_article": "an avatar decoration"}}],
                        "platforms": [0]
                    }
                },
                "user_status": null
            }],
            "quest_enrollment_blocked_until": null
        }""",
            QuestsResponse::class.java
        ).quests
        .single()
}