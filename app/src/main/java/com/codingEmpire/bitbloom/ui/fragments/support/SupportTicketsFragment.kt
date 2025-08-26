package com.codingEmpire.bitbloom.ui.fragments.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.databinding.FragmentSupportTicketsBinding
import com.codingEmpire.bitbloom.ui.fragments.BaseFragment
import com.codingEmpire.bitbloom.viewModels.support.UserSupportVMFactory
import com.codingEmpire.bitbloom.viewModels.support.UserSupportViewModel

class SupportTicketsFragment : BaseFragment() {

    private var _vb: FragmentSupportTicketsBinding? = null
    private val vb get() = _vb!!

    private val vm: UserSupportViewModel by viewModels {
        UserSupportVMFactory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _vb = FragmentSupportTicketsBinding.inflate(inflater, container, false)
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        setUpReward(view)

        vb.pendingCard.setOnClickListener(nav(R.id.pendingTicketsFragment))
        vb.closedCard.setOnClickListener(nav(R.id.closedTicketsFragment))
        vb.answeredCard.setOnClickListener(nav(R.id.answeredTicketsFragment))
        vb.allCard.setOnClickListener(nav(R.id.allTicketsFragment))

        lifecycleScope.launchWhenStarted {
            vm.pendingCount.collect { vb.pendingAmount.text = it.toString() }
        }
        lifecycleScope.launchWhenStarted {
            vm.closedCount.collect { vb.closedAmount.text = it.toString() }
        }
        lifecycleScope.launchWhenStarted {
            vm.myTickets.collect { vb.allAmount.text = it.size.toString() }
        }
    }

    private fun nav(@IdRes dest: Int) = View.OnClickListener {
        findNavController().navigate(dest)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _vb = null
    }
}
