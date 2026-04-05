package com.jntuh.capfit.adapter

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jntuh.capfit.data.UserGameData
import com.jntuh.capfit.databinding.ItemFriendAddableBinding
import com.jntuh.capfit.databinding.ItemFriendDeleteableBinding
import com.jntuh.capfit.ui.friends.OtherProfile

class FriendsAdapter(
    private val onAddClick: (UserGameData) -> Unit,
    private val onRemoveClick: (UserGameData) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    enum class Mode { FRIENDS, SEARCH }

    private var mode = Mode.FRIENDS
    private val list = mutableListOf<UserGameData>()

    fun setMode(newMode: Mode) {
        mode = newMode
    }

    fun submitList(newList: List<UserGameData>) {
        list.clear()
        list.addAll(newList)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (mode == Mode.FRIENDS) 0 else 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        return if (viewType == 0) {
            val binding = ItemFriendDeleteableBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            FriendVH(binding)
        } else {
            val binding = ItemFriendAddableBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            SearchVH(binding)
        }
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        val item = list[position]

        holder.itemView.setOnClickListener {

            val context = holder.itemView.context
            val intent = Intent(context, OtherProfile::class.java)

            intent.putExtra("user_game_data", item)
            context.startActivity(intent)
        }

        when (holder) {

            is FriendVH -> {
                holder.binding.textName.text = item.userName
                holder.binding.btnRemove.setOnClickListener {
                    onRemoveClick(item)
                }
            }

            is SearchVH -> {
                holder.binding.textName.text = item.userName
                holder.binding.btnAdd.setOnClickListener {
                    onAddClick(item)
                    holder.binding.apply {
                        btnAdd.isEnabled = false

                        btnAdd.alpha = 0.4f
                    }

                }
            }
        }
    }

    class FriendVH(val binding: ItemFriendDeleteableBinding)
        : RecyclerView.ViewHolder(binding.root)

    class SearchVH(val binding: ItemFriendAddableBinding)
        : RecyclerView.ViewHolder(binding.root)
}
