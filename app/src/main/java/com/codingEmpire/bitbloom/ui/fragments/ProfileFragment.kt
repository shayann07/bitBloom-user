package com.codingEmpire.bitbloom.ui.fragments

import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.databinding.FragmentProfileBinding
import com.codingEmpire.bitbloom.repos.ProfileRepository
import com.codingEmpire.bitbloom.ui.MainActivity
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.TourHelper
import com.codingEmpire.bitbloom.utils.TourTarget
import com.codingEmpire.bitbloom.viewModels.ProfileViewModel
import com.codingEmpire.bitbloom.viewModels.ProfileViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Calendar

class ProfileFragment : BaseFragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val prefService by lazy { PrefService(requireContext()) }
    private val userCode by lazy { prefService.getUserId().orEmpty() }

    private var originalUserID = ""
    private var originalName = ""
    private var originalDob = ""
    private var originalPhone = ""
    private var referralCode = ""

    private val viewModel: ProfileViewModel by viewModels {
        ProfileViewModelFactory(
            ProfileRepository(FirebaseFirestore.getInstance()), userCode
        )
    }

    private val storage = FirebaseStorage.getInstance()
    private val PICK_IMAGE_REQUEST = 1001

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)
        setUpReward(view)

        // DOB picker
        binding.dobEt.apply {
            inputType = android.text.InputType.TYPE_NULL
            isFocusable = false
            setOnClickListener { showDatePicker() }
        }
        startTour()

        // Loading & error
        viewModel.isLoading.observe(viewLifecycleOwner) { if (it) showLoading() else hideLoading() }
        viewModel.error.observe(viewLifecycleOwner) { it?.let { msg -> showSnackbar(msg, true) } }

        // Profile data
        viewModel.profileData.observe(viewLifecycleOwner) { data ->
            data?.let {
                originalUserID = it["id"].toString()
                originalName = it["name"].toString()
                originalDob = it["dob"].toString()
                originalPhone = it["phoneNo"].toString()
                referralCode = it["referralCode"]?.toString().orEmpty()

                // Profile fields
                binding.profileName.text = originalName
                binding.nameET.apply {
                    setText(originalName)
                    isEnabled = true
                }

                binding.profileId.text = "Id : $originalUserID"

                binding.mailET.apply {
                    setText(it["email"].toString())
                    isEnabled = false
                }
                binding.dobEt.setText(originalDob)
                binding.phoneNoET.apply {
                    setText(originalPhone)
                    isEnabled = true
                }

                // Referral code: only show if non-empty
                if (referralCode.isBlank()) {
                    binding.referralId.visibility = View.GONE
                } else {
                    binding.referralId.apply {
                        visibility = View.VISIBLE
                        text = "Upline : $referralCode"
                    }
                }

                loadProfileImageFromLocalOrRemote(userCode)
            }
        }

        // Button actions
        binding.updateProfileBtn.setOnClickListener { onUpdateProfile() }
        binding.logoutBtn.setOnClickListener {
            showLoading()
            FirebaseAuth.getInstance().signOut()
            PrefService.clearAllPrefs(requireContext())
            hideLoading()

            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)  // change nav_graph to actual graph ID
                .build()
            (requireActivity() as MainActivity).viewModel.clear()
            findNavController().navigate(R.id.loginFragment, null, navOptions)
        }



        binding.profileImage.setOnClickListener {
            val imageUrl = prefService.getProfileImageUrl()
            if (!imageUrl.isNullOrBlank()) {
                showImageOptionsDialog(imageUrl)
            } else {
                pickImageFromGallery()
            }
        }

        binding.updatePasswordBtn.setOnClickListener {
            findNavController().navigate(R.id.newPasswordFragment)
        }

        // Always load fresh
        viewModel.loadProfile()
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        if (originalDob.contains("/")) {
            val parts = originalDob.split("/")
            if (parts.size == 3) {
                cal.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
            }
        }
        DatePickerDialog(
            requireContext(), { _, year, month, day ->
                binding.dobEt.setText(String.format("%02d/%02d/%04d", day, month + 1, year))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            show()
            window?.setDimAmount(0.65f)
        }
    }

    private fun onUpdateProfile() {
        val newName = binding.nameET.text.toString().trim()
        val newDob = binding.dobEt.text.toString().trim()
        val newPhone = binding.phoneNoET.text.toString().trim()

        val updates = mutableMapOf<String, Any>().apply {
            if (newName.isNotEmpty() && newName != originalName) put("name", newName)
            if (newDob.isNotEmpty() && newDob != originalDob) put("dob", newDob)
            if (newPhone.isNotEmpty() && newPhone != originalPhone) put("phoneNo", newPhone)
        }

        if (updates.isEmpty()) {
            showSnackbar("Nothing changed.", true)
            return
        }

        lifecycleScope.launch {
            showLoading()
            try {
                val snap =
                    FirebaseFirestore.getInstance().collection("users").whereEqualTo("id", userCode)
                        .get().await()

                if (snap.isEmpty) {
                    showSnackbar("Profile not found.", true)
                } else {
                    val docRef = snap.documents.first().reference
                    docRef.update(updates).await()

                    updates["name"]?.let {
                        binding.profileName.text = it.toString()
                        originalName = it.toString()
                    }
                    updates["dob"]?.let { originalDob = it.toString() }
                    updates["phoneNo"]?.let { originalPhone = it.toString() }

                    showSnackbar("Profile updated successfully!")
                }
            } catch (ex: Exception) {
                showSnackbar("Update failed. Try again.", true)
            } finally {
                hideLoading()
            }
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data?.data != null) {
            val sourceUri = data.data!!
            val destinationUri = Uri.fromFile(
                File(
                    requireContext().cacheDir, "cropped_${System.currentTimeMillis()}.jpg"
                )
            )

            val options = UCrop.Options().apply {
                setCircleDimmedLayer(true)
                setShowCropGrid(false)
                setHideBottomControls(true)
                setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.blue))
                setCompressionQuality(90) // reduce size, keep quality
            }

            UCrop.of(sourceUri, destinationUri).withAspectRatio(1f, 1f)
                .withMaxResultSize(1080, 1080).withOptions(options).start(requireContext(), this)

        } else if (requestCode == UCrop.REQUEST_CROP && resultCode == Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(data!!)
            if (resultUri != null) {
                uploadProfileImageToFirebase(resultUri)
            }
        } else if (requestCode == UCrop.REQUEST_CROP && resultCode == UCrop.RESULT_ERROR) {
            val error = UCrop.getError(data!!)
            showSnackbar("Crop failed: ${error?.message}", true)
        }
    }


    private fun uploadProfileImageToFirebase(uri: Uri) {
        showLoading()
        val ref = storage.reference.child("profile_pics/$userCode.jpg")
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                if (_binding != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(
                        Lifecycle.State.STARTED
                    )
                ) {
                    prefService.saveProfileImageUrl(url.toString())
                    Glide.with(this).load(url).circleCrop().placeholder(R.drawable.camera)
                        .into(binding.profileImage)
                    showSnackbar("Profile picture updated successfully.")
                }
                hideLoading()
            }
        }.addOnFailureListener {
            if (_binding != null) hideLoading()
            showSnackbar("Could not upload picture.", true)
        }
    }

    private fun loadProfileImageFromLocalOrRemote(uid: String) {
        val cachedUrl = prefService.getProfileImageUrl()

        // Load from cache first if available
        if (!cachedUrl.isNullOrBlank()) {
            Glide.with(this).load(cachedUrl).circleCrop().placeholder(R.drawable.camera)
                .error(R.drawable.camera).into(binding.profileImage)
        }

        // Always try to fetch the latest image in background
        storage.reference.child("profile_pics/$uid.jpg").downloadUrl.addOnSuccessListener { uri ->
            val url = uri.toString()
            // Avoid reloading the same image
            if (url != cachedUrl && _binding != null && viewLifecycleOwner.lifecycle.currentState.isAtLeast(
                    Lifecycle.State.STARTED
                )
            ) {
                prefService.saveProfileImageUrl(url)
                Glide.with(this).load(url).circleCrop().placeholder(R.drawable.camera)
                    .error(R.drawable.camera).into(binding.profileImage)
            }
        }.addOnFailureListener {
            // Optional: Show default image or toast if image doesn't exist
        }
    }

    private fun showImageOptionsDialog(imageUrl: String) {
        // inflate our custom view
        val view = layoutInflater.inflate(R.layout.dialog_options, null)
        val dlg = MaterialAlertDialogBuilder(
            requireContext(),
            com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered
        ).setView(view).create()

        // wire up clicks
        view.findViewById<TextView>(R.id.option_view).setOnClickListener {
            dlg.dismiss()
            showFullProfileImageDialog(imageUrl)
        }
        view.findViewById<TextView>(R.id.option_update).setOnClickListener {
            dlg.dismiss()
            pickImageFromGallery()
        }

        // dim behind
        dlg.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.6f)
            // wrap content so it doesn't fill screen
            setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dlg.show()
    }

    private fun showFullProfileImageDialog(imageUrl: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_profile_image, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.profileImage)
        val editBtn = dialogView.findViewById<ImageView>(R.id.editButton)

        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Load image into center
        Glide.with(this).load(imageUrl).circleCrop().placeholder(R.drawable.camera).into(imageView)

        editBtn.setOnClickListener {
            dialog.dismiss()
            pickImageFromGallery()
        }

        // Dismiss on outside touch
        dialogView.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun startTour() {
        TourHelper.startTour(
            fragment = this, tourKey = "profile", targets = listOf(
                TourTarget(
                    binding.profileImage,
                    "Upload Profile Picture",
                    "Tap here to upload your profile picture."
                ),
                TourTarget(
                    binding.updatePasswordBtn,
                    "Update Password",
                    "Tap here to update your password."
                ),
            ), onMessage = { message, isError ->
                showSnackbar(message, isError)
            })
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        if (_binding == null) return
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).apply {
            view.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    requireContext(),
                    if (isError) R.color.snackbar_error else R.color.snackbar_success
                )
            )
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
