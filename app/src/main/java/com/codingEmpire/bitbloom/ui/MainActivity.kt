package com.codingEmpire.bitbloom.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.databinding.ActivityMainBinding
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.RemoteUpdateManager
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.AccountViewModel
import com.google.android.gms.security.ProviderInstaller
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    internal lateinit var viewModel: AccountViewModel
    private lateinit var updater: RemoteUpdateManager
    private var lastImageUrl: String? = null

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            showSnackbar("Notification permission denied", isError = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            try {
                ProviderInstaller.installIfNeeded(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        loadProfileImageFromLocalOrRemote(PrefService(this).getUserId().orEmpty())
        setupSocialLinks()

        updater = RemoteUpdateManager(this).also { it.clearFlagsIfUpdated() }
        SoundManager.init(this)
        viewModel = ViewModelProvider(this)[AccountViewModel::class.java]

        // Initial observer (called once)
        viewModel.profileData.observe(this) { profile ->
            bindDrawerProfile(profile)
        }

        // Initial load
        viewModel.loadProfile()


        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController
        val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
        navGraph.setStartDestination(
            if (PrefService(this).checkLogin()) R.id.homeFragment
            else R.id.loginFragment
        )
        navController.graph = navGraph

        binding.bottomNavBar.setOnItemSelectedListener { item ->
            val navOptions = androidx.navigation.NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.graph.startDestinationId, false)
                .build()

            val currentId = navController.currentDestination?.id
            if (currentId != item.itemId) {
                navController.navigate(item.itemId, null, navOptions)
            }
            true
        }

        val showFor = setOf(
            R.id.homeFragment,
            R.id.walletFragment,
            R.id.plansFragment,
            R.id.teamLevelsFragment,
            R.id.profileFragment
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Update visibility
            binding.bottomNavBar.visibility =
                if (destination.id in showFor) View.VISIBLE else View.GONE

            // Sync selected item in nav bar
            binding.bottomNavBar.menu.findItem(destination.id)?.isChecked = true
        }


        mapOf(
            R.id.menuHome to R.id.homeFragment,
            R.id.menuDeposit to R.id.depositFragment,
            R.id.menuWithdraw to R.id.withdrawFragment,
            R.id.menuProfile to R.id.profileFragment,
            R.id.menuDashboard to R.id.dashboardFragment,
            R.id.menuPrivacyPolicy to R.id.privacyPolicyFragment,
            R.id.menuFAQs to R.id.faqsFragment,
            R.id.menuTickets to R.id.supportTicketsFragment

        ).forEach { (menuId, destId) ->
            binding.navigationView.findViewById<View>(menuId).setOnClickListener {
                navController.navigate(destId)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            }
        }
        binding.navigationView.findViewById<View>(R.id.logoutOpt).setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            PrefService.clearAllPrefs(this)
            viewModel.clear()

            val navController = (supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment).navController

            val navOptions = androidx.navigation.NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true) // Replace with your actual root graph ID
                .build()

            navController.navigate(R.id.loginFragment, null, navOptions)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                if (binding.bottomNavBar.visibility == View.VISIBLE) {
                    binding.bottomNavBar.alpha = 1f - slideOffset
                }
                binding.navHostFragment.translationX = drawerView.width * slideOffset
            }

            override fun onDrawerOpened(drawerView: View) {
                binding.navHostFragment.animate()
                    .translationX(drawerView.width.toFloat())
                    .setDuration(200)
                    .start()

                // Force reload profile every time drawer is opened
                viewModel.loadProfile()
                // Always recheck in case image was updated during session
                val userId = PrefService(this@MainActivity).getUserId().orEmpty()
                loadProfileImageFromLocalOrRemote(userId)

            }

            override fun onDrawerClosed(drawerView: View) {
                binding.bottomNavBar.apply {
                    alpha = 1f
                    visibility =
                        if (navController.currentDestination?.id in showFor) View.VISIBLE else View.GONE
                }
            }

            override fun onDrawerStateChanged(newState: Int) {}
        })
    }

    override fun onResume() {
        super.onResume()
        updater.checkForUpdate()
    }

    fun openDrawer() = binding.drawerLayout.openDrawer(GravityCompat.START)

    private fun showSnackbar(message: String, isError: Boolean = false) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).apply {
            view.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    this@MainActivity,
                    if (isError) R.color.snackbar_error else R.color.snackbar_success
                )
            )
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            show()
        }
    }

    private fun bindDrawerProfile(profile: Map<String, Any?>?) {
        if (profile == null) {
            binding.customDrawerHeader.userNameTextView.text = ""
            binding.customDrawerHeader.userEmailTextView.text = ""
            binding.customDrawerHeader.drawerImageView.setImageResource(R.drawable.ic_profile)
            return
        }

        binding.customDrawerHeader.userNameTextView.text =
            profile["name"]?.toString().orEmpty()
        binding.customDrawerHeader.userEmailTextView.text =
            profile["email"]?.toString().orEmpty()


    }

    private fun loadProfileImageFromLocalOrRemote(uid: String) {
        val prefService = PrefService(this)
        val cachedUrl = prefService.getProfileImageUrl()

        // If already loaded, skip
        if (cachedUrl != null && cachedUrl == lastImageUrl) {
            return
        }

        if (!cachedUrl.isNullOrBlank()) {
            // Load cached image first (fast, offline)
            Glide.with(this)
                .load(cachedUrl)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .placeholder(R.drawable.ic_profile)
                .error(R.drawable.ic_profile)
                .circleCrop()
                .into(binding.customDrawerHeader.drawerImageView)
        }

        // Fetch latest from Firebase (for updated picture)
        FirebaseStorage.getInstance().reference
            .child("profile_pics/$uid.jpg")
            .downloadUrl
            .addOnSuccessListener { uri ->
                val url = uri.toString()
                if (url != lastImageUrl) {
                    lastImageUrl = url
                    prefService.saveProfileImageUrl(url)
                    Glide.with(this)
                        .load(url)
                        .circleCrop()
                        .placeholder(R.drawable.ic_profile)
                        .error(R.drawable.ic_profile)
                        .into(binding.customDrawerHeader.drawerImageView)
                }
            }
            .addOnFailureListener {
                // fallback image if not found
                binding.customDrawerHeader.drawerImageView.setImageResource(R.drawable.ic_profile)
            }
    }

    // ─── 2. helper that tries the app first, then falls back to browser ───
    private fun openLink(packageName: String, appUri: String, webUri: String = appUri) {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(appUri)).setPackage(packageName)
        try {
            startActivity(appIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUri)))
        }
    }

    // ─── 3. one-stop wiring for the three buttons ─────────────────
    private fun setupSocialLinks() {
        /* WhatsApp channel */
        binding.navigationView.findViewById<View>(R.id.btnWhatsApp).setOnClickListener {
            openLink(
                packageName = "com.whatsapp",
                /* If the WhatsApp app is installed this link opens the channel directly;
                   otherwise the browser page loads. */
                appUri = "https://whatsapp.com/channel/0029Vb5ZfdOKQuJNBXCF890H"
            )
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        /* Telegram channel */
        binding.navigationView.findViewById<View>(R.id.btnTelegram).setOnClickListener {
            openLink(
                packageName = "org.telegram.messenger",
                appUri = "tg://resolve?domain=bitbloomuk",
                webUri = "https://t.me/bitbloomuk"
            )
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

}
