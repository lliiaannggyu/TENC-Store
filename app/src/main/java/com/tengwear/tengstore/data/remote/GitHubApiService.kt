package com.tengwear.tengstore.data.remote

import com.tengwear.tengstore.data.remote.dto.ReleaseDto
import com.tengwear.tengstore.data.remote.dto.RepoDto
import com.tengwear.tengstore.data.remote.dto.RepoPermissionDto
import com.tengwear.tengstore.data.remote.dto.SearchRepositoriesResponse
import com.tengwear.tengstore.data.remote.dto.SearchUsersResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Minimal GitHub REST API surface used by the app:
 *  - search public repositories (by stars)
 *  - list releases for a repository (which contain the binary assets)
 *  - fetch raw file content (测试项目索引仓库的 repos.txt)
 */
interface GitHubApiService {
    @GET("search/repositories")
    suspend fun searchRepositories(
        @Query("q") query: String,
        @Query("sort") sort: String? = null,
        @Query("order") order: String? = null,
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
    ): SearchRepositoriesResponse

    /** 搜索 GitHub 用户（按用户名匹配）。 */
    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("per_page") perPage: Int = 30,
    ): SearchUsersResponse

    /** 列出指定用户的公开仓库（用户仓库浏览）。 */
    @GET("users/{user}/repos")
    suspend fun listUserRepos(
        @Path("user") user: String,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100,
    ): List<RepoDto>

    /** 查询仓库及当前令牌对其的权限（管理入口验证用，不内置任何令牌）。 */
    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): RepoPermissionDto

    @GET("repos/{owner}/{repo}/releases")
    suspend fun listReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20,
    ): List<ReleaseDto>

    /** 列出当前令牌账号下的仓库（应用上传：选择目标仓库）。 */
    @GET("user/repos")
    suspend fun listMyRepos(
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated",
    ): List<RepoDto>

    /** 以原始文本形式读取仓库内文件（如索引仓库的 repos.txt）。 */
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun rawFile(
        @Header("Accept") accept: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path", encoded = true) path: String,
    ): ResponseBody
}
