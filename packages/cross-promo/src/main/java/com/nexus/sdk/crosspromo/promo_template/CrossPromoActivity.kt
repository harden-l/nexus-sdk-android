package com.nexus.sdk.crosspromo.promo_template

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.nexus.sdk.crosspromo.placement.CrossPromoProduct
import com.nexus.sdk.crosspromo.placement.CrossPromoSDK
import com.nexus.sdk.crosspromo.placement.ShowPromoPageOptions
import java.net.URL
import kotlin.concurrent.thread

class CrossPromoActivity : Activity() {
    private val primaryColor = Color.rgb(17, 106, 69)
    private val backgroundColor = Color.rgb(246, 249, 250)
    private val borderColor = Color.rgb(213, 222, 221)
    private val mutedTextColor = Color.rgb(91, 106, 103)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(loadingView())
        thread(name = "cross-promo-load-products") {
            runCatching {
                CrossPromoSDK.getProductsForDisplay(forceRefresh = true)
            }.onSuccess { products ->
                runOnUiThread { setContentView(contentView(products)) }
            }.onFailure { error ->
                runOnUiThread { setContentView(errorView(error)) }
            }
        }
    }

    private fun contentView(products: List<CrossPromoProduct>): View {
        val options = CrossPromoSDK.getActivePageOptions()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), statusBarHeight() + dp(10), dp(20), dp(24))
            setBackgroundColor(backgroundColor)
        }
        root.addView(header(options))
        if (products.isEmpty()) {
            root.addView(label("No recommended apps available.", 15f, mutedTextColor, top = 16))
        }
        products.forEach { product ->
            root.addView(productCard(product, options), fullWidthParams(top = 14))
        }
        return ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            addView(
                root, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun loadingView(): View {
        return pageWithMessage("Loading recommended apps...")
    }

    private fun errorView(error: Throwable): View {
        return pageWithMessage("Failed to load recommended apps.\n${error.message.orEmpty()}")
    }

    private fun pageWithMessage(message: String): View {
        val options = CrossPromoSDK.getActivePageOptions()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), statusBarHeight() + dp(10), dp(20), dp(24))
            setBackgroundColor(backgroundColor)
            addView(header(options))
            addView(label(message, 15f, mutedTextColor, top = 16))
        }
        return ScrollView(this).apply {
            setBackgroundColor(backgroundColor)
            addView(
                root, ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun header(options: ShowPromoPageOptions): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@CrossPromoActivity).apply {
                gravity = Gravity.END
                addView(TextView(this@CrossPromoActivity).apply {
                    text = "X"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(Color.rgb(13, 27, 24))
                    background = roundedBackground(Color.WHITE, Color.WHITE, dp(18))
                    setOnClickListener { finish() }
                }, LinearLayout.LayoutParams(dp(36), dp(36)))
            })
            addView(
                label(options.title, 28f, Color.rgb(13, 27, 24)).apply {
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(8), 0, 0)
                })
            options.description.takeIf { it.isNotBlank() }?.let { description ->
                addView(
                    descriptionCard(description), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, dp(14), 0, dp(16))
                    })
            }
        }
    }

    private fun descriptionCard(description: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = roundedBackground(Color.rgb(235, 246, 242), borderColor, dp(16))
            addView(
                label(description, 20f, mutedTextColor).apply {
                    setLineSpacing(dp(2).toFloat(), 1.0f)
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun productCard(product: CrossPromoProduct, options: ShowPromoPageOptions): View {
        val installed = CrossPromoSDK.isProductInstalled(product)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = roundedBackground(Color.WHITE, borderColor, dp(16))
            addView(productIcon(product), LinearLayout.LayoutParams(dp(64), dp(64)).apply {
                setMargins(0, 0, dp(14), 0)
            })
            addView(LinearLayout(this@CrossPromoActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(LinearLayout(this@CrossPromoActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label(product.title, 18f, Color.rgb(14, 25, 23)).apply {
                        typeface = Typeface.DEFAULT_BOLD
                    })
                })
                product.description.takeIf { it.isNotBlank() }?.let {
                    addView(label(it, 14f, mutedTextColor, top = 4))
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(this@CrossPromoActivity).apply {
                text = if (installed) "Open" else "Install"
                isAllCaps = false
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = roundedBackground(primaryColor, primaryColor, dp(18))
                setOnClickListener {
                    CrossPromoSDK.openProduct(product, options.placement, options.campaign)
                }
            }, LinearLayout.LayoutParams(dp(92), dp(44)).apply {
                setMargins(dp(12), 0, 0, 0)
            })
        }
    }

    private fun statusBadge(installed: Boolean): TextView {
        return TextView(this).apply {
            text = if (installed) "Installed" else "Not installed"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(if (installed) primaryColor else mutedTextColor)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = roundedBackground(
                if (installed) Color.rgb(224, 241, 236) else Color.rgb(242, 245, 244),
                if (installed) Color.rgb(224, 241, 236) else borderColor,
                dp(12)
            )
        }
    }

    private fun productIcon(product: CrossPromoProduct): View {
        val placeholder = TextView(this).apply {
            text = product.title.firstOrNull()?.uppercase().orEmpty()
            textSize = 24f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(primaryColor)
            background =
                roundedBackground(Color.rgb(224, 241, 236), Color.rgb(224, 241, 236), dp(16))
        }
        if (product.iconUrl.isBlank()) return placeholder
        return FrameLayout(this).apply {
            addView(
                placeholder, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            val image = ImageView(this@CrossPromoActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
            }
            addView(
                image, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            thread(name = "cross-promo-icon") {
                val bitmap = runCatching {
                    URL(product.iconUrl).openStream().use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
                if (bitmap != null) {
                    runOnUiThread {
                        image.setImageBitmap(bitmap)
                        image.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun label(textValue: String, size: Float, color: Int, top: Int = 0): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            setPadding(0, dp(top), 0, 0)
        }
    }

    private fun roundedBackground(fillColor: Int, strokeColor: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillColor)
            cornerRadius = radius.toFloat()
            setStroke(dp(1), strokeColor)
        }
    }

    private fun fullWidthParams(top: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(top)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }
}
