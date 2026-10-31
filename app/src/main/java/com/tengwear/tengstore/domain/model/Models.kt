package com.tengwear.tengstore.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A GitHub repository returned by the search endpoint.
 */
@Parcelize
data class Repo(
    val id: Long,
    val name: String,
    val fullName: String,          // owner/repo
    val owner: String,
    val ownerAvatarUrl: String,
    val description: String?,
    val stars: Int,
    val language: String?,
) : Parcelable

/**
 * A GitHub release. [apkAssets] are the artifacts we actually care about.
 */
@Parcelize
data class Release(
    val id: Long,
    val tagName: String,
    val name: String?,
    val body: String?,
    val publishedAt: String?,
    val prerelease: Boolean,
    val draft: Boolean,
    val assets: List<Asset>,
) : Parcelable {
    val apkAssets: List<Asset>
        get() = assets.filter { it.isApk }
}

/**
 * A release artifact (asset). We only download APK-style binaries.
 */
@Parcelize
data class Asset(
    val id: Long,
    val name: String,
    val size: Long,
    val downloadUrl: String,
    val contentType: String?,
    val createdAt: String?,
) : Parcelable {
    val isApk: Boolean
        get() = name.endsWith(".apk", ignoreCase = true) ||
            contentType == "application/vnd.android.package-archive"
}
