package com.nexus.sdk.coreuser.email_bind

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.Patterns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

internal object CoreUserEmailBindDialog {
    fun show(
        activity: Activity,
        initialEmail: String?,
        onCancel: () -> Unit,
        onSubmit: (String, String) -> Unit
    ) {
        val input = EditText(activity).apply {
            hint = "please input your email"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(initialEmail.orEmpty())
            setSelection(text.length)
            textSize = 15f
            setPadding(activity.dp(14), 0, activity.dp(14), 0)
            minHeight = activity.dp(48)
            background = activity.roundedStroke(
                Color.WHITE,
                Color.rgb(209, 216, 224),
                activity.dp(1),
                activity.dp(8)
            )
        }

        val errorText = TextView(activity).apply {
            text = ""
            textSize = 12f
            setTextColor(Color.rgb(196, 57, 57))
            visibility = View.GONE
            setPadding(0, activity.dp(6), 0, 0)
        }

        val passwordInput = EditText(activity).apply {
            hint = "Set a password"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 15f
            setPadding(activity.dp(14), 0, activity.dp(14), 0)
            minHeight = activity.dp(48)
            background = activity.roundedStroke(
                Color.WHITE,
                Color.rgb(209, 216, 224),
                activity.dp(1),
                activity.dp(8)
            )
        }

        val dialog = Dialog(activity).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            setOnCancelListener { onCancel() }
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(24), activity.dp(24), activity.dp(32))
            background = activity.roundedFill(Color.WHITE, activity.dp(12))
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(activity).apply {
                text = "Bind Email"
                textSize = 20f
                setTextColor(Color.rgb(22, 28, 36))
                typeface = Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
//        header.addView(
//            TextView(activity).apply {
//                text = "X"
//                textSize = 16f
//                gravity = Gravity.CENTER
//                setTextColor(Color.rgb(96, 106, 118))
//                typeface = Typeface.DEFAULT_BOLD
//                background = activity.roundedFill(Color.rgb(244, 246, 248), activity.dp(18))
//                setOnClickListener { dialog.cancel() }
//            },
//            LinearLayout.LayoutParams(activity.dp(36), activity.dp(36))
//        )
        content.addView(header)
        content.addView(TextView(activity).apply {
            text = "Enter an email and set a password for future sign-in."
            textSize = 16f
            setTextColor(Color.rgb(96, 106, 118))
            setPadding(0, activity.dp(16), 0, activity.dp(16))
        })
        content.addView(
            input,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(48)).apply {
                setMargins(0, activity.dp(16), 0, activity.dp(0))
            }
        )
        content.addView(
            errorText,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        content.addView(
            passwordInput,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(48)).apply {
                setMargins(0, activity.dp(12), 0, 0)
            }
        )
        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(32), 0, 0)
        }
        actions.addView(
            activity.dialogButton("Cancel", primary = false) {
                dialog.cancel()
            },
            LinearLayout.LayoutParams(0, activity.dp(44), 1f).apply {
                setMargins(0, 0, activity.dp(10), 0)
            }
        )
        actions.addView(
            activity.dialogButton("Bind", primary = true) {
                val email = input.text.toString().trim()
                val password = passwordInput.text.toString()
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    errorText.text = "Please enter a valid email address."
                    errorText.visibility = View.VISIBLE
                    input.background = activity.roundedStroke(
                        Color.WHITE,
                        Color.rgb(196, 57, 57),
                        activity.dp(1),
                        activity.dp(16)
                    )
                    return@dialogButton
                }
                if (password.isBlank()) {
                    errorText.text = "Please enter a password."
                    errorText.visibility = View.VISIBLE
                    return@dialogButton
                }
                dialog.dismiss()
                onSubmit(email, password)
            },
            LinearLayout.LayoutParams(0, activity.dp(44), 1f)
        )
        content.addView(actions)

        input.setOnFocusChangeListener { _, _ ->
            errorText.visibility = View.GONE
            input.background = activity.roundedStroke(
                Color.WHITE,
                Color.rgb(209, 216, 224),
                activity.dp(1),
                activity.dp(8)
            )
        }

        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun Activity.dialogButton(text: String, primary: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 14f
            minHeight = 0
            minimumHeight = 0
            setTextColor(if (primary) Color.WHITE else Color.rgb(52, 64, 84))
            background = if (primary) {
                roundedFill(Color.rgb(17, 106, 69), dp(8))
            } else {
                roundedStroke(Color.rgb(248, 250, 252), Color.rgb(209, 216, 224), dp(1), dp(8))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun Activity.roundedFill(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun Activity.roundedStroke(
        fill: Int,
        stroke: Int,
        strokeWidth: Int,
        radius: Int
    ): GradientDrawable {
        return roundedFill(fill, radius).apply {
            setStroke(strokeWidth, stroke)
        }
    }

    private fun Activity.dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
