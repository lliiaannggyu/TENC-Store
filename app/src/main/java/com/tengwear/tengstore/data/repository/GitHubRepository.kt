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

    suspend fun search(query: String, page: Int = 1, perPage: Int = 30): List<Repo> {
        val q = query.trim()
        require(q.isNotBlank()) { "Query must not be blank" }
        return api.searchRepositories(q, page = page, perPage = perPage).items.map { it.toDomain() }
    }

    /**
     * 校验当前令牌对仓库是否有推送/管理权限。
     * 返回 true=有权限；false=无权限（仓库不存在或非协作者）；null=无法验证（网络等）。
     * 注意：用调用者自己已登录的令牌验证，应用内不保存任何内置令牌。
     */
    suspend fun checkPushAccess(owner: String, repo: String): Boolean? {
        return try {
            val dto = api.getRepo(owner, repo)
            val perms = dto.permissions
            perms?.push == true || perms?.admin == true
        } catch (e: retrofit2.HttpException) {
            if (e.code() in listOf(401, 403, 404)) false else null
        } catch (e: Exception) {
            null
        }
    }

    /** 搜索 GitHub 用户，返回用户名列表。 */
    suspend fun searchUsers(query: String): List<String> {
        val q = query.trim()
        require(q.isNotBlank()) { "Query must not be blank" }
        return api.searchUsers(q).items.map { it.login }
    }

    /** 列出指定用户的仓库（含私有仓库，需令牌有权限时 GitHub 自动附带）。 */
    suspend fun userRepos(user: String): List<Repo> {
        return api.listUserRepos(user).map { it.toDomain() }
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
