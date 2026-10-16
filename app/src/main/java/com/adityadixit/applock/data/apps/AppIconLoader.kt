package com.adityadixit.applock.data.apps

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.adityadixit.applock.core.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads app icons off the main thread at a fixed size and keeps a small LRU cache, so scrolling
 * a list of hundreds of apps never decodes adaptive icons on the UI thread.
 */
@Singleton
class AppIconLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val sizePx = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
    private val cache = LruCache<String, ImageBitmap>(MAX_CACHED_ICONS)

    suspend fun load(packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return withContext(ioDispatcher) {
            try {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(sizePx, sizePx)
                    .asImageBitmap()
                    .also { cache.put(packageName, it) }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    suspend fun loadLabel(packageName: String): String = withContext(ioDispatcher) {
        try {
            val pm = context.packageManager
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private companion object {
        const val ICON_SIZE_DP = 48
        const val MAX_CACHED_ICONS = 200
    }
}
