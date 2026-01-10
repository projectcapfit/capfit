package com.jntuh.capfit.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.jntuh.capfit.R
import com.jntuh.capfit.data.Achievement
import com.jntuh.capfit.data.UserGameData
import com.jntuh.capfit.databinding.ItemAchievementBinding

class AchievementAdapter(
    private val list: List<Achievement>,
    private val user: UserGameData
) : RecyclerView.Adapter<AchievementAdapter.AchViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchViewHolder {
        return AchViewHolder(
            ItemAchievementBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: AchViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount() = list.size

    inner class AchViewHolder(private val binding: ItemAchievementBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(a: Achievement) {
            val context = binding.root.context
            val unlocked = user.achievements.contains(a.id)

            binding.title.text = a.title
            binding.icon.setImageResource(a.icon)
            val circle = binding.icon.parent as FrameLayout
            if (unlocked) {

                circle.setBackgroundResource(R.drawable.circle_bg_dark_green)
                binding.status.text = "Earned"
                binding.card.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.achievements_light_green)
                )
                binding.status.setTextColor(
                    ContextCompat.getColor(context, R.color.achievements_text_dark_green)
                )
                binding.icon.setColorFilter(
                    ContextCompat.getColor(context, R.color.light_black)
                )
            } else {
                circle.setBackgroundResource(R.drawable.circle_bg_dark_gray)
                binding.status.text = "Locked"
                binding.card.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.achievements_light_gray)
                )
                binding.status.setTextColor(
                    ContextCompat.getColor(context, R.color.ash)
                )
                binding.icon.setColorFilter(
                    ContextCompat.getColor(context, R.color.light_black)
                )
            }
        }
    }
}
