package com.codingEmpire.bitbloom.utils

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.models.TransactionModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TransactionDialogUtil {

    fun showTransactionDialog(context: Context, txn: TransactionModel) {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialoge_recipt)
        dialog.setCancelable(true)

        // Dim the background and push dialog from top
        // Dim the background and center the dialog
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.6f)
            setGravity(Gravity.CENTER)
            val params = attributes
            params.y = 100
        }


        val tvAmount = dialog.findViewById<TextView>(R.id.invested_amount)

        // Max 6 cards (matching layout includes)
        val cardList = listOf(
            R.id.card_userId,
            R.id.card_paymentTime,
            R.id.card_planName,
            R.id.card_userName,

            )

        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val formattedDate = sdf.format(txn.timestamp?.toDate() ?: Date())

        val values = when (txn.type.lowercase()) {
            "deposit" -> listOf(
                "Address" to (txn.address ?: "N/A"),
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to txn.type
            )

            "withdraw" -> listOf(
                "walletAddress" to (txn.walletAddress ?: "N/A"),
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to txn.type
            )

            "teamreward", "team_reward" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "Address" to (txn.address ?: "N/A"),
                "Status" to txn.status,
                "Transaction" to txn.type,
                "Time" to formattedDate
            )

            "roireward", "roi_reward" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "Transaction ID" to (txn.coinpaymentsId ?: txn.id ?: "N/A"),
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to txn.type
            )

            "plan bought", "plan_bought" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "Plan Name" to (txn.planName ?: "N/A"),
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to txn.type
            )

            "plan bonus ", "plan_bonus" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "From" to (txn.triggeredBy ?: "N/A"),
                "Status" to txn.status,
                "Time" to formattedDate,
                "Transaction" to txn.type
            )

            "luckyspin" -> listOf(
                "Reward" to "$${"%.2f".format(txn.amount)}",
                "Time" to formattedDate,
                "Status" to txn.status,
                "Transaction" to "Lucky Spin"
            )

            "salary" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "Time" to formattedDate,
                "Status" to txn.status,
                "Transaction" to "Salary"
            )

            "achievement" -> listOf(
                "Amount" to "$${"%.2f".format(txn.amount)}",
                "Time" to formattedDate,
                "Status" to txn.status,
                "Transaction" to "Achievement"
            )

            else -> listOf("Info" to "Others")
        }

        tvAmount.text = "$${"%.2f".format(txn.amount)}"

        // Hide all cards first
        cardList.forEach { dialog.findViewById<View>(it).visibility = View.GONE }

        // Populate dynamic values
        values.forEachIndexed { index, (label, value) ->
            if (index < cardList.size) {
                val card = dialog.findViewById<View>(cardList[index])
                card.findViewById<TextView>(R.id.title).text = label
                card.findViewById<TextView>(R.id.value).text = value
                card.visibility = View.VISIBLE
            }
        }

        dialog.show()
    }
}
