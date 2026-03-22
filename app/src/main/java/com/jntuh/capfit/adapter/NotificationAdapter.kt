package com.jntuh.capfit.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jntuh.capfit.R
import com.jntuh.capfit.data.AppNotification
import com.jntuh.capfit.data.NotificationType
import com.jntuh.capfit.databinding.ItemNotificationBinding

class NotificationAdapter(
    private val notifications: List<AppNotification>,
    private val onAcceptClicked: (AppNotification) -> Unit,
    private val onRejectClicked: (AppNotification) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    inner class NotificationViewHolder(val binding: ItemNotificationBinding)
        : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notif = notifications[position]
        val b = holder.binding

        b.imgIcon.setImageResource(R.drawable.ic_calories)

        b.tvTitle.text = notif.title

        if (!notif.data.isNullOrEmpty()) {
            val sub = notif.data?.get("subtitle")
            if (sub != null) {
                b.tvSubtitle.text = sub
                b.tvSubtitle.visibility = android.view.View.VISIBLE
            } else {
                b.tvSubtitle.visibility = android.view.View.GONE
            }
        } else {
            b.tvSubtitle.visibility = android.view.View.GONE
        }

        b.tvTime.text = notif.timeAgo()

        if (notif.type == NotificationType.FRIEND_REQUEST &&
            notif.actionStatus == "PENDING") {

            b.actionButtonsLayout.visibility = android.view.View.VISIBLE

            b.btnAccept.setOnClickListener { onAcceptClicked(notif) }
            b.btnReject.setOnClickListener { onRejectClicked(notif) }

        } else {
            b.actionButtonsLayout.visibility = android.view.View.GONE
        }
    }

    override fun getItemCount(): Int = notifications.size
}

fun AppNotification.timeAgo(): String {
    val diff = System.currentTimeMillis() - timestamp

    val mins = diff / 60000
    val hrs = mins / 60
    val days = hrs / 24

    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "$mins mins ago"
        hrs < 24 -> "$hrs hrs ago"
        else -> "$days days ago"
    }
}
