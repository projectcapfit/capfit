package com.jntuh.capfit.adapter

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jntuh.capfit.R
import com.jntuh.capfit.data.UserGameData
import com.jntuh.capfit.ui.friends.OtherProfile

class LeaderboardAdapter(
    private val currentUid: String
) : RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    private var items: List<Pair<UserGameData, Int>> = emptyList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRank:     TextView  = view.findViewById(R.id.tvRankNumber)
        val tvName:     TextView  = view.findViewById(R.id.tvName)
        val tvArea:     TextView  = view.findViewById(R.id.tvScore)
        val ivMedal:    ImageView = view.findViewById(R.id.ivMedal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rank_normal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (user, rank) = items[position]
        val isMe = user.uid == currentUid

        holder.itemView.setOnClickListener { it ->
            val intent =  Intent(holder.itemView.context, OtherProfile::class.java)
            intent.putExtra("user_game_data", user)
            holder.itemView.context.startActivity(intent)
        }
        // Rank label
        holder.tvRank.text = "#$rank"
        // Name — bold + highlight if current user
        holder.tvName.text = if (isMe) "${user.userName} (You)" else user.userName

        holder.tvName.setTextColor(
            if (isMe) Color.parseColor("#B0CF3B") else Color.parseColor("#111111")
        )

        val area = user.capturedArea
        holder.tvArea.text = if (area >= 1_000_000)
            String.format("%.2f km²", area / 1_000_000)
        else
            String.format("%.0f m²", area)

        // Medal icon for top 3
        when (rank) {
            1 -> { holder.ivMedal.visibility = View.VISIBLE; holder.ivMedal.setImageResource(R.drawable.ic_medal_gold) }
            2 -> { holder.ivMedal.visibility = View.VISIBLE; holder.ivMedal.setImageResource(R.drawable.ic_medal_silver) }
            3 -> { holder.ivMedal.visibility = View.VISIBLE; holder.ivMedal.setImageResource(R.drawable.ic_medal_bronze) }
            else -> holder.ivMedal.visibility = View.GONE
        }

        // Highlight row if current user
        holder.itemView.setBackgroundColor(
            if (isMe) Color.parseColor("#F0F9E0") else Color.WHITE
        )
    }

    override fun getItemCount() = items.size

    fun submitList(list: List<Pair<UserGameData, Int>>) {
        items = list
        notifyDataSetChanged()
    }
}