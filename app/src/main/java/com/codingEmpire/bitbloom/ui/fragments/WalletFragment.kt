package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.TransactionAdapter
import com.codingEmpire.bitbloom.databinding.FragmentWalletBinding
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.utils.TourHelper
import com.codingEmpire.bitbloom.utils.TourTarget
import com.codingEmpire.bitbloom.utils.TransactionDialogUtil
import com.codingEmpire.bitbloom.viewModels.AccountViewModel
import com.codingEmpire.bitbloom.viewModels.TransactionViewModel
import com.codingEmpire.bitbloom.viewModels.WalletViewModel
import com.google.android.material.snackbar.Snackbar

class WalletFragment : BaseFragment() {

    private var _binding: FragmentWalletBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: WalletViewModel
    private lateinit var transactionViewModel: TransactionViewModel
    private lateinit var transactionAdapter: TransactionAdapter
    private val accountViewModel: AccountViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)
        setUpReward(view)

        viewModel = ViewModelProvider(this)[WalletViewModel::class.java]
        transactionViewModel = ViewModelProvider(this)[TransactionViewModel::class.java]

        val userId = PrefService(requireContext()).getString("user_id") ?: return

        // Initial loads
        viewModel.loadCryptoPrices()
        viewModel.loadWallet()
        viewModel.loadWalletAndTokenValues()
        viewModel.observeXBLMLiveRate()
        accountViewModel.loadProfile()
        viewModel.loadTotalRewardTokens(userId)
        startTour()


        transactionAdapter = TransactionAdapter(emptyList()) { txn ->
            TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
        }
        binding.lastTransactionRecycler.apply {
            adapter = transactionAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        transactionViewModel.fetchAllTransactions(userId)

        // Observers
        viewModel.isLoading.observe(viewLifecycleOwner) { if (it) showLoading() else hideLoading() }
        viewModel.error.observe(viewLifecycleOwner) { it?.let { msg -> showSnackbar(msg, true) } }

        viewModel.totalRewardTokens.observe(viewLifecycleOwner) { sum ->
            binding.XBitBloomtvTokens.text = "$sum Tokens"

        }
        viewModel.spinUsdtAmount.observe(viewLifecycleOwner) { value ->
            binding.spinUSDTValue.text = "$${"%.2f".format(value)}"
        }

        viewModel.totalWalletAmount.observe(viewLifecycleOwner) { value ->
            binding.walletCard.tvAmount.text = " $${"%.2f".format(value)}"
        }

        viewModel.tokenOnlyAmount.observe(viewLifecycleOwner) { value ->
            binding.USDTValue.text = "$${"%.2f".format(value)}"
        }

        viewModel.xblmLiveRate.observe(viewLifecycleOwner) { (rate, pct) ->
            binding.bitBloomPriceTv.text = "$${"%.4f".format(rate)}"
            binding.bitbloomPct.text = "${if (pct >= 0) "+" else ""}${"%.2f".format(pct)}%"
            binding.XBitBloomChange.text = "${if (pct >= 0) "+" else ""}${"%.2f".format(pct)}%"

            val isPositive = pct >= 0
            val color = ContextCompat.getColor(
                requireContext(),
                if (isPositive) R.color.seaGreen else android.R.color.holo_red_light
            )

            // Update text color and arrow tint
            binding.bitbloomPct.setTextColor(color)
            binding.XBitBloomChange.setTextColor(color)
            binding.bitbloomArrow.setColorFilter(color)
            binding.XBitBloomCoinIV.setColorFilter(color)
            // Set icon based on direction
            binding.bitbloomArrow.setImageResource(
                if (isPositive) R.drawable.arrowup else R.drawable.arrowdown
            )
            binding.XBitBloomCoinIV.setImageResource(
                if (isPositive) R.drawable.arrowup else R.drawable.arrowdown
            )
        }

        viewModel.cryptoPrices.observe(viewLifecycleOwner) { prices ->
            prices?.get("bitcoin")?.let {
                setCryptoUI(
                    price = it.usd,
                    change = it.usd_24h_change,
                    priceTv2 = binding.bitcoinPriceTV,
                    changeTv2 = binding.bitcoinChangeTv,
                    iconIv2 = binding.bitcoinChangeiv
                )
            }
            prices?.get("ethereum")?.let {
                setCryptoUI(
                    price = it.usd,
                    change = it.usd_24h_change,
                    priceTv2 = binding.ethPrice,
                    changeTv2 = binding.ethChange,
                    iconIv2 = binding.ethChangeIv
                )
            }
            prices?.get("tether")?.let {
                setCryptoUI(
                    price = it.usd,
                    change = it.usd_24h_change,
                    binding.tetherPriceTv,
                    binding.tetherChange,
                    binding.tetherChangeIv
                )
                binding.USDTAmountChange.text = "$${"%.2f".format(it.usd_24h_change)}"
                val isUp = it.usd_24h_change >= 0
                val color = ContextCompat.getColor(
                    requireContext(), if (isUp) R.color.seaGreen else android.R.color.holo_red_light
                )
                val icon = if (isUp) R.drawable.arrowup else R.drawable.arrowdown
                val changeText = "${if (isUp) "+" else ""}${"%.2f".format(it.usd_24h_change)}%"
                binding.USDTAmountChange.text = changeText
                binding.USDTAmountChange.setTextColor(color)
                binding.tetherChangeIv.apply {
                    setImageResource(icon)
                    setColorFilter(color)
                }
            }
        }

        transactionViewModel.allTransactions.observe(viewLifecycleOwner) { txns ->
            transactionAdapter.submitList(txns)
        }

        binding.seeAllTransaction.setOnClickListener {
            findNavController().navigate(R.id.action_walletFragment_to_transactionsFragment)
        }

        accountViewModel.profileData.observe(viewLifecycleOwner) { data ->
            binding.walletCard.tvName.text = "${data["name"]}"
        }

        binding.depositAmount.itemDeposit.setOnClickListener {
            findNavController().navigate(R.id.action_walletFragment_to_depositFragment)
        }
        binding.withdrawAmount.itemWithdraw.setOnClickListener {
            findNavController().navigate(R.id.action_walletFragment_to_withdrawFragment)
        }
        binding.transfer.itemPlansBought.setOnClickListener {
            showSnackbar("Coming Soon", true)
        }
        binding.swap.itemSetting.setOnClickListener {
            showSnackbar("Coming Soon", true)
        }
    }

    private fun setCryptoUI(
        price: Double,
        change: Double,
        priceTv2: android.widget.TextView? = null,
        changeTv2: android.widget.TextView? = null,
        iconIv2: android.widget.ImageView? = null
    ) {
        val isUp = change >= 0
        val color = ContextCompat.getColor(
            requireContext(), if (isUp) R.color.seaGreen else android.R.color.holo_red_light
        )
        val icon = if (isUp) R.drawable.arrowup else R.drawable.arrowdown
        val changeText = "${if (isUp) "+" else ""}${"%.2f".format(change)}%"



        priceTv2?.text = "$${"%.2f".format(price)}"
        changeTv2?.text = changeText
        changeTv2?.setTextColor(color)
        iconIv2?.apply {
            setImageResource(icon)
            setColorFilter(color)
        }
    }

    private fun startTour() {
        TourHelper.startTour(
            fragment = this,
            tourKey = "wallet",
            targets = listOf(
                TourTarget(
                    binding.walletCard.tvAmount,
                    "Wallet Balance",
                    "This shows your current wallet balance."
                ),
                TourTarget(
                    binding.depositAmount.itemDeposit,
                    "Deposit",
                    "Tap here to deposit funds."
                ),
                TourTarget(
                    binding.withdrawAmount.itemWithdraw,
                    "Withdraw",
                    "Withdraw your earnings here."
                ),
            ),
            onMessage = { message, isError ->
                showSnackbar(message, isError)
            }
        )
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

    override fun onResume() {
        super.onResume()
        viewModel.loadWallet()
        viewModel.loadCryptoPrices()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}