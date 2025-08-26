package com.codingEmpire.bitbloom.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codingEmpire.bitbloom.models.BuyPlan

class PlansPagerAdapter(
    private val pages: List<List<BuyPlan>>
) : RecyclerView.Adapter<PlansPagerAdapter.PageViewHolder>() {

    inner class PageViewHolder(val recyclerView: RecyclerView) :
        RecyclerView.ViewHolder(recyclerView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val recyclerView = RecyclerView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(parent.context)
        }
        return PageViewHolder(recyclerView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val adapter = com.codingEmpire.bitbloom.adapters.PurchasedPlansAdapter(pages[position]) { /* click optional */ }
        holder.recyclerView.adapter = adapter
    }

    override fun getItemCount(): Int = pages.size
}
