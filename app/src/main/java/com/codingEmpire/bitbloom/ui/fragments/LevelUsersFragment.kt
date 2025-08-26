package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.TeamUserAdapter
import com.codingEmpire.bitbloom.databinding.FragmentLevelUsersBinding
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.LevelUsersViewModel
import com.google.android.material.snackbar.Snackbar

class LevelUsersFragment : BaseFragment() {

    private var _b: FragmentLevelUsersBinding? = null
    private val b get() = _b!!

    private val args by navArgs<LevelUsersFragmentArgs>()
    private val vm: LevelUsersViewModel by viewModels()
    private val adapter = TeamUserAdapter()

    private lateinit var rv: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentLevelUsersBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        setUpReward(view)

        // Title
        b.profileTitle.text = "Level ${args.level} Users"

        // RecyclerView setup
        rv = b.rvLevelUser
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Observe data
        vm.users.observe(viewLifecycleOwner) { list ->
            adapter.submit(list)
            val hasAny = list.isNotEmpty()
            rv.visibility = if (hasAny) View.VISIBLE else View.GONE
            b.tvEmpty.visibility = if (hasAny) View.GONE else View.VISIBLE
        }
        vm.loading.observe(viewLifecycleOwner) { if (it) showLoading() else hideLoading() }
        vm.error.observe(viewLifecycleOwner) { msg ->
            msg?.let { showSnackbar(it, true) }
        }

        // Load once
        vm.load(args.level)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
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
}
