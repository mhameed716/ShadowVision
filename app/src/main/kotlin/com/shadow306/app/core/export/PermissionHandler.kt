package com.shadow306.app.core.export

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Handles runtime permissions for Shadow306.
 *
 * On Android 13+ (API 33+), Photo Picker needs NO permissions.
 * On Android 12 and below, we need READ_EXTERNAL_STORAGE for gallery.
 * POST_NOTIFICATIONS is needed for download progress (Android 13+).
 */
object PermissionHandler {

    fun needsGalleryPermission(): Boolean = Build.VERSION.SDK_INT <= 32

    fun hasGalleryPermission(context: Context): Boolean {
        if (!needsGalleryPermission()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun galleryPermission(): String = Manifest.permission.READ_EXTERNAL_STORAGE
    fun notificationPermission(): String = Manifest.permission.POST_NOTIFICATIONS
}
