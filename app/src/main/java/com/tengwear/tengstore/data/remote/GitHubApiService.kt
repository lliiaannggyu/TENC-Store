package com.tengwear.tengstore.data.remote

import com.tengwear.tengstore.data.remote.dto.ReleaseDto
import com.tengwear.tengstore.data.remote.dto.RepoDto
import com.tengwear.tengstore.data.remote.dto.SearchRepositoriesResponse
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
        @Query("sort") sort: String = "stars",
        @Query("order") order: String = "desc",
        @Query("per_page") perPage: Int = 30,
    ): SearchRepositoriesResponse

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
