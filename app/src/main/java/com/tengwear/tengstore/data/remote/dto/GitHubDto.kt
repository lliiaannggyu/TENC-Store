package com.tengwear.tengstore.data.remote.dto

import com.tengwear.tengstore.domain.model.Asset
import com.tengwear.tengstore.domain.model.Release
import com.tengwear.tengstore.domain.model.Repo
import com.google.gson.annotations.SerializedName

data class SearchRepositoriesResponse(
    @SerializedName("total_count") val totalCount: Int = 0,
    @SerializedName("items") val items: List<RepoDto> = emptyList(),
)

data class RepoDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("description") val description: String?,
    @SerializedName("stargazers_count") val stargazersCount: Int,
    @SerializedName("language") val language: String?,
    @SerializedName("owner") val owner: OwnerDto,
    @SerializedName("html_url") val htmlUrl: String,
)

data class OwnerDto(
    @SerializedName("login") val login: String,
    @SerializedName("avatar_url") val avatarUrl: String,
)

data class ReleaseDto(
    @SerializedName("id") val id: Long,
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String?,
    @SerializedName("body") val body: String?,
    @SerializedName("published_at") val publishedAt: String?,
    @SerializedName("prerelease") val prerelease: Boolean = false,
    @SerializedName("draft") val draft: Boolean = false,
    @SerializedName("assets") val assets: List<AssetDto> = emptyList(),
)

data class AssetDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("size") val size: Long,
    @SerializedName("browser_download_url") val browserDownloadUrl: String,
    @SerializedName("content_type") val contentType: String?,
    @SerializedName("created_at") val createdAt: String?,
)

fun RepoDto.toDomain(): Repo = Repo(
    id = id,
    name = name,
    fullName = fullName,
    owner = owner.login,
    ownerAvatarUrl = owner.avatarUrl,
    description = description,
    stars = stargazersCount,
    language = language,
)

fun ReleaseDto.toDomain(): Release = Release(
    id = id,
    tagName = tagName,
    name = name,
    body = body,
    publishedAt = publishedAt,
    prerelease = prerelease,
    draft = draft,
    assets = assets.map { it.toDomain() },
)

fun AssetDto.toDomain(): Asset = Asset(
    id = id,
    name = name,
    size = size,
    downloadUrl = browserDownloadUrl,
    contentType = contentType,
    createdAt = createdAt,
)
