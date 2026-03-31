package com.jntuh.capfit.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jntuh.capfit.R
import com.jntuh.capfit.data.TrackingSession
import com.jntuh.capfit.helper.WorkoutDateFormatter

class WorkoutAdapter(
    private var sessions: List<TrackingSession> = emptyList()
) : RecyclerView.Adapter<WorkoutAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtWhenLabel: TextView = view.findViewById(R.id.txtWhenLabel)
        val txtDuration:  TextView = view.findViewById(R.id.txtDuration)
        val txtDistance:  TextView = view.findViewById(R.id.txtDistance)
        val txtArea:      TextView = view.findViewById(R.id.txtArea)
        val divider:      View     = view.findViewById(R.id.divider)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]

        // Hide divider on last item
        holder.divider.visibility = if (position == sessions.size - 1) View.GONE else View.VISIBLE

        // Date label: "Today" / "Yesterday" / "16 Mar" / "5 Nov 2025"
        holder.txtWhenLabel.text = WorkoutDateFormatter.format(session.date)

        // Duration from startTime → endTime
        val durationMs = if (session.startTime > 0L && session.endTime > session.startTime)
            session.endTime - session.startTime else 0L
        holder.txtDuration.text = formatDuration(durationMs)

        // Distance
        holder.txtDistance.text = if (session.distance >= 1000)
            String.format("%.2f km", session.distance / 1000.0)
        else
            String.format("%.0f m", session.distance)

        // Area — only show if session has territory
        if (session.area > 0) {
            holder.txtArea.text = String.format("%.0f m²", session.area)
            holder.txtArea.visibility = View.VISIBLE
        } else {
            holder.txtArea.text = "No territory"
            holder.txtArea.visibility = View.VISIBLE
        }
    }

    override fun getItemCount() = sessions.size

    fun submitList(newList: List<TrackingSession>) {
        sessions = newList
        notifyDataSetChanged()
    }

    private fun formatDuration(ms: Long): String {
        val totalSecs = (ms / 1000).toInt()
        val hrs  = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return when {
            hrs  > 0 -> "${hrs}h ${mins}m ${secs}s"
            mins > 0 -> "${mins}m ${secs}s"
            else     -> "${secs}s"
        }
    }
}