package com.tengwear.tengstore.domain.model

import android.os.Parcel
import android.os.Parcelable

/**
 * A GitHub repository returned by the search endpoint.
 */
data class Repo(
    val id: Long,
    val name: String,
    val fullName: String,          // owner/repo
    val owner: String,
    val ownerAvatarUrl: String,
    val description: String?,
    val stars: Int,
    val language: String?,
) : Parcelable {
    private constructor(p: Parcel) : this(
        p.readLong(),
        p.readString().orEmpty(),
        p.readString().orEmpty(),
        p.readString().orEmpty(),
        p.readString().orEmpty(),
        p.readString(),
        p.readInt(),
        p.readString(),
    )

    override fun describeContents() = 0

    override fun writeToParcel(p: Parcel, flags: Int) {
        p.writeLong(id)
        p.writeString(name)
        p.writeString(fullName)
        p.writeString(owner)
        p.writeString(ownerAvatarUrl)
        p.writeString(description)
        p.writeInt(stars)
        p.writeString(language)
    }

    companion object CREATOR : Parcelable.Creator<Repo> {
        override fun createFromParcel(p: Parcel) = Repo(p)
        override fun newArray(size: Int) = arrayOfNulls<Repo>(size)
    }
}

/**
 * A GitHub release. [apkAssets] are the artifacts we actually care about.
 */
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

    private constructor(p: Parcel) : this(
        p.readLong(),
        p.readString().orEmpty(),
        p.readString(),
        p.readString(),
        p.readString(),
        p.readByte() != 0.toByte(),
        p.readByte() != 0.toByte(),
        p.createTypedArrayList(Asset.CREATOR).orEmpty(),
    )

    override fun describeContents() = 0

    override fun writeToParcel(p: Parcel, flags: Int) {
        p.writeLong(id)
        p.writeString(tagName)
        p.writeString(name)
        p.writeString(body)
        p.writeString(publishedAt)
        p.writeByte(if (prerelease) 1 else 0)
        p.writeByte(if (draft) 1 else 0)
        p.writeTypedList(assets)
    }

    companion object CREATOR : Parcelable.Creator<Release> {
        override fun createFromParcel(p: Parcel) = Release(p)
        override fun newArray(size: Int) = arrayOfNulls<Release>(size)
    }
}

/**
 * A release artifact (asset). We only download APK-style binaries.
 */
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

    private constructor(p: Parcel) : this(
        p.readLong(),
        p.readString().orEmpty(),
        p.readLong(),
        p.readString().orEmpty(),
        p.readString(),
        p.readString(),
    )

    override fun describeContents() = 0

    override fun writeToParcel(p: Parcel, flags: Int) {
        p.writeLong(id)
        p.writeString(name)
        p.writeLong(size)
        p.writeString(downloadUrl)
        p.writeString(contentType)
        p.writeString(createdAt)
    }

    companion object CREATOR : Parcelable.Creator<Asset> {
        override fun createFromParcel(p: Parcel) = Asset(p)
        override fun newArray(size: Int) = arrayOfNulls<Asset>(size)
    }
}
