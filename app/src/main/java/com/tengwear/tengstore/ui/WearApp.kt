package com.tengwear.tengstore.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.tengwear.tengstore.ui.announcements.AnnouncementDetailScreen
import com.tengwear.tengstore.ui.announcements.AnnouncementsScreen
import com.tengwear.tengstore.ui.assets.AssetsScreen
import com.tengwear.tengstore.ui.downloads.DownloadsScreen
import com.tengwear.tengstore.ui.feedback.FeedbackScreen
import com.tengwear.tengstore.ui.filemanager.FileManagerScreen
import com.tengwear.tengstore.ui.detail.RepoDetailScreen
import com.tengwear.tengstore.ui.home.HomeScreen
import com.tengwear.tengstore.ui.inbox.InboxScreen
import com.tengwear.tengstore.ui.library.FavoritesScreen
import com.tengwear.tengstore.ui.library.HistoryScreen
import com.tengwear.tengstore.ui.logs.LogsScreen
import com.tengwear.tengstore.ui.market.MarketScreen
import com.tengwear.tengstore.ui.releases.ReleasesScreen
import com.tengwear.tengstore.ui.search.SearchResultScreen
import com.tengwear.tengstore.ui.search.SearchScreen
import com.tengwear.tengstore.ui.settings.SettingsScreen
import com.tengwear.tengstore.ui.testhub.TestHubScreen
import com.tengwear.tengstore.ui.theme.WearGitHubStoreTheme
import com.tengwear.tengstore.ui.update.UpdateScreen
import com.tengwear.tengstore.ui.upload.UploadScreen
import java.net.URLDecoder

/**
 * 根导航：商店无需口令，启动即进主菜单。
 *
 * 路由：
 *  - home                        : 主菜单
 *  - search                      : 搜索输入页
 *  - search_results/{query}      : 搜索结果页（独立页面，含加载态）
 *  - testhub                     : 测试项目（项目详情：下载/反馈）
 *  - feedback/{fullName}/{mail}  : 项目反馈（建议/Bug，发项目开发者）
 *  - releases/{fullName}         : 仓库 Release 列表
 *  - assets/{releaseId}          : Release 的 APK 产物与下载
 *  - downloads                   : 我的下载
 *  - announcements               : 公告（公告索引仓库内容）
 *  - inbox                       : 信息（接收日志邮件，口令校验）
 *  - logs                        : 日志反馈（复制/邮箱发送）
 *  - market / testhub / upload   : 市场 / 测试项目 / 应用上传
 *  - settings                    : 设置
 */
@Composable
fun WearApp() {
    WearGitHubStoreTheme {
        val navController: NavHostController = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = "home",
        ) {
            composable("home") {
                HomeScreen(navController)
            }
            composable("search") {
                SearchScreen(navController)
            }
            composable("search_results/{query}") { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query")
                    ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                    .orEmpty()
                SearchResultScreen(navController, query)
            }
            composable("user_search/{query}") { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query")
                    ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                    .orEmpty()
                com.tengwear.tengstore.ui.users.UserSearchScreen(navController, query)
            }
            composable("user_repos/{user}") { backStackEntry ->
                val user = backStackEntry.arguments?.getString("user")
                    ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                    .orEmpty()
                com.tengwear.tengstore.ui.users.UserReposScreen(navController, user)
            }
            composable("market") {
                MarketScreen(navController)
            }
            composable("testhub") {
                TestHubScreen(navController)
            }
            composable("favorites") {
                FavoritesScreen(navController)
            }
            composable("favorite_users") {
                com.tengwear.tengstore.ui.library.FavoriteUsersScreen(navController)
            }
            composable("history") {
                HistoryScreen(navController)
            }
            composable("detail/{fullName}") { backStackEntry ->
                val fullName =
                    (backStackEntry.arguments?.getString("fullName") ?: "").replace("~~", "/")
                RepoDetailScreen(navController, fullName)
            }
            composable("feedback/{fullName}/{devEmail}") { backStackEntry ->
                val fullName = (backStackEntry.arguments?.getString("fullName") ?: "").replace("~~", "/")
                val devEmail = backStackEntry.arguments?.getString("devEmail")
                    ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                    ?.takeIf { it != "none" }
                    .orEmpty()
                FeedbackScreen(navController, fullName, devEmail)
            }
            composable("upload") {
                UploadScreen(navController)
            }
            composable("submit") {
                com.tengwear.tengstore.ui.submit.SubmitScreen(navController)
            }
            composable("filemanager") {
                FileManagerScreen(navController)
            }
            composable("releases/{fullName}") { backStackEntry ->
                val fullName =
                    (backStackEntry.arguments?.getString("fullName") ?: "").replace("~~", "/")
                ReleasesScreen(navController, fullName)
            }
            composable("assets/{releaseId}") { backStackEntry ->
                val releaseId =
                    backStackEntry.arguments?.getString("releaseId")?.toLongOrNull() ?: -1L
                AssetsScreen(navController, releaseId)
            }
            composable("downloads") {
                DownloadsScreen(navController)
            }
            composable("announcements") {
                AnnouncementsScreen(navController)
            }
            composable("announcement/{index}") { backStackEntry ->
                val index = backStackEntry.arguments?.getString("index")?.toIntOrNull() ?: -1
                AnnouncementDetailScreen(navController, index)
            }
            composable("inbox") {
                InboxScreen(navController)
            }
            composable("logs") {
                LogsScreen(navController)
            }
            composable("settings") {
                SettingsScreen(navController)
            }
            composable("update") {
                UpdateScreen(navController)
            }
        }
    }
}
