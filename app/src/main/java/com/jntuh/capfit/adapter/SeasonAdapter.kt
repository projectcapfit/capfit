package com.jntuh.capfit.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jntuh.capfit.data.SeasonData
import com.jntuh.capfit.databinding.ItemSeasonBinding

class SeasonAdapter(seasons: List<SeasonData> = emptyList()) :
    ListAdapter<SeasonData, SeasonAdapter.SeasonViewHolder>(DiffCallback()) {

    init { submitList(seasons) }

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

        val season = getItem(position)

        holder.binding.tvSeasonTitle.text =
            "Season ${season.seasonYear}-${season.seasonMonth}"

        holder.binding.tvSeasonSubtitle.text =
            getMonthName(season.seasonMonth) + " " + season.seasonYear

        val distM = season.distanceCoveredInThisSeason
        holder.binding.tvSeasonDistance.text = if (distM >= 1000)
            String.format("%.1f km", distM / 1000.0)
        else "$distM m"

        holder.binding.tvSeasonArea.text =
            "${season.areaCoveredInThisSeason} m²"

        holder.binding.tvSeasonWorkouts.text =
            season.numberOfWorkouts.toString()

        holder.binding.tvSeasonRank.text =
            if (season.seasonRank == -1) "Unranked"
            else "Rank ${season.seasonRank}"
    }

    private fun getMonthName(month: String): String {
        return when (month) {
            "01" -> "January"; "02" -> "February"; "03" -> "March"
            "04" -> "April";   "05" -> "May";       "06" -> "June"
            "07" -> "July";    "08" -> "August";    "09" -> "September"
            "10" -> "October"; "11" -> "November";  "12" -> "December"
            else -> month
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SeasonData>() {

        override fun areItemsTheSame(
            oldItem: SeasonData,
            newItem: SeasonData
        ): Boolean {
            return oldItem.seasonYear == newItem.seasonYear &&
                    oldItem.seasonMonth == newItem.seasonMonth
        }

        override fun areContentsTheSame(
            oldItem: SeasonData,
            newItem: SeasonData
        ): Boolean {
            return oldItem == newItem
        }
    }
}