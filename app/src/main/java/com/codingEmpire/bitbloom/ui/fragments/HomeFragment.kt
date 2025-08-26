package com.codingEmpire.bitbloom.ui.fragments

/* ── new imports ─────────────────────────────────────────────────────────── */
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.support.annotation.RequiresApi
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.airbnb.lottie.LottieAnimationView
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.AnnouncementImageAdapter
import com.codingEmpire.bitbloom.databinding.FragmentHomeBinding
import com.codingEmpire.bitbloom.repos.DailyRewardRepo
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.utils.TourHelper
import com.codingEmpire.bitbloom.utils.TourTarget
import com.codingEmpire.bitbloom.viewModels.AccountViewModel
import com.codingEmpire.bitbloom.viewModels.LuckySpinViewModel
import com.codingEmpire.bitbloom.viewModels.WalletViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

private const val DIALOG_DIM = 0.65f

class HomeFragment : BaseFragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val walletViewModel: WalletViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()
    private val luckySpinVm: LuckySpinViewModel by viewModels()
    private val prefService: PrefService by lazy { PrefService(requireContext()) }
    private var pagerJob: Job? = null
    private val slideDelayMs = 7_000L
    private var slideForward = true
    private lateinit var pagerCb: ViewPager2.OnPageChangeCallback
    val PDF_URL =
        "https://firebasestorage.googleapis.com/v0/b/investment-app-11ac4.firebasestorage.app/o/bitbloom%20presentation%2FBIT%20BLOOM%20New%20Plan%20Presentation.pdf?alt=media&token=c89fb6c0-7e58-4093-93ad-93ef04a4ad14"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val navController = findNavController()
        val backStackEntry = navController.currentBackStackEntry
        val loginSuccess = backStackEntry?.savedStateHandle?.get<Boolean>("login_success") == true

        if (loginSuccess) {
            showSnackbar("Login successful!")
            backStackEntry?.savedStateHandle?.remove<Boolean>("login_success")
        }



        setupDrawerTrigger(view)
        setUpReward(view)
        setupNav()
        setupWalletObserver()

        accountViewModel.getAnnouncementImageUrls() // Load image URLs

        setupPager()
        observeAnnouncementImages()

        val imm =
            requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)


        // Always load on view creation
        walletViewModel.startListeningToWalletUpdates()
        accountViewModel.loadProfile()
        startTour()

        val userId = prefService.getUserId() ?: return
        decideAndLaunchDialogs()

        binding.walletCard.annoucementsNavigator.setOnClickListener {
            showPdfDownloadDialog(PDF_URL)
        }
        // Referral link field (static, no need to restore)
        val referralLink = "https://bitbloom.uk/?ref=$userId"
        binding.inputReferral.apply {
            setText(referralLink)
            isFocusable = false
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    compoundDrawablesRelative.getOrNull(2)?.let { drawableEnd ->
                        val touchStart = right - paddingEnd - drawableEnd.intrinsicWidth
                        if (event.rawX >= touchStart) {
                            val clipboard =
                                requireContext().getSystemService(ClipboardManager::class.java)
                            val clip = ClipData.newPlainText("Referral Link", referralLink)
                            clipboard.setPrimaryClip(clip)
                            showSnackbar("Link copied to clipboard")
                            v.performClick()
                            return@setOnTouchListener true
                        }
                    }
                }
                false
            }
        }

        // Referral code copy
        binding.textReferralCode.text = userId
        (binding.copyCodeLayout.parent as? View)?.setOnClickListener {
            val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
            val clip = ClipData.newPlainText("Referral Code", userId)
            clipboard.setPrimaryClip(clip)
            showSnackbar("Code copied to clipboard")
        }

    }

    private fun setupNav() {
        listOf(
            binding.teamScreenBtn to R.id.achievementsFragment,
            binding.leaderBoardScreenBtn to R.id.leaderboardFragment,
            binding.dashboardScreenBtn to R.id.dashboardFragment,
            binding.announcementScreenBtn to R.id.annoucementsFragment,
            binding.luckySpinScreenBtn to R.id.luckySpinFragment,
            binding.txnScreenBtn to R.id.transactionsFragment,
            binding.salaryScreenBtn to R.id.salaryFragment,
            binding.dailyRewardsScreenBtn to R.id.dailyRewardFragment,
            binding.supportScreenBtn to R.id.supportFragment,
            binding.walletCard.btnInvest to R.id.investmentPlansFragment,
            binding.walletCard.btnTransactions to R.id.transactionsFragment,
            binding.walletCard.tvDetails to R.id.teamLevelsFragment
        ).forEach { (viewBtn, destId) ->
            viewBtn.setOnClickListener { viewBtn.findNavController().navigate(destId) }
        }
    }

    private fun setupWalletObserver() {
        walletViewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                showLoading()
                binding.walletCard.tvBalance.text = "Loading..."
            } else hideLoading()
        }

        walletViewModel.error.observe(viewLifecycleOwner) { err ->
            err?.let { showSnackbar(it, true) }
        }

        walletViewModel.walletData.observe(viewLifecycleOwner) { data ->
            val balance = (data?.get("balance") as? Double) ?: 0.0
            val formatter = NumberFormat.getCurrencyInstance(Locale.CANADA).apply {
                currency = Currency.getInstance("CAD")
            }
            binding.walletCard.tvBalance.text = formatter.format(balance)
        }

        accountViewModel.profileData.observe(viewLifecycleOwner) { data ->
            binding.greetingTxt.text = "Hi, ${data["name"]}"
        }
    }

    // Optionally keep this for toast/snackbar feedback elsewhere
    private fun showSnackbar(message: String, isError: Boolean = false) {
        if (isError) SoundManager.playFailure(requireContext())
        val snack = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
        val bgColor = ContextCompat.getColor(
            requireContext(),
            if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.view.backgroundTintList = ColorStateList.valueOf(bgColor)
        snack.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snack.show()
    }

    private fun setupPager() = with(binding.imgAnnouncementPager) {
        offscreenPageLimit = 3
        clipToPadding = false
        clipChildren = false

        // ── show a bit of the neighbours (16dp each side) ──
        val px = resources.displayMetrics.density * 16
        setPadding(px.toInt(), 0, px.toInt(), 0)

        // ── subtle “gallery” effect ─────────────────────────
        setPageTransformer { page, position ->
            val scale = 0.86f + (1f - abs(position)) * 0.14f
            page.scaleY = scale
        }

        pagerCb = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> pagerJob?.cancel()     // user touched
                    ViewPager2.SCROLL_STATE_IDLE -> startAutoSlide()       // resume after drag
                }
            }
        }
        registerOnPageChangeCallback(pagerCb)
    }

    private fun observeAnnouncementImages() {
        accountViewModel.announcementImageUrls.observe(viewLifecycleOwner) { urls ->
            if (urls.isNullOrEmpty()) return@observe

            val vp = binding.imgAnnouncementPager
            val adapter = AnnouncementImageAdapter(urls)
            vp.adapter = adapter

            // jump to the middle of a huge “fake” list so looping looks endless
            vp.setCurrentItem(adapter.itemCount * 50, false)

            startAutoSlide()
        }
    }

    private fun showPdfDownloadDialog(pdfUrl: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_update, null).apply {
            findViewById<TextView>(R.id.updateTitle).text = "Download PDF"
            findViewById<TextView>(R.id.updateDesc).text = "Would you like to download this file?"
            findViewById<MaterialButton>(R.id.updateNowBtn).text = "Download"
            findViewById<View>(R.id.updateProgressContainer).visibility = View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create().apply {
                window?.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
                window?.setDimAmount(0.65f)
            }

        dialogView.findViewById<MaterialButton>(R.id.updateNowBtn).setOnClickListener { btn ->
            btn.isEnabled = false
            dialogView.findViewById<View>(R.id.updateProgressContainer).visibility = View.VISIBLE
            downloadPdfWithProgress(pdfUrl, dialogView, dialog)
        }

        dialog.show()
    }

    private fun downloadPdfWithProgress(
        url: String,
        dialogView: View,
        dialog: androidx.appcompat.app.AlertDialog
    ) {
        val progressBar = dialogView.findViewById<CircularProgressIndicator>(R.id.updateCircle)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading PDF")
            .setDescription("Please wait...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "bitbloom_doc.pdf")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)


        val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        lifecycleScope.launch(Dispatchers.IO) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            var downloading = true

            while (downloading) {
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloading = false
                            withContext(Dispatchers.Main) {
                                dialog.dismiss()
                                showSnackbar("PDF downloaded to Downloads folder.")
                            }
                        }

                        DownloadManager.STATUS_FAILED -> {
                            downloading = false
                            withContext(Dispatchers.Main) {
                                dialog.dismiss()
                                showSnackbar("Download failed.", isError = true)
                            }
                        }

                        DownloadManager.STATUS_RUNNING -> {
                            val total =
                                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val downloaded =
                                cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            if (total > 0) {
                                val progress = (downloaded * 100 / total).toInt()
                                withContext(Dispatchers.Main) {
                                    progressBar.isIndeterminate = false
                                    progressBar.setProgressCompat(progress, true)
                                }
                            }
                        }
                    }
                }
                cursor.close()
                delay(400)
            }
        }
    }

    private fun startAutoSlide() {
        pagerJob?.cancel()
        val itemCount = binding.imgAnnouncementPager.adapter?.itemCount ?: return
        if (itemCount < 2) return                // nothing to slide

        pagerJob = lifecycleScope.launchWhenResumed {
            while (isActive) {
                delay(slideDelayMs)

                val vp = binding.imgAnnouncementPager
                var next = vp.currentItem + if (slideForward) 1 else -1

                /* turn around at the ends */
                if (next == itemCount) {
                    slideForward = false; next = itemCount - 2
                }
                if (next < 0) {
                    slideForward = true; next = 1
                }

                vp.setCurrentItem(next, true)
            }
        }
    }

    private fun startTour() {
        TourHelper.startTour(
            fragment = this,
            tourKey = "home",
            targets = listOf(
                TourTarget(binding.greetingTxt, "Welcome", "Dashboard greeting"),
                TourTarget(
                    binding.walletCard.tvBalance,
                    "Your Balance",
                    "Your current wallet balance"
                ),
                TourTarget(
                    binding.walletCard.btnInvest,
                    "Invest Now",
                    "Start a new investment plan"
                ),
                TourTarget(
                    binding.walletCard.btnTransactions,
                    "Transactions",
                    "All your past deposits and earnings"
                ),
                TourTarget(
                    binding.walletCard.annoucementsNavigator,
                    "Announcements",
                    "Latest app updates"
                ),
                TourTarget(binding.textReferralCode, "Referral Code", "Share to earn more"),
                TourTarget(binding.menuIcon, "Menu", "Explore Key Features"),
                TourTarget(binding.notificationIcon, "Daily Rewards", "Get daily rewards"),
            ), onMessage = { message, isError ->
                showSnackbar(message, isError)
            }
        )

    }

    // ───────────────────────────────────────────────────────────────────────────
