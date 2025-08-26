package com.codingEmpire.bitbloom.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.databinding.ItemAvailablePlans1Binding
import com.codingEmpire.bitbloom.databinding.ItemAvailablePlans2Binding
import com.codingEmpire.bitbloom.models.BuyPlan
import java.text.SimpleDateFormat
import java.util.Locale

class PurchasedPlansAdapter(
    private val plans: List<BuyPlan>,
    private val onPlanClick: (BuyPlan) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int = position % 2

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            val binding = ItemAvailablePlans1Binding.inflate(inflater, parent, false)
            TypeOneVH(binding)
        } else {
            val binding = ItemAvailablePlans2Binding.inflate(inflater, parent, false)
            TypeTwoVH(binding)
        }
    }

    override fun getItemCount(): Int = plans.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val plan = plans[position]
        when (holder) {
            is TypeOneVH -> holder.bind(plan, onPlanClick)
            is TypeTwoVH -> holder.bind(plan, onPlanClick)
        }
    }

    private val dateFormat by lazy { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }

    class TypeOneVH(private val b: ItemAvailablePlans1Binding)
        : RecyclerView.ViewHolder(b.root) {
        private val df = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

        fun bind(plan: BuyPlan, click: (BuyPlan) -> Unit) {
            b.planTitle.text   = plan.planName
            b.amountValue.text = "$${plan.investedAmount}"
            b.roiValue.text    = "${plan.percentage}%"
            b.amountLabel.text = "Invested Amount"

            // Update the “Plan Days” label to “Expiry Date”
            b.root.findViewById<TextView>(R.id.planDaysTitle)
                ?.text = "Expiry Date"

            // Format and set expiry date
            val expiryStr = df.format(plan.expiryDate)
            val startDateStr = df.format(plan.startDate)
            b.startDate.text = startDateStr
            b.root.findViewById<TextView>(R.id.planDays)
                ?.text = expiryStr

            b.root.setOnClickListener { click(plan) }
        }
    }

    class TypeTwoVH(private val b: ItemAvailablePlans2Binding)
        : RecyclerView.ViewHolder(b.root) {
        private val df = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

        fun bind(plan: BuyPlan, click: (BuyPlan) -> Unit) {
            b.planTitle.text   = plan.planName
            b.amountValue.text = "$${plan.investedAmount}"
            b.roiValue.text    = "${plan.percentage}%"

            b.root.findViewById<TextView>(R.id.planDaysTitle)
                ?.text = "Expiry Date"

            val expiryStr = df.format(plan.expiryDate)
            val startDateStr = df.format(plan.startDate)
            b.startDate.text = startDateStr
            b.root.findViewById<TextView>(R.id.planDays)
                ?.text = expiryStr

            b.root.setOnClickListener { click(plan) }
        }
    }
}
