package com.codingEmpire.bitbloom.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.codingEmpire.bitbloom.databinding.FragmentPrivacyPolicyBinding
import com.google.android.material.textview.MaterialTextView

class PrivacyPolicyFragment : BaseFragment() {
    private var _b: FragmentPrivacyPolicyBinding? = null
    private val b get() = _b!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = FragmentPrivacyPolicyBinding.inflate(inflater, container, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        setUpReward(view)

        val container = b.policyContent
        val white = ContextCompat.getColor(requireContext(), android.R.color.white)
        val dividerColor = white and 0x33FFFFFF.toInt() // 20% white

        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

        fun makeHeadline(text: String) = MaterialTextView(requireContext()).apply {
            setText(text)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline5)
            setTextColor(white)
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }

        fun makeParagraph(text: String) = MaterialTextView(requireContext()).apply {
            setText(text)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2)
            setTextColor(white)
            setLineSpacing(0f, 1.3f)
            setPadding(dp(16), 0, dp(16), dp(16))
        }

        fun makeBullet(text: String, indentDp: Int = 0) = MaterialTextView(requireContext()).apply {
            setText("• $text")
            setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2)
            setTextColor(white)
            setPadding(dp(16 + indentDp), 0, dp(16), dp(8))
        }

        fun makeDivider() = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1))
            setBackgroundColor(dividerColor)
        }

        // Intro
        container.addView(makeHeadline("Privacy Policy"))
        container.addView(
            makeParagraph(
                "At Bit Bloom, we are committed to protecting your personal and financial information. " +
                        "This policy explains how we collect, use, and safeguard your data."
            )
        )

        // Sections
        listOf(
            "1. Information Collection" to listOf(
                "Personal data such as name, email, and phone number.",
                "Financial data like investment details.",
                "Usage data including site visits and interactions."
            ),
            "2. Use of Data" to listOf(
                "We use your information to:",
                "Provide ROI analysis and financial consulting services.",
                "Communicate updates or offers related to our services.",
                "Analyze trends to improve user experience."
            ),
            "3. Data Protection" to listOf(
                "We apply industry-standard security protocols.",
                "Access is restricted to authorized personnel only."
            ),
            "4. Data Sharing" to listOf(
                "We may share your data with:",
                "Third-party analytics/hosting services.",
                "Regulatory authorities when required."
            ),
            "5. Your Rights" to listOf(
                "As a user, you can:",
                "Request a copy of your stored data.",
                "Ask for corrections or updates.",
                "Unsubscribe from marketing messages."
            ),
            "6. Changes to This Policy" to listOf(
                "We may revise this policy as needed.",
                "Updates will be posted on our official website."
            )
        ).forEachIndexed { idx, (heading, items) ->
            if (idx > 0) container.addView(makeDivider())
            container.addView(makeHeadline(heading))
            items.forEachIndexed { j, text ->
                makeBullet(text, if (j == 0) 0 else 16)
                    .also { container.addView(it) }
            }
        }

        // Contact
        container.addView(makeDivider().apply { setPadding(0, dp(24), 0, 0) })
        container.addView(makeHeadline("7. Contact Us"))
        container.addView(
            makeParagraph(
                "If you have any concerns or questions, feel free to contact us:"
            )
        )
        container.addView(MaterialTextView(requireContext()).apply {
            text = "contact@bitbloom.uk"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2)
            setTextColor(white)
            paint.isUnderlineText = true
            setPadding(dp(16), 0, dp(16), dp(40))
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_SENDTO, "mailto:contact@bitbloom.uk".toUri()))
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
