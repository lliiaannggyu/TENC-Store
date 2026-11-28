package com.tengwear.tengstore.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

        // 使用协议：首次使用（或未确认）时先展示协议页，滑动到底部确认后才进主页
        val agreementAccepted by com.tengwear.tengstore.data.AppContainer.settings
            .agreementAccepted.collectAsState()

        // 剪贴板自动跳转：窗口获焦时检测（MainActivity 侧），命中「TENC Store 分享」
        // 内容后在首页自动跳到对应项目，并清空剪贴板避免重复跳转。
        // 检测到时若不在首页，等回到首页再补跳转（300ms 轮询，最多等 30 秒）。
        androidx.compose.runtime.LaunchedEffect(agreementAccepted) {
            if (!agreementAccepted) return@LaunchedEffect
            com.tengwear.tengstore.ui.util.ShareHelper.pendingJump.collect { repo ->
                if (repo == null) return@collect
                var waited = 0
                while (navController.currentDestination?.route != "home" && waited < 30_000) {
                    kotlinx.coroutines.delay(300)
                    waited += 300
                }
                if (navController.currentDestination?.route == "home") {
                    runCatching {
                        navController.navigate("detail/${repo.replace("/", "~~")}")
                    }
                    com.tengwear.tengstore.ui.util.ShareHelper.consumeJump(navController.context)
                }
            }
        }

        // 跨应用深链：其他应用 tencstore://app/... 拉起商店直达对应页面
        androidx.compose.runtime.LaunchedEffect(agreementAccepted) {
            if (!agreementAccepted) return@LaunchedEffect
            com.tengwear.tengstore.ui.util.DeepLink.pending.collect { route ->
                route?.let {
                    navController.navigate(it)
                    com.tengwear.tengstore.ui.util.DeepLink.consume()
                }
            }
        }

        SwipeDismissableNavHost(
            navController = navController,
            startDestination = if (agreementAccepted) "home" else "agreement",
        ) {
            composable("agreement") {
                com.tengwear.tengstore.ui.agreement.UsageAgreementScreen(
                    onAgreed = {
                        com.tengwear.tengstore.data.AppContainer.settings.acceptAgreement()
                        navController.navigate("home") {
                            popUpTo("agreement") { inclusive = true }
                        }
                    },
                )
            }
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
            // 测试项目详情（独立导航页）：从首页「测试」列表 / 测试项目页进入，
            // 右滑与返回键会 popBackStack 回到列表，而不是作用于整个首页。
            composable("project_detail/{fullName}") { backStackEntry ->
                val fullName =
                    (backStackEntry.arguments?.getString("fullName") ?: "").replace("~~", "/")
                com.tengwear.tengstore.ui.testhub.ProjectDetailRoute(navController, fullName)
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
            composable("auth") {
                com.tengwear.tengstore.ui.auth.AuthCenterScreen(navController)
            }
            composable("auth_pick") {
                com.tengwear.tengstore.ui.auth.AppPickerScreen(navController)
            }
            composable("update") {
                UpdateScreen(navController)
            }
        }

        // 全局返回拦截：右滑与返回键一律只「返回上一级」，绝不退出应用。
        // SwipeDismissableNavHost 内部在首页（backStack.size == 1）时会禁用自身的返回处理，
        // 此时返回/系统右滑手势会落到 Activity 默认行为，直接退出应用。
        // 这里在 NavHost 之后（组合顺序更靠后 = 优先级更高）注册一个始终启用的 BackHandler 接管：
        // 有上一级就 popBackStack 回到上一页；已在首页则消费事件、什么都不做，从而不退出。
        androidx.activity.compose.BackHandler(enabled = true) {
            if (navController.previousBackStackEntry != null) {
                navController.popBackStack()
            }
            // 已在首页/协议页：不调用 finish，事件被本 Handler 消费，应用保持驻留。
        }
    }
}
