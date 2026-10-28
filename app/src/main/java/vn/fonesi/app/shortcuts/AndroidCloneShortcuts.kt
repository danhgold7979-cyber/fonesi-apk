package vn.fonesi.app.shortcuts

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import androidx.core.graphics.createBitmap
import vn.fonesi.app.R
import vn.fonesi.app.MainActivity
import vn.fonesi.app.storage.CloneMetadataRow
import vn.fonesi.app.storage.CloneStylePolicy
import vn.fonesi.app.storage.CloneVisualStyle
import vn.fonesi.app.storage.FonesiDatabase

enum class PinResult { REQUEST_SENT, UNSUPPORTED, REJECTED }

/** Pin requests only: no dynamic-shortcut quota imposed as a product clone limit. */
class AndroidCloneShortcuts(private val context: Context, private val db: FonesiDatabase) {
    private val manager get() = context.getSystemService(ShortcutManager::class.java)

    /** Call in response to a foreground user action. True means requested, not pinned by the user. */
    fun requestPin(clone: CloneMetadataRow): PinResult {
        val service = manager ?: return PinResult.UNSUPPORTED
        if (!service.isRequestPinShortcutSupported) return PinResult.UNSUPPORTED
        return if (service.requestPinShortcut(info(clone), null)) PinResult.REQUEST_SENT else PinResult.REJECTED
    }

    /** Background thread. Only missing metadata disables a shortcut; a missing engine is temporary. */
    fun synchronizePinned(forceIcons: Boolean = false) {
        val service = manager ?: return
        val updates = mutableListOf<ShortcutInfo>()
        val stale = mutableListOf<String>()
        service.pinnedShortcuts.forEach { pinned ->
            val token = CloneRoute.tokenFromShortcutId(pinned.id) ?: return@forEach
            val saved = db.clones().findByLaunchToken(token)
            if (saved == null) stale += pinned.id
            else if (forceIcons || pinned.shortLabel?.toString() != shortLabel(saved.label) || pinned.longLabel?.toString() != saved.label) {
                updates += info(saved)
            }
        }
        if (stale.isNotEmpty()) service.disableShortcuts(stale, "Bản clone này đã bị xóa. Mở Fonesi để chọn bản khác.")
        if (updates.isNotEmpty()) service.updateShortcuts(updates)
    }

    internal fun info(clone: CloneMetadataRow): ShortcutInfo = ShortcutInfo.Builder(context, CloneRoute.shortcutId(clone.launchToken))
        .setShortLabel(shortLabel(clone.label))
        .setLongLabel(clone.label)
        .setActivity(android.content.ComponentName(context, MainActivity::class.java))
        .setIntent(CloneRoute.intent(context, clone.launchToken))
        .setIcon(icon(clone))
        .build()

    private fun shortLabel(label: String): String = label.substring(0, label.offsetByCodePoints(0, minOf(40, label.codePointCount(0, label.length))))

    private fun icon(clone: CloneMetadataRow): Icon = try {
        val style = CloneStylePolicy.normalize(clone.accentKey, clone.badgeText)
        val drawable = context.packageManager.getApplicationIcon(clone.packageName).mutate()
        val bitmap = createBitmap(144, 144)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, 144, 144)
        drawable.draw(canvas)
        val accent = when (style.accentKey) {
            "teal" -> Color.rgb(18, 107, 85)
            "blue" -> Color.rgb(38, 91, 184)
            "purple" -> Color.rgb(114, 61, 179)
            "amber" -> Color.rgb(165, 100, 9)
            "red" -> Color.rgb(184, 54, 65)
            "slate" -> Color.rgb(85, 98, 119)
            else -> error("Unsupported accent")
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawCircle(118f, 118f, 26f, paint)
        paint.color = Color.WHITE
        paint.textSize = 32f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val badge = CloneStylePolicy.displayBadge(style)
        if (badge.codePointCount(0, badge.length) == 2) paint.textSize = 23f
        canvas.drawText(badge, 118f, 129f, paint)
        Icon.createWithBitmap(bitmap)
    } catch (_: Exception) { Icon.createWithResource(context, R.drawable.ic_fonesi) }
}
