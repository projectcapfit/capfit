package com.jntuh.capfit.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jntuh.capfit.R
import com.jntuh.capfit.data.Achievement
import com.jntuh.capfit.data.AchievementCategory
import com.jntuh.capfit.databinding.ItemAchievementBinding

class AchievementAdapter(
    private var list: List<Achievement>
) : RecyclerView.Adapter<AchievementAdapter.AchViewHolder>() {

    inner class AchViewHolder(val binding: ItemAchievementBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchViewHolder {
        val binding = ItemAchievementBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AchViewHolder(binding)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: AchViewHolder, position: Int) {
        val a = list[position]
        val b = holder.binding
        val ctx = b.root.context

        b.title.text = a.title

        val iconRes = when (a.category) {
            AchievementCategory.DISTANCE -> R.drawable.ic_distance
            AchievementCategory.AREA -> R.drawable.ic_area
            AchievementCategory.SCORE -> R.drawable.ic_score
            AchievementCategory.STREAK -> R.drawable.ic_streak
        }
        b.icon.setImageResource(iconRes)

        if (a.isUnlocked) {
            b.status.text = "Earned"
            b.status.setTextColor(ContextCompat.getColor(ctx, R.color.green))
            b.title.setTextColor(ContextCompat.getColor(ctx, R.color.black))
            b.iconCircle.setBackgroundResource(R.drawable.icon_circle_unlocked)
        } else {
            b.status.text = "Locked"
            b.status.setTextColor(ContextCompat.getColor(ctx, R.color.ash))
            b.title.setTextColor(ContextCompat.getColor(ctx, R.color.light_black))
            b.iconCircle.setBackgroundResource(R.drawable.icon_circle_locked)
        }
    }

    fun updateList(newList: List<Achievement>) {
        list = newList.sortedByDescending { it.isUnlocked }
        notifyDataSetChanged()
    }
}
