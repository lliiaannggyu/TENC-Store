package com.tengwear.tengstore.data.repository

import com.tengwear.tengstore.data.remote.GitHubApiService
import com.tengwear.tengstore.data.remote.dto.toDomain
import com.tengwear.tengstore.domain.model.Asset
import com.tengwear.tengstore.domain.model.Release
import com.tengwear.tengstore.domain.model.Repo

/**
 * Single source of truth for GitHub data. Keeps the rest of the app
 * agnostic of Retrofit / network details.
 */
class GitHubRepository(private val api: GitHubApiService) {

    suspend fun search(query: String): List<Repo> {
        val q = query.trim()
        require(q.isNotBlank()) { "Query must not be blank" }
        return api.searchRepositories(q).items.map { it.toDomain() }
    }

    suspend fun releases(owner: String, repo: String): List<Release> {
        return api.listReleases(owner, repo)
            .map { it.toDomain() }
            .filterNot { it.draft }
    }

    /** 列出当前令牌账号下的仓库（应用上传：选择目标仓库）。 */
    suspend fun myRepos(): List<Repo> {
        return api.listMyRepos().map { it.toDomain() }
    }

    /** 以文本形式读取仓库内文件（测试项目索引 repos.txt）。 */
    suspend fun rawFile(owner: String, repo: String, path: String): String {
        return api.rawFile(
            accept = "application/vnd.github.raw",
            owner = owner,
            repo = repo,
            path = path,
        ).string()
    }

    // Kept for documentation: which artifacts we consider downloadable.
    private fun Release.apkAssets(): List<Asset> = assets.filter { it.isApk }
}
