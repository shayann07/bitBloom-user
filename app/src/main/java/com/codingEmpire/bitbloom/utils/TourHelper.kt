package com.codingEmpire.bitbloom.utils

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence

data class TourTarget(val view: View, val title: String, val description: String)

object TourHelper {

    fun startTour(
        fragment: Fragment,
        tourKey: String,
        targets: List<TourTarget>,
        onMessage: ((message: String, isError: Boolean) -> Unit)? = null
    ) {
        val ctx = fragment.requireContext()
        val activity = fragment.requireActivity()
        val pref = PersistentPrefService(ctx)


        if (pref.isTourCompleted(tourKey)) return

        var skipClicked = false
        var tapSequence: TapTargetSequence? = null

        val decor = activity.window.decorView as ViewGroup

        // Create Skip Button
        val skipButton = TextView(ctx).apply {
            text = "Skip Tour"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(24, 12, 24, 12)
            background = GradientDrawable().apply {
                cornerRadius = 100f
                setColor(Color.parseColor("#CC000000")) // semi-transparent black
                setStroke(2, Color.WHITE)
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END or Gravity.TOP
                topMargin = 120
                marginEnd = 32
            }
            isClickable = true
            isFocusable = true
        }

        val overlay = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.TRANSPARENT)
            addView(skipButton)
        }

        val tapTargets = targets.map {
            TapTarget.forView(it.view, it.title, it.description)
                .cancelable(false)
                .drawShadow(false)
                .transparentTarget(true)
                .tintTarget(true)
                .targetRadius(40)
                .outerCircleAlpha(0.85f)
                .textColorInt(Color.WHITE)
                .descriptionTextColorInt(Color.LTGRAY)
                .targetCircleColorInt(Color.WHITE)
                .outerCircleColorInt(Color.parseColor("#3758FC"))
                .textTypeface(Typeface.DEFAULT_BOLD)
        }

        var currentIndex = 0

        tapSequence = TapTargetSequence(activity).apply {
            targets(tapTargets)
            listener(object : TapTargetSequence.Listener {
                override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) {
                    if (currentIndex == 0 && targetClicked) {
                        skipButton.visibility = View.GONE
                    }
                    currentIndex++
                }

                override fun onSequenceFinish() {
                    if (!skipClicked) onMessage?.invoke("Tour complete!", false)
                    pref.setTourCompleted( tourKey)
                    decor.removeView(overlay)
                }

                override fun onSequenceCanceled(lastTarget: TapTarget) {
                    if (skipClicked) onMessage?.invoke("Tour skipped", false)
                    pref.setTourCompleted(tourKey)
                    decor.removeView(overlay)
                }
            })
        }


        // Skip logic
        skipButton.setOnClickListener {
            skipClicked = true
            pref.setTourCompleted(tourKey)
            onMessage?.invoke("Tour skipped", false)

            // Remove TapTarget overlays
            val toRemove = mutableListOf<View>()
            for (i in 0 until decor.childCount) {
                val child = decor.getChildAt(i)
                if (child.javaClass.name.contains("TapTargetView")) {
                    toRemove.add(child)
                }
            }
            toRemove.forEach { decor.removeView(it) }

            decor.removeView(overlay)
        }

        // Start tour and add overlay last to keep skip on top
        tapSequence.start()
        decor.post { decor.addView(overlay) }
    }
}
