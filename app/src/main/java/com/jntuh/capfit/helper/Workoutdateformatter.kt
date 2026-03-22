package com.jntuh.capfit.helper

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object WorkoutDateFormatter {

    // Input: "yyyy-MM-dd" string from TrackingSession.date
    // Output: "Today", "Yesterday", "16 Mar", "16 Mar 2025" (if different year)
    fun format(dateStr: String): String {
        if (dateStr.isBlank()) return ""

        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr) ?: return dateStr

            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val yesterday = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val dateCal = Calendar.getInstance().apply { time = date }

            when {
                isSameDay(dateCal, today)     -> "Today"
                isSameDay(dateCal, yesterday) -> "Yesterday"
                dateCal.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
                    SimpleDateFormat("d MMM", Locale.getDefault()).format(date)
                else ->
                    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date)
            }
        } catch (e: Exception) {
            dateStr
        }
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }
}