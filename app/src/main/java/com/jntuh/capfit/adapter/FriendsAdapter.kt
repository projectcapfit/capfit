package com.jntuh.capfit.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jntuh.capfit.data.UserGameData
import com.jntuh.capfit.databinding.ItemFriendAddableBinding
import com.jntuh.capfit.databinding.ItemFriendDeleteableBinding

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
                }
            }
        }
    }

    // ---------- ViewHolders ----------

    class FriendVH(val binding: ItemFriendDeleteableBinding)
        : RecyclerView.ViewHolder(binding.root)

    class SearchVH(val binding: ItemFriendAddableBinding)
        : RecyclerView.ViewHolder(binding.root)
}