// HomeFragment.kt  –  put this in place of your current methods
// ───────────────────────────────────────────────────────────────────────────
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun maybeShowWelcomeDialog(onBonusClaimed: () -> Unit = {}) {
        val uid = prefService.getUserId() ?: return          // safety-net

        lifecycleScope.launch {
            // ── Bail-out if already claimed on the server ───────────────────
            val alreadyClaimed = FirebaseFirestore.getInstance()
                .collection("luckySpins")
                .document(uid)
                .get()
                .await()
                .getBoolean("welcomeRewardClaimed") == true
            if (alreadyClaimed) return@launch

            // ── Build the custom dialog view (progress container stays GONE) ─
            val dialogView = layoutInflater.inflate(R.layout.dialog_update, null).apply {
                findViewById<TextView>(R.id.updateTitle).text = "Welcome Bonus"
                findViewById<TextView>(R.id.updateDesc).text = "Collect your \$5 Lucky USDT!"
                findViewById<MaterialButton>(R.id.updateNowBtn).text = "Claim $5"
                findViewById<View>(R.id.updateProgressContainer).visibility = View.GONE
            }

            // ── Create the dialog (transparent bg, dimmed) ──────────────────
            val dialog = MaterialAlertDialogBuilder(
                requireContext(),
                com.google.android.material.R.style
                    .ThemeOverlay_Material3_MaterialAlertDialog_Centered
            )
                .setView(dialogView)
                .setCancelable(false)
                .create()
                .apply {
                    window?.setBackgroundDrawable(
                        android.graphics.Color.TRANSPARENT.toDrawable()
                    )
                    window?.setDimAmount(0.65f)
                }

            dialogView.findViewById<MaterialButton>(R.id.updateNowBtn).setOnClickListener { btn ->
                btn.isEnabled = false
                showLoading() // show the global loading overlay

                lifecycleScope.launch {
                    val result = runCatching {
                        luckySpinVm.claimWelcomeBonus(uid)
                    }

                    hideLoading()
                    (requireView() as ViewGroup).post {
                        if (result.isSuccess) {
                            dialog.dismiss()
                            playConfetti { onBonusClaimed() }   // ⬅ trigger next dialog here
                            showSnackbar("Bonus added to Lucky USDT!")
                        } else {
                            SoundManager.playFailure(requireContext())
                            showSnackbar("Couldn’t add bonus, please try again.", true)
                            btn.isEnabled = true
                        }
                    }
                }
            }
            dialog.show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun maybeShowStarterPackDialog() {
        val uid = prefService.getUserId() ?: return

        lifecycleScope.launch {
            if (DailyRewardRepo().hasClaimedStarterPack(uid)) return@launch

            val view = layoutInflater.inflate(R.layout.dialog_update, null).apply {
                findViewById<TextView>(R.id.updateTitle).text = "Bitbloom Starter Pack"
                findViewById<TextView>(R.id.updateDesc).text = "Kick-start your bitbloom journey"
                findViewById<MaterialButton>(R.id.updateNowBtn).text = "Claim 50 XBLM"
                findViewById<View>(R.id.updateProgressContainer).visibility = View.GONE
            }

            val dlg = MaterialAlertDialogBuilder(
                requireContext(),
                com.google.android.material.R.style
                    .ThemeOverlay_Material3_MaterialAlertDialog_Centered
            ).setView(view).setCancelable(false).create().apply {
                window?.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
                window?.setDimAmount(DIALOG_DIM)
            }

            view.findViewById<MaterialButton>(R.id.updateNowBtn).setOnClickListener { btn ->
                btn.isEnabled = false
                showLoading()

                lifecycleScope.launch {
                    val ok = runCatching {
                        luckySpinVm.claimStarterPack(uid)
                    }.isSuccess

                    hideLoading()
                    (requireView() as ViewGroup).post {
                        if (ok) {
                            dlg.dismiss()
                            SoundManager.playSuccess(requireContext())
                            playConfetti()
                            showSnackbar("50 tokens added to your balance!")
                        } else {
                            SoundManager.playFailure(requireContext())
                            showSnackbar("Couldn’t add tokens, please retry.", true)
                            btn.isEnabled = true
                        }
                    }
                }
            }
            dlg.show()
        }
    }

    private fun playConfetti(onEnd: () -> Unit = {}) {
        val root = requireView() as ViewGroup

        val lottie = LottieAnimationView(requireContext()).apply {
            setAnimation(R.raw.confetti)
            repeatCount = 0                    // play once, but JSON itself keeps looping
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(lottie)

        /* --- ①  play the sound right here --- */
        SoundManager.playSuccess(requireContext())

        lottie.playAnimation()

        /* --- ②  guarantee the callback after 2 s even if the JSON loops forever --- */
        lottie.postDelayed({
            root.removeView(lottie)
            onEnd()                            // fire the “next-dialog” callback
        }, 2000L)                              // ← length of your confetti (adjust if needed)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun decideAndLaunchDialogs() = lifecycleScope.launch {
        val uid = prefService.getUserId() ?: return@launch

        // read both flags in parallel
        val luckyDoc = FirebaseFirestore.getInstance()
            .collection("luckySpins").document(uid).get().await()
        val welcomeClaimed = luckyDoc.getBoolean("welcomeRewardClaimed") == true
        val tokensClaimed = DailyRewardRepo().hasClaimedStarterPack(uid)

        if (!welcomeClaimed) {
            // show USDT dialog and chain the tokens dialog afterwards
            maybeShowWelcomeDialog(
                onBonusClaimed = {                    // callback
                    if (!tokensClaimed) maybeShowStarterPackDialog()
                }
            )
        } else if (!tokensClaimed) {
            // USDT already done – go straight to tokens dialog
            maybeShowStarterPackDialog()
        }
    }

    override fun onPause() {
        super.onPause()
        pagerJob?.cancel()
    }

    override fun onDestroyView() {
        binding.imgAnnouncementPager.unregisterOnPageChangeCallback(pagerCb)
        pagerJob?.cancel()
        _binding = null
        walletViewModel.stopListeningToWalletUpdates()
        super.onDestroyView()
    }
}