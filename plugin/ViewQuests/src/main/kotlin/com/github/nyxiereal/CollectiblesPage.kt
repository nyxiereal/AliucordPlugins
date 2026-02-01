package com.github.nyxiereal

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.aliucord.Constants
import com.aliucord.Http
import com.aliucord.Logger
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import com.aliucord.utils.SerializedName
import com.discord.utilities.time.TimeUtils
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
                val req = Http.Request.newDiscordRNRequest("/users/@me/collectibles-purchases", "GET")
                val res = req.execute()
                val collectibles = res.json(Array<CollectiblePurchase>::class.java).toList()

                // Update UI on main thread
                Utils.mainThread.post {
                    setActionBarTitle("Collectibles")

                    if (collectibles.isEmpty()) {
                        addNoCollectiblesView(context)
                    } else {
                        collectibles.forEach { collectible -> addCollectibleCard(context, collectible) }
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
        TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).run {
            text = "No collectibles purchased yet"
            typeface = ResourcesCompat.getFont(context, Constants.Fonts.whitney_medium)
            gravity = Gravity.CENTER
            setPadding(
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(32),
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(32)
            )
            linearLayout.addView(this)
        }
    }

    private fun addErrorView(context: Context, error: String) {
        TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).run {
            text = "Failed to load collectibles:\n$error"
            typeface = ResourcesCompat.getFont(context, Constants.Fonts.whitney_medium)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#ED4245"))
            setPadding(
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(32),
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(32)
            )
            linearLayout.addView(this)
        }
    }

    private fun addCollectibleCard(context: Context, collectible: CollectiblePurchase) {
        val cardContainer =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background =
                            android.graphics.drawable.GradientDrawable().apply {
                                setColor(Color.parseColor("#2F3136"))
                                cornerRadius = DimenUtils.dpToPx(8).toFloat()
                            }
                    layoutParams =
                            LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    .apply {
                                        setMargins(
                                                DimenUtils.dpToPx(4),
                                                DimenUtils.dpToPx(2),
                                                DimenUtils.dpToPx(4),
                                                DimenUtils.dpToPx(2)
                                        )
                                    }
                    setPadding(0, 0, 0, DimenUtils.dpToPx(8))
                }

        // Title
        TextView(context, null, 0, R.i.UiKit_Settings_Item_Header).apply {
            text = collectible.name
            typeface = ResourcesCompat.getFont(context, Constants.Fonts.whitney_semibold)
            setPadding(
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(8)
            )
            cardContainer.addView(this)
        }

        // Summary
        TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
            text = collectible.summary
            setPadding(
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(4),
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(12)
            )
            cardContainer.addView(this)
        }

        // Type information
        addTypeInfo(context, collectible, cardContainer)

        // Items
        if (!collectible.items.isNullOrEmpty()) {
            addItemsInfo(context, collectible.items, cardContainer)
        }

        // Purchase and expiry dates
        addDateInfo(context, collectible.purchasedAt, collectible.expiresAt, cardContainer)

        linearLayout.addView(cardContainer)
    }

    private fun addTypeInfo(
            context: Context,
            collectible: CollectiblePurchase,
            container: LinearLayout
    ) {
        TextView(context, null, 0, R.i.UiKit_Settings_Item_Label).apply {
            text = "Type: ${getCollectibleType(collectible.type)} • ${getPurchaseType(collectible.purchaseType)}"
            typeface = ResourcesCompat.getFont(context, Constants.Fonts.whitney_semibold)
            setPadding(
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(8),
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(4)
            )
            container.addView(this)
        }
    }

    private fun addItemsInfo(
            context: Context,
            items: List<CollectibleItem>,
            container: LinearLayout
    ) {
        if (items.isNotEmpty()) {
            TextView(context, null, 0, R.i.UiKit_Settings_Item_Label).apply {
                text = "Items:"
                typeface = ResourcesCompat.getFont(context, Constants.Fonts.whitney_semibold)
                setPadding(
                        DimenUtils.dpToPx(16),
                        DimenUtils.dpToPx(12),
                        DimenUtils.dpToPx(16),
                        DimenUtils.dpToPx(4)
                )
                container.addView(this)
            }

            items.forEach { item ->
                TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
                    text = "- ${item.label}"
                    setPadding(
                            DimenUtils.dpToPx(24),
                            DimenUtils.dpToPx(2),
                            DimenUtils.dpToPx(16),
                            DimenUtils.dpToPx(2)
                    )
                    container.addView(this)
                }
            }
        }
    }

    private fun addDateInfo(
            context: Context,
            purchasedAt: String,
            expiresAt: String?,
            container: LinearLayout
    ) {
        TextView(context, null, 0, R.i.UiKit_Settings_Item_SubText).apply {
            val expiryText = if (expiresAt != null) {
                "Expires: ${formatDate(context, expiresAt)}"
            } else {
                "Expires: Never"
            }
            text = "Purchased: ${formatDate(context, purchasedAt)}\n$expiryText"
            setPadding(
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(12),
                    DimenUtils.dpToPx(16),
                    DimenUtils.dpToPx(8)
            )
            container.addView(this)
        }
    }

    private fun getCollectibleType(type: Int): String =
            when (type) {
                0 -> "Avatar Decoration"
                1 -> "Profile Effect"
                2 -> "Bundle"
                3000 -> "Badge"
                else -> "Unknown ($type)"
            }

    private fun getPurchaseType(type: Int): String =
            when (type) {
                1 -> "Direct Purchase"
                10 -> "Quest Reward"
                else -> "Unknown Purchase Type"
            }

    private fun formatDate(context: Context, isoDate: String): String {
        return try {
            val timestamp = TimeUtils.parseUTCDate(isoDate)
            if (timestamp == 0L) return isoDate
            TimeUtils.INSTANCE.renderUtcDate(timestamp, context, 2)
        } catch (e: Exception) {
            isoDate
        }
    }
}
