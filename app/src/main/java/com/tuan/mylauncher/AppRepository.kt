package com.tuan.mylauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserHandle
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** Thông tin một ứng dụng hiển thị trong launcher. */
data class AppInfo(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    val letter: String,      // chữ cái đầu dùng cho thanh A–Z
    val searchName: String,  // tên đã bỏ dấu, dùng để tìm kiếm
) {
    val key: String get() = component.flattenToString() + "#" + user.hashCode()
}

/** Bỏ dấu tiếng Việt + chữ thường: "Điện Thoại" -> "dien thoai" */
fun String.simplify(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('đ', 'd').replace('Đ', 'D')
        .lowercase(Locale.ROOT)

private fun letterOf(label: String): String {
    val c = label.trim().simplify().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
}

class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val collator = Collator.getInstance(Locale("vi"))

    /** Đọc danh sách ứng dụng (chạy nền để không giật giao diện). */
    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val iconPx = (48 * context.resources.displayMetrics.density).toInt()
        launcherApps.profiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            .filter { it.componentName.packageName != context.packageName }
            .map { a ->
                val label = a.label.toString()
                AppInfo(
                    label = label,
                    packageName = a.componentName.packageName,
                    component = a.componentName,
                    user = a.user,
                    icon = a.getBadgedIcon(0).toBitmap(iconPx, iconPx).asImageBitmap(),
                    letter = letterOf(label),
                    searchName = label.simplify(),
                )
            }
            .sortedWith(compareBy(collator) { it.label })
    }

    // ----- Ứng dụng yêu thích (lưu theo thứ tự) -----
    fun favorites(): List<String> =
        prefs.getString("favorites", "").orEmpty().split("|").filter { it.isNotBlank() }

    fun saveFavorites(keys: List<String>) {
        prefs.edit().putString("favorites", keys.joinToString("|")).apply()
    }

    // ----- Hành động -----
    fun launch(app: AppInfo) {
        try {
            launcherApps.startMainActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Không mở được ${app.label}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(app: AppInfo) {
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Không mở được thông tin ứng dụng", Toast.LENGTH_SHORT).show()
        }
    }

    fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không gỡ được ứng dụng này", Toast.LENGTH_SHORT).show()
        }
    }

    /** Mở một màn hình hệ thống (đồng hồ báo thức, lịch...), bỏ qua nếu máy không có. */
    fun openSafely(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }
}
