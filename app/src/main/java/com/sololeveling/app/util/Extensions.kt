package com.sololeveling.app.util

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.view.View
import android.view.animation.AnimationUtils
import com.sololeveling.app.R
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ─── View Extensions ──────────────────────────────────────────────────────────

fun View.fadeIn(duration: Long = 300) {
    alpha = 0f
    visibility = View.VISIBLE
    animate().alpha(1f).setDuration(duration).start()
}

fun View.fadeOut(duration: Long = 300, onEnd: (() -> Unit)? = null) {
    animate().alpha(0f).setDuration(duration).withEndAction {
        visibility = View.GONE
        onEnd?.invoke()
    }.start()
}

fun View.slideInFromBottom(duration: Long = 400) {
    translationY = height.toFloat()
    visibility = View.VISIBLE
    animate().translationY(0f).setDuration(duration)
        .setInterpolator(android.view.animation.DecelerateInterpolator())
        .start()
}

fun View.pulse() {
    animate()
        .scaleX(1.05f).scaleY(1.05f)
        .setDuration(150)
        .withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
}

fun View.shake() {
    val anim = AnimationUtils.loadAnimation(context, android.R.anim.cycle_interpolator)
    startAnimation(anim)
}

// ─── Number Extensions ────────────────────────────────────────────────────────

val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
val Float.dp: Float get() = this * Resources.getSystem().displayMetrics.density
val Int.sp: Float get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this.toFloat(), Resources.getSystem().displayMetrics)

fun Long.toReadableTime(): String {
    val hours = TimeUnit.MILLISECONDS.toHours(this)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(this) % 60
    return when {
        hours > 0 -> "${hours}ч ${minutes}мин"
        minutes > 0 -> "${minutes}мин"
        else -> "< 1 мин"
    }
}

// ─── Date Extensions ──────────────────────────────────────────────────────────

fun Long.toDateString(): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
    return sdf.format(Date(this))
}

fun Long.toTimeString(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toRelativeTime(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < 60_000 -> "только что"
        diff < 3_600_000 -> "${diff / 60_000} мин назад"
        diff < 86_400_000 -> "${diff / 3_600_000} ч назад"
        else -> toDateString()
    }
}

fun isToday(timestamp: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp }
    val cal2 = Calendar.getInstance()
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

fun isSameDay(ts1: Long, ts2: Long): Boolean {
    val c1 = Calendar.getInstance().apply { timeInMillis = ts1 }
    val c2 = Calendar.getInstance().apply { timeInMillis = ts2 }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}

fun getMidnight(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun getEndOfDay(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    return cal.timeInMillis
}

// ─── String Extensions ────────────────────────────────────────────────────────

fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
    return if (length <= maxLength) this
    else substring(0, maxLength - ellipsis.length) + ellipsis
}

fun String.capitalizeFirst(): String {
    return if (isEmpty()) this
    else this[0].uppercaseChar() + substring(1)
}

// ─── Context Extensions ───────────────────────────────────────────────────────

fun Context.getColorCompat(colorRes: Int): Int {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        getColor(colorRes)
    } else {
        @Suppress("DEPRECATION")
        resources.getColor(colorRes)
    }
}

fun Context.isNetworkAvailable(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } else {
        @Suppress("DEPRECATION")
        cm.activeNetworkInfo?.isConnected == true
    }
}

// ─── Math / Progress ──────────────────────────────────────────────────────────

fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + fraction * (end - start)
}

fun clamp(value: Float, min: Float, max: Float): Float {
    return maxOf(min, minOf(max, value))
}

fun progressPercent(current: Long, total: Long): Int {
    if (total == 0L) return 0
    return ((current.toFloat() / total) * 100).toInt().coerceIn(0, 100)
}
