package com.codingEmpire.bitbloom.ui.fragments.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.codingEmpire.bitbloom.databinding.FragmentSupportBinding
import com.codingEmpire.bitbloom.ui.fragments.BaseFragment
import com.codingEmpire.bitbloom.viewModels.support.UserSupportVMFactory
import com.codingEmpire.bitbloom.viewModels.support.UserSupportViewModel
import com.google.android.material.snackbar.Snackbar

class SupportFragment : BaseFragment() {

    private var _vb: FragmentSupportBinding? = null
    private val vb get() = _vb!!

    private val vm: UserSupportViewModel by viewModels {
        UserSupportVMFactory(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _vb = FragmentSupportBinding.inflate(inflater, container, false)
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        setUpReward(view)

        vb.btnSubmit.setOnClickListener {
            val phone = vb.etPhoneNumber.text.toString()
            val subject = vb.etSubject.text.toString()
            val msg = vb.editMessage.text.toString()

            if (subject.isBlank() || msg.isBlank()) {
                Snackbar.make(view, "Please fill all fields", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            vm.submitTicket(phone, subject, msg)
            Snackbar.make(view, "Ticket submitted", Snackbar.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _vb = null
    }
}
