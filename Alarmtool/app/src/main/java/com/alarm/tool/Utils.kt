package com.alarm.tool

import android.content.Context

// Extension functions for common utilities
fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density + 0.5f).toInt()
} 