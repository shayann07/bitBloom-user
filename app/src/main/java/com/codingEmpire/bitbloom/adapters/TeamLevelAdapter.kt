package com.codingEmpire.bitbloom.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.codingEmpire.bitbloom.databinding.ItemTeamLevelBinding
import com.codingEmpire.bitbloom.models.TeamLevel
import java.util.Locale
import kotlin.math.floor

class TeamLevelAdapter(
    private val onClick: (TeamLevel) -> Unit
) : ListAdapter<TeamLevel, TeamLevelAdapter.LevelVH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        LevelVH(
            ItemTeamLevelBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: LevelVH, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnClickListener { onClick(item) }
    }

    inner class LevelVH(private val b: ItemTeamLevelBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(lvl: TeamLevel) = with(b) {
            // Header
            levelNumber.text = "Level ${lvl.level}"
            totalUsers.text = "Total Users\n${lvl.totalUsers}"
            activeUser.text = "Active\n${lvl.activeUsers}"

            // Invested amount: truncate to 2 decimals
            val raw = lvl.investedAmount
            val truncated = floor(raw * 100) / 100
            val display = if (truncated == truncated.toLong().toDouble()) {
                // integer, no decimals
                "%,d".format(Locale.getDefault(), truncated.toLong())
            } else {
                // show exactly two decimals
                String.format(Locale.getDefault(), "%,.2f", truncated)
            }
            totalDeposit.text = "Business\n$$display"

            inActiveUsers.text = "In-active\n${lvl.inactiveUsers}"

            // Lock overlay
            if (lvl.levelUnlocked) {
                lockOverlay.visibility = View.GONE
                root.alpha = 1f
            } else {
                lockOverlay.visibility = View.VISIBLE
                root.alpha = 0.4f
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<TeamLevel>() {
        override fun areItemsTheSame(o: TeamLevel, n: TeamLevel) =
            o.level == n.level

        override fun areContentsTheSame(o: TeamLevel, n: TeamLevel) =
            o == n
    }
}