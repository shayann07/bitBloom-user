package com.codingEmpire.bitbloom.ui.fragments

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.codingEmpire.bitbloom.databinding.FragmentFaqsBinding
import com.google.android.material.textview.MaterialTextView

class FaqsFragment : BaseFragment() {
    private var _b: FragmentFaqsBinding? = null
    private val b get() = _b!!

    private val sections = listOf(
        "Login FAQs" to listOf(
            "How do I log in to my account?" to
                    "Enter your credentials on the login screen and tap “Log In.”",
            "What if I forgot my password?" to
                    "Tap “Forgot Password,” follow the email steps, then reset it.",
            "Why am I getting a login error?" to
                    "Double-check your email/password, ensure caps-lock is off, then retry."
        ),
        "Registration FAQs" to listOf(
            "How do I create an account?" to
                    "Tap “Register,” fill in the form, and submit.",
            "What info is required?" to
                    "Username, email, password, plus a couple personal details.",
            "How long does it take?" to
                    "Account creation is instant; email verification may take a few minutes."
        ),
        "General FAQs" to listOf(
            "Having login/registration issues?" to
                    "Contact support via our help desk or email us.",
            "How to keep my account secure?" to
                    "Use a strong, unique password and never share your credentials."
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = FragmentFaqsBinding.inflate(inflater, container, false).also { _b = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // enable the drawer menu
        setupDrawerTrigger(view)

        setUpReward(view)
        val container = b.faqContent
        val white = ContextCompat.getColor(requireContext(), android.R.color.white)
        val dividerColor = white and 0x33FFFFFF.toInt()  // 20% white

        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

        // Section title
        fun makeHeadline(text: String) = MaterialTextView(requireContext()).apply {
            setText(text)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Headline5)
            setTextColor(white)
            setPadding(dp(16), dp(24), dp(16), dp(12))
        }

        // Q&A item
        fun makeQA(q: String, a: String) = MaterialTextView(requireContext()).apply {
            val sb = SpannableStringBuilder()
            sb.append("Q: ").setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, 3, 0)
            sb.append(q).append("\n")
            val aStart = sb.length
            sb.append("A: ")
                .setSpan(StyleSpan(android.graphics.Typeface.BOLD), aStart, aStart + 3, 0)
            sb.append(a)
            text = sb

            setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2)
            setTextColor(white)
            setLineSpacing(0f, 1.3f)
            setPadding(dp(16), 0, dp(16), dp(16))
        }

        // Divider between sections
        fun makeDivider() = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1))
            setBackgroundColor(dividerColor)
        }

        // Build UI
        sections.forEachIndexed { idx, (title, qas) ->
            if (idx > 0) container.addView(makeDivider())
            container.addView(makeHeadline(title))
            qas.forEach { (q, a) -> container.addView(makeQA(q, a)) }
        }

        // Contact footer
        container.addView(makeDivider().apply { setPadding(0, dp(24), 0, 0) })
        container.addView(MaterialTextView(requireContext()).apply {
            text = "📧 For further assistance, email us at:"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2)
            setTextColor(white)
            setPadding(dp(16), dp(24), dp(16), dp(4))
        })
        container.addView(MaterialTextView(requireContext()).apply {
            text = "contact@bitbloom.uk"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2)
            setTextColor(white)
            paint.isUnderlineText = true
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(16), 0, dp(16), dp(40))
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
