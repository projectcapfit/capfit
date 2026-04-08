package com.jntuh.capfit.adapter

import android.content.res.ColorStateList
import android.graphics.Color
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
            LayoutInflater.from(parent.context), parent, false
        )
        return AchViewHolder(binding)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: AchViewHolder, position: Int) {
        val a   = list[position]
        val b   = holder.binding
        val ctx = b.root.context

        b.title.text = a.title

        val iconRes = when (a.category) {
            AchievementCategory.DISTANCE    -> R.drawable.ic_distance
            AchievementCategory.AREA        -> R.drawable.ic_area
            AchievementCategory.STREAK      -> R.drawable.ic_streak
            AchievementCategory.WORKOUTS    -> R.drawable.ic_calories
            AchievementCategory.ACTIVE_TIME -> R.drawable.ic_layers
        }
        b.icon.setImageResource(iconRes)

//        if (a.isUnlocked) {
//            b.status.text = "Earned"
//            b.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.ach_card_unlocked))
//            b.status.setTextColor(ContextCompat.getColor(ctx, R.color.ach_text_unlocked))
//            b.iconCircle.setBackgroundResource(R.drawable.icon_circle_unlocked)
//
//            b.icon.setColorFilter(ContextCompat.getColor(ctx, R.color.black))
//
//        } else {
//            b.status.text = "${a.currentProgress} / ${a.threshold}"
//            b.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.ach_card_locked))
//            b.status.setTextColor(ContextCompat.getColor(ctx, R.color.ach_text_locked))
//            b.iconCircle.setBackgroundResource(R.drawable.icon_circle_locked)
//
//            b.icon.setColorFilter(ContextCompat.getColor(ctx, R.color.ach_text_locked))
//        }
        if (a.isUnlocked) {
            b.card.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
            b.iconCircle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F0F7E0"))
            b.icon.imageTintList = ColorStateList.valueOf(Color.parseColor("#96C11F"))
            b.title.setTextColor(Color.parseColor("#2D3319"))
            b.status.setTextColor(Color.parseColor("#7AA317"))
            b.status.text = "Unlocked"
        } else {
            b.status.text = "${a.currentProgress} / ${a.threshold}"
            b.card.setCardBackgroundColor(Color.parseColor("#FAFAFA"))
            b.iconCircle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EEEEEE"))
            b.icon.imageTintList = ColorStateList.valueOf(Color.parseColor("#BDBDBD"))
            b.title.setTextColor(Color.parseColor("#757575"))
            b.status.setTextColor(Color.parseColor("#9E9E9E"))
        }
    }

    fun updateList(newList: List<Achievement>) {
        // Unlocked first, then locked sorted by category
        list = newList.sortedWith(
            compareByDescending<Achievement> { it.isUnlocked }
                .thenBy { it.category.ordinal }
                .thenBy { it.threshold }
        )
        notifyDataSetChanged()
    }
}