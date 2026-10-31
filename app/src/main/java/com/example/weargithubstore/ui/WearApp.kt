package com.example.weargithubstore.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.weargithubstore.ui.announcements.AnnouncementsScreen
import com.example.weargithubstore.ui.assets.AssetsScreen
import com.example.weargithubstore.ui.downloads.DownloadsScreen
import com.example.weargithubstore.ui.feedback.FeedbackScreen
import com.example.weargithubstore.ui.filemanager.FileManagerScreen
import com.example.weargithubstore.ui.detail.RepoDetailScreen
import com.example.weargithubstore.ui.home.HomeScreen
import com.example.weargithubstore.ui.inbox.InboxScreen
import com.example.weargithubstore.ui.library.FavoritesScreen
import com.example.weargithubstore.ui.library.HistoryScreen
import com.example.weargithubstore.ui.logs.LogsScreen
import com.example.weargithubstore.ui.market.MarketScreen
import com.example.weargithubstore.ui.releases.ReleasesScreen
import com.example.weargithubstore.ui.search.SearchResultScreen
import com.example.weargithubstore.ui.search.SearchScreen
import com.example.weargithubstore.ui.settings.SettingsScreen
import com.example.weargithubstore.ui.testhub.TestHubScreen
import com.example.weargithubstore.ui.theme.WearGitHubStoreTheme
import com.example.weargithubstore.ui.update.UpdateScreen
import com.example.weargithubstore.ui.upload.UploadScreen
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
            composable("market") {
                MarketScreen(navController)
            }
            composable("testhub") {
                TestHubScreen(navController)
            }
            composable("favorites") {
                FavoritesScreen(navController)
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
