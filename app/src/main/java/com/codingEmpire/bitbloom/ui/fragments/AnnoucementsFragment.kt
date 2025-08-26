package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.AnnouncementAdapter
import com.codingEmpire.bitbloom.models.AnnouncementModel
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.AccountViewModel
import com.google.android.material.snackbar.Snackbar

class AnnoucementsFragment : BaseFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AnnouncementAdapter
    private lateinit var viewModel: AccountViewModel

    // backing list for adapter
    private val data = mutableListOf<AnnouncementModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_annoucements, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)

        // bind RecyclerView
        recyclerView = view.findViewById(R.id.rvTransactions)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // adapter setup
        adapter = AnnouncementAdapter(data)
        recyclerView.adapter = adapter

        // obtain ViewModel
        viewModel = ViewModelProvider(this)
            .get(AccountViewModel::class.java)

        // observe loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) showLoading() else hideLoading()
        }

        // observe announcements list
        viewModel.announcements.observe(viewLifecycleOwner) { list ->
            list?.let {
                data.clear()
                data.addAll(it)
                adapter.notifyDataSetChanged()
            }
        }

        // always load announcements
        viewModel.loadAnnouncements()
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
