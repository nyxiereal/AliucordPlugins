package com.github.nyxiereal.viewquests

import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.TextView
import android.widget.VideoView
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import kotlin.math.min

class QuestVideoPage(
    private val quest: Quest,
    private val taskType: String,
    private val task: QuestTask,
    private val onStatusUpdated: (QuestUserStatus) -> Unit
) : SettingsPage() {
    private val logger = Logger("ViewQuests")
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusView: TextView
    private lateinit var videoView: VideoView
    private var active = true
    private var reporting = false
    private var videoCompleted = false
    private var lastReported = 0.0
    private val reportRunnable = Runnable { reportProgress() }

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle(quest.config.messages.questName)

        val context = view.context
        createHeaderTextView(
            context,
            "Watch to complete quest",
            Padding(16, 16, 16, 4)
        ).apply { linearLayout.addView(this) }
        createSubTextView(
            context,
            "Progress is reported from the video's actual playback position.",
            Padding(16, 2, 16, 12)
        ).apply { linearLayout.addView(this) }

        videoView = VideoView(context).apply {
            layoutParams = LinearLayout
                .LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    DimenUtils.dpToPx(230)
                ).apply {
                    setMargins(
                        DimenUtils.dpToPx(12),
                        0,
                        DimenUtils.dpToPx(12),
                        DimenUtils.dpToPx(12)
                    )
                }
        }
        linearLayout.addView(videoView)

        statusView = createLabelTextView(
            context,
            "Preparing quest...",
            Padding(16, 8, 16, 16)
        ).apply {
            gravity = Gravity.CENTER
            linearLayout.addView(this)
        }

        Utils.threadPool.execute {
            try {
                val status = quest.userStatus?.takeIf { it.enrolledAt != null }
                    ?: QuestsApi.enroll(quest)
                quest.userStatus = status
                Utils.mainThread.post {
                    if (!active) return@post
                    onStatusUpdated(status)
                    prepareVideo(status)
                }
            } catch (e: Exception) {
                showError("Failed to enroll in quest", e)
            }
        }
    }

    private fun prepareVideo(status: QuestUserStatus) {
        if (status.completedAt != null) {
            statusView.text = "Quest complete"
            statusView.setTextColor(Color.parseColor("#57F287"))
            videoView.visibility = View.GONE
            return
        }

        val media = task.assets?.videoLowRes ?: task.assets?.video
        if (media == null) {
            statusView.text = "This quest did not provide a playable video."
            statusView.setTextColor(Color.parseColor("#ED4245"))
            videoView.visibility = View.GONE
            return
        }

        lastReported = status.progress?.get(taskType)?.value?.toDouble() ?: 0.0
        val mediaController = MediaController(videoView.context).apply {
            setAnchorView(videoView)
        }
        videoView.setMediaController(mediaController)
        videoView.setVideoURI(Uri.parse(questAssetUrl(media.url)))
        videoView.setOnPreparedListener {
            if (!active) return@setOnPreparedListener
            videoView.seekTo((lastReported * 1000).toInt())
            videoView.start()
            updateProgress(lastReported.toInt())
            reportProgress()
        }
        videoView.setOnCompletionListener {
            videoCompleted = true
            scheduleReport()
        }
        videoView.setOnErrorListener { _, what, extra ->
            statusView.text = "Video playback failed ($what/$extra)"
            statusView.setTextColor(Color.parseColor("#ED4245"))
            true
        }
        statusView.text = "Loading video..."
        videoView.requestFocus()
    }

    private fun reportProgress() {
        if (!active) return
        if (reporting) return
        if (!videoCompleted && !videoView.isPlaying) {
            scheduleReport()
            return
        }

        val playbackPosition = if (videoCompleted) {
            task.target.toDouble()
        } else {
            min(task.target.toDouble(), videoView.currentPosition / 1000.0)
        }
        val timestamp = min(playbackPosition, lastReported + MAX_PROGRESS_STEP)
        if (timestamp < lastReported + MIN_PROGRESS_STEP && timestamp < task.target) {
            scheduleReport()
            return
        }

        reporting = true
        Utils.threadPool.execute {
            try {
                val updated = QuestsApi.reportVideoProgress(quest.id, timestamp)
                quest.userStatus = updated
                lastReported = maxOf(
                    timestamp,
                    updated.progress?.get(taskType)?.value?.toDouble() ?: 0.0
                )
                Utils.mainThread.post {
                    if (!active) return@post
                    reporting = false
                    onStatusUpdated(updated)
                    updateProgress(lastReported.toInt())
                    if (updated.completedAt != null) {
                        handler.removeCallbacks(reportRunnable)
                        statusView.text = "Quest complete"
                        statusView.setTextColor(Color.parseColor("#57F287"))
                        videoView.pause()
                    } else {
                        scheduleReport()
                    }
                }
            } catch (e: Exception) {
                reporting = false
                showError("Failed to report video progress", e)
            }
        }
    }

    private fun updateProgress(seconds: Int) {
        statusView.text = "Watched ${min(seconds, task.target)} / ${task.target} seconds"
        statusView.setTextColor(Color.parseColor("#DBDEE1"))
    }

    private fun scheduleReport() {
        handler.removeCallbacks(reportRunnable)
        handler.postDelayed(reportRunnable, REPORT_INTERVAL_MS)
    }

    private fun showError(prefix: String, error: Exception) {
        logger.error(prefix, error)
        Utils.mainThread.post {
            if (!active) return@post
            reporting = false
            statusView.text = "$prefix: ${error.message ?: "Unknown error"}"
            statusView.setTextColor(Color.parseColor("#ED4245"))
        }
    }

    override fun onDestroyView() {
        active = false
        handler.removeCallbacksAndMessages(null)
        videoView.stopPlayback()
        super.onDestroyView()
    }

    private companion object {
        const val REPORT_INTERVAL_MS = 7_500L
        const val MIN_PROGRESS_STEP = 6.0
        const val MAX_PROGRESS_STEP = 7.0
    }
}