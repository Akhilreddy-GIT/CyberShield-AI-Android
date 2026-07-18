package com.cybershield.ai.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cybershield.ai.presentation.auth.AccountScreen
import com.cybershield.ai.presentation.awareness.AwarenessScreen
import com.cybershield.ai.presentation.cases.CaseDetailScreen
import com.cybershield.ai.presentation.cases.CasesScreen
import com.cybershield.ai.presentation.chat.ChatScreen
import com.cybershield.ai.presentation.components.CyberShieldBottomBar
import com.cybershield.ai.presentation.components.MainTab
import com.cybershield.ai.presentation.components.isMainTabRoute
import com.cybershield.ai.presentation.emergency.EmergencyScreen
import com.cybershield.ai.presentation.evidence.CameraCaptureScreen
import com.cybershield.ai.presentation.evidence.EvidenceScreen
import com.cybershield.ai.presentation.evidence.VaultHubScreen
import com.cybershield.ai.presentation.home.HomeScreen
import com.cybershield.ai.presentation.legal.LegalScreen
import com.cybershield.ai.presentation.report.ReportScreen
import com.cybershield.ai.presentation.risk.RiskScreen
import com.cybershield.ai.presentation.timeline.TimelineScreen

/**
 * Navigates to a top-level ("main tab") destination — Home, Guardian
 * (Chat), Crisis, Vault, or Cases — using consistent single-top / save-and-
 * restore-state semantics every time, regardless of WHICH screen initiated
 * the navigation (bottom bar tap, a button on Home, "Open chat" from a case
 * detail screen, etc).
 *
 * This is the fix for the "tab becomes unclickable" / navigation dead-end
 * bug: previously only the bottom bar's own onTabSelected applied
 * popUpTo+launchSingleTop+restoreState, while other call sites (Home's
 * "start conversation", CaseDetail's "Open chat") did a plain navigate()
 * that pushed a brand new Chat destination on top of the existing one.
 * Because Chat's route carries a query argument (chat?caseId=...), two
 * different case ids produce two different route strings, so
 * launchSingleTop alone can't dedupe them — the back stack would keep
 * growing every time chat was opened from a case, eventually leaving
 * Compose Navigation's saved/restored state pointing at a detached entry
 * and making Home/Cases taps stop navigating.
 *
 * Routing every top-level navigation through this one helper guarantees
 * there is ever only one Chat (or Home, or Cases, ...) entry alive in the
 * back stack at a time, exactly like switching tabs in a normal bottom-nav
 * app — the previous tab's state is saved, not destroyed, and restored
 * when the user comes back to it.
 */
private fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun CyberShieldNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = isMainTabRoute(currentRoute)

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                CyberShieldBottomBar(
                    currentRoute = currentRoute,
                    onTabSelected = { tab ->
                        val route = when (tab) {
                            MainTab.Guardian -> Screen.Chat.create(null)
                            else -> tab.route
                        }
                        navController.navigateToTab(route)
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (showBottomBar) Modifier.padding(bottom = padding.calculateBottomPadding())
                    else Modifier.padding(padding),
                ),
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route,
                enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
                exitTransition = { fadeOut() + slideOutHorizontally { -it / 16 } },
                popEnterTransition = { fadeIn() + slideInHorizontally { -it / 12 } },
                popExitTransition = { fadeOut() + slideOutHorizontally { it / 16 } },
            ) {
                composable(Screen.Splash.route) {
                    val accountViewModel: com.cybershield.ai.presentation.auth.AccountViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    com.cybershield.ai.presentation.auth.SplashScreen(
                        authTokenFlow = accountViewModel.authTokenFlow,
                        onNavigate = { hasToken ->
                            val dest = if (hasToken) Screen.Home.route else Screen.Login.route
                            navController.navigate(dest) {
                                popUpTo(Screen.Splash.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Login.route) {
                    com.cybershield.ai.presentation.auth.LoginScreen(
                        onLoginSuccess = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Login.route) { inclusive = true }
                            }
                        },
                        onNavigateRegister = {
                            navController.navigate(Screen.Account.route)
                        }
                    )
                }

                composable(Screen.Home.route) {
                    HomeScreen(
                        onChat = { caseId -> navController.navigateToTab(Screen.Chat.create(caseId)) },
                        onCases = { navController.navigateToTab(Screen.Cases.route) },
                        onAccount = { navController.navigate(Screen.Profile.route) },
                        onLegal = { navController.navigate(Screen.Legal.route) },
                        onEmergency = { navController.navigateToTab(Screen.Emergency.route) },
                        onAwareness = { navController.navigate(Screen.Awareness.route) },
                    )
                }

                composable(
                    route = "chat?caseId={caseId}",
                    arguments = listOf(
                        navArgument("caseId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    ChatScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCase = { id -> navController.navigate(Screen.CaseDetail.create(id)) },
                        showTopBack = !showBottomBar,
                    )
                }

                composable(Screen.Vault.route) {
                    VaultHubScreen(
                        onOpenEvidence = { id -> navController.navigate(Screen.Evidence.create(id)) },
                        onStartChat = { navController.navigateToTab(Screen.Chat.create(null)) },
                        onOpenCases = { navController.navigateToTab(Screen.Cases.route) },
                    )
                }

                composable(Screen.Cases.route) {
                    CasesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCase = { id -> navController.navigate(Screen.CaseDetail.create(id)) },
                        showTopBack = false,
                    )
                }

                composable(
                    route = Screen.CaseDetail.route,
                    arguments = listOf(navArgument("caseId") { type = NavType.StringType }),
                ) {
                    CaseDetailScreen(
                        onBack = { navController.popBackStack() },
                        onChat = { id -> navController.navigateToTab(Screen.Chat.create(id)) },
                        onEvidence = { id -> navController.navigate(Screen.Evidence.create(id)) },
                        onReport = { id -> navController.navigate(Screen.Report.create(id)) },
                        onRisk = { id -> navController.navigate(Screen.Risk.create(id)) },
                        onTimeline = { id -> navController.navigate(Screen.Timeline.create(id)) },
                    )
                }

                composable(
                    route = Screen.Evidence.route,
                    arguments = listOf(navArgument("caseId") { type = NavType.StringType }),
                ) {
                    EvidenceScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCamera = { id -> navController.navigate(Screen.Camera.create(id)) },
                        showTopBack = true,
                    )
                }

                composable(
                    route = Screen.Camera.route,
                    arguments = listOf(navArgument("caseId") { type = NavType.StringType }),
                ) {
                    CameraCaptureScreen(
                        onBack = { navController.popBackStack() },
                        onCapturedUploaded = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Screen.Report.route,
                    arguments = listOf(navArgument("caseId") { type = NavType.StringType }),
                ) {
                    ReportScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Screen.Risk.route,
                    arguments = listOf(navArgument("caseId") { type = NavType.StringType }),
                ) {
                    RiskScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Screen.Timeline.route,
                    arguments = listOf(navArgument("caseId") { type = NavType.StringType }),
                ) {
                    TimelineScreen(onBack = { navController.popBackStack() })
                }

                composable(Screen.Account.route) {
                    AccountScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Profile.route) {
                    com.cybershield.ai.presentation.profile.ProfileScreen(
                        onBack = { navController.popBackStack() },
                        onNavigatePrivacy = { navController.navigate(Screen.Privacy.route) },
                        onNavigateLegal = { navController.navigate(Screen.Legal.route) }
                    )
                }
                composable(Screen.Privacy.route) {
                    com.cybershield.ai.presentation.profile.PrivacyScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Screen.Legal.route) {
                    LegalScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Emergency.route) {
                    EmergencyScreen(onBack = { navController.popBackStack() }, showTopBack = false)
                }
                composable(Screen.Awareness.route) {
                    AwarenessScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
