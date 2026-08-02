package com.github.nyxiereal.viewquests

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.aliucord.Http
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import com.aliucord.utils.SerializedName
import com.discord.utilities.time.TimeUtils
import com.facebook.drawee.view.SimpleDraweeView
import com.lytefast.flexinput.R

data class CollectiblePurchase(
    @SerializedName("sku_id") val skuId: String,
    val name: String,
    val summary: String,
    @SerializedName("store_listing_id") val storeListingId: String,
    @SerializedName("unpublished_at") val unpublishedAt: String? = null,
    val styles: CollectibleStyles? = null,
    val items: List<CollectibleItem>? = null,
    val type: Int,
    @SerializedName("premium_type") val premiumType: Int,
    @SerializedName("category_sku_id") val categorySkuId: String,
    @SerializedName("purchase_type") val purchaseType: Int,
    @SerializedName("purchased_at") val purchasedAt: String,
    @SerializedName("expires_at") val expiresAt: String? = null
)

data class CollectibleStyles(
    @SerializedName("background_colors") val backgroundColors: List<Int>? = null,
    @SerializedName("button_colors") val buttonColors: List<Int>? = null,
    @SerializedName("confetti_colors") val confettiColors: List<Int>? = null
)

data class CollectibleItem(
    val type: Int,
    val id: String,
    @SerializedName("sku_id") val skuId: String,
    val asset: String,
    val assets: CollectibleAssets? = null,
    val label: String
)

data class CollectibleAssets(
    @SerializedName("static_image_url") val staticImageUrl: String? = null,
    @SerializedName("animated_image_url") val animatedImageUrl: String? = null
)

class CollectiblesPage : SettingsPage() {
    private val logger = Logger("ViewQuests")

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Loading Collectibles...")

        val context = view.context

        // Fetch collectibles from Discord in a background thread
        Utils.threadPool.execute {
            try {
                val req = Http.Request.newDiscordRNRequest(
                    "/users/@me/collectibles-purchases",
                    "GET"
                )
                val res = req.execute()
                val collectibles = res.json(Array<CollectiblePurchase>::class.java).toList()

                // Update UI on main thread
                Utils.mainThread.post {
                    setActionBarTitle("Collectibles")

                    if (collectibles.isEmpty()) {
                        addNoCollectiblesView(context)
                    } else {
                        collectibles
                            .sortedByDescending { collectible ->
                                runCatching { TimeUtils.parseUTCDate(collectible.purchasedAt) }
                                    .getOrDefault(0L)
                            }.forEach { collectible ->
                                addCollectibleCard(context, collectible)
                            }
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to fetch collectibles", e)
                Utils.mainThread.post {
                    setActionBarTitle("Collectibles")
                    addErrorView(context, e.message ?: "Unknown error")
                }
            }
        }
    }

    private fun addNoCollectiblesView(context: Context) {
        createSubTextView(
            context,
            "No collectibles purchased yet",
            Padding(16, 32, 16, 32)
        ).apply {
            gravity = Gravity.CENTER
            linearLayout.addView(this)
        }
    }

    private fun addErrorView(context: Context, error: String) {
        createSubTextView(
            context,
            "Failed to load collectibles:\n$error",
            Padding(16, 32, 16, 32),
            Color.parseColor("#ED4245")
        ).apply {
            gravity = Gravity.CENTER
            linearLayout.addView(this)
        }
    }

    private fun addCollectibleCard(context: Context, collectible: CollectiblePurchase) {
        val cardContainer = createCard(context, 12)
        addCollectiblePreview(context, collectible, cardContainer)

        createHeaderTextView(
            context,
            collectible.name,
            Padding(16, 14, 16, 4)
        ).apply { cardContainer.addView(this) }

        createSubTextView(
            context,
            collectible.summary,
            Padding(16, 2, 16, 10)
        ).apply { cardContainer.addView(this) }

        createLabelTextView(
            context,
            "${getCollectibleType(
                collectible.type
            )}  •  ${getPurchaseType(collectible.purchaseType)}",
            Padding(16, 6, 16, 6)
        ).apply { cardContainer.addView(this) }

        collectible.items.orEmpty().forEach { item ->
            createSubTextView(
                context,
                item.label,
                Padding(16, 4, 16, 6)
            ).apply { cardContainer.addView(this) }
        }

        val expiry = collectible.expiresAt
            ?.let {
                " • Expires ${formatDate(
                    context,
                    it
                )}"
            }.orEmpty()
        createSubTextView(
            context,
            "Purchased ${formatDate(context, collectible.purchasedAt)}$expiry",
            Padding(16, 10, 16, 2),
            Color.parseColor("#B5BAC1")
        ).apply { cardContainer.addView(this) }

        linearLayout.addView(cardContainer)
    }

    private fun addCollectiblePreview(
        context: Context,
        collectible: CollectiblePurchase,
        container: LinearLayout
    ) {
        val colors = collectible.styles?.backgroundColors.orEmpty()
        val preview = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                DimenUtils.dpToPx(118)
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (colors.size >= 2) {
                    intArrayOf(colors[0].withOpaqueAlpha(), colors[1].withOpaqueAlpha())
                } else {
                    intArrayOf(Color.parseColor("#5865F2"), Color.parseColor("#3C45A5"))
                }
            ).apply { cornerRadius = DimenUtils.dpToPx(8).toFloat() }
            clipToOutline = true
        }

        val item = collectible.items?.firstOrNull()
        val imageUrl = item?.assets?.staticImageUrl
            ?: item?.assets?.animatedImageUrl
            ?: item?.asset?.takeIf { collectible.type == 0 }?.let {
                "https://cdn.discordapp.com/avatar-decoration-presets/$it.png?size=240&passthrough=true"
            }
        if (imageUrl != null) {
            preview.addView(
                SimpleDraweeView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        DimenUtils.dpToPx(104),
                        DimenUtils.dpToPx(104),
                        Gravity.CENTER
                    )
                    setImageURI(imageUrl)
                }
            )
        } else {
            preview.addView(
                ImageView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        DimenUtils.dpToPx(52),
                        DimenUtils.dpToPx(52),
                        Gravity.CENTER
                    )
                    setImageDrawable(context.getDrawable(R.e.ic_gift_24dp))
                    setColorFilter(Color.WHITE)
                    alpha = 0.9f
                }
            )
        }
        container.addView(preview)
    }

    private fun Int.withOpaqueAlpha(): Int = this or (0xFF shl 24)

    private fun getCollectibleType(type: Int): String = when (type) {
        0 -> "Avatar Decoration"
        1 -> "Profile Effect"
        2 -> "Bundle"
        3000 -> "Badge"
        else -> "Unknown ($type)"
    }

    private fun getPurchaseType(type: Int): String = when (type) {
        1 -> "Direct Purchase"
        10 -> "Quest Reward"
        else -> "Unknown Purchase Type"
    }
}