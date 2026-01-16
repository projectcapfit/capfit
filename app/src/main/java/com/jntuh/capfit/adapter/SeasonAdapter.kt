package com.jntuh.capfit.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jntuh.capfit.data.SeasonData
import com.jntuh.capfit.databinding.ItemSeasonBinding

class SeasonAdapter(
    private var seasons: List<SeasonData>
) : RecyclerView.Adapter<SeasonAdapter.SeasonViewHolder>() {

    inner class SeasonViewHolder(
        val binding: ItemSeasonBinding
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SeasonViewHolder {
        val binding = ItemSeasonBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SeasonViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: SeasonViewHolder,
        position: Int
    ) {
        val season = seasons[position]

        holder.binding.tvSeasonTitle.text =
            "Season ${season.seasonYear}-${season.seasonMonth}"

        holder.binding.tvSeasonDistance.text =
            season.distanceCoveredInThisSeason.toString()

        holder.binding.tvSeasonArea.text =
            season.areaCoveredInThisSeason.toString()

        holder.binding.tvSeasonScore.text =
            season.seasonScore.toString()

        holder.binding.tvSeasonRank.text =
            if (season.seasonRank == -1)
                "Rank: Not ranked yet"
            else
                "Rank: ${season.seasonRank}"
    }

    override fun getItemCount(): Int = seasons.size

    fun submitList(newList: List<SeasonData>) {
        seasons = newList
        notifyDataSetChanged()
    }
}
