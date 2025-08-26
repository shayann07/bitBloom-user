package com.codingEmpire.bitbloom.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.models.TransactionModel
import com.codingEmpire.bitbloom.utils.TxnConstants
import java.text.SimpleDateFormat
import java.util.Locale

class TransactionAdapter(
    private var transactions: List<TransactionModel>,
    private val onClick: (TransactionModel) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    inner class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvType = itemView.findViewById<TextView>(R.id.tvType)
        private val tvAmount = itemView.findViewById<TextView>(R.id.tvAmount)
        private val tvDate = itemView.findViewById<TextView>(R.id.tvDate)
        private val tvStatus = itemView.findViewById<TextView>(R.id.tvStatus)

        fun bind(txn: TransactionModel) {
            // Amount
            tvAmount.text = String.format("$%.2f", txn.amount)
            // Date
            tvDate.text = formatDate(txn.timestamp)

            // Status & color
            tvStatus.text = txn.status
            val colorRes = when (txn.status.lowercase()) {
                TxnConstants.STATUS_PENDING -> R.color.yellow
                TxnConstants.STATUS_APPROVED,
                TxnConstants.STATUS_COLLECTED,
                TxnConstants.STATUS_RECEIVED -> R.color.seaGreen

                TxnConstants.STATUS_REJECTED,
                TxnConstants.STATUS_CANCELLED -> R.color.black

                else -> android.R.color.darker_gray
            }
            tvStatus.setTextColor(itemView.context.getColorCompat(colorRes))

            // Type display logic
            tvType.text = when {
                txn.type.equals(TxnConstants.TYPE_ROI, ignoreCase = true) ||
                        txn.type.equals(TxnConstants.TYPE_TEAM, ignoreCase = true) ->
                    txn.address ?: formatType(txn.type)

                else -> formatType(txn.type)
            }

            // Item click
            itemView.setOnClickListener { onClick(txn) }
        }

        private fun formatType(type: String): String {
            return when (type.lowercase(Locale.ROOT)) {
                "roi" -> "ROI"
                "roirefund" -> "Investment Refund"
                "referral" -> "Referral Bonus"
                "team" -> "Team Reward"
                "deposit" -> "Deposit"
                "withdraw" -> "Withdrawal"
                "luckyspin" -> "Lucky Spin"
                "salary" -> "Salary"
                "achievement" -> "Achievement"
                else -> type.replaceFirstChar { it.uppercase() }
            }
        }

        private fun formatDate(ts: Any?): String {
            return try {
                val date = (ts as? com.google.firebase.Timestamp)?.toDate()
                    ?: return "Unknown Date"
                val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                sdf.format(date)
            } catch (e: Exception) {
                "Invalid Date"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(transactions[position])
    }

    override fun getItemCount(): Int = transactions.size

    fun submitList(newList: List<TransactionModel>?) {
        newList?.let {
            transactions = it
            notifyDataSetChanged()
        }
    }

    // Helper for color resolution
    private fun android.content.Context.getColorCompat(resId: Int): Int =
        androidx.core.content.ContextCompat.getColor(this, resId)
}
