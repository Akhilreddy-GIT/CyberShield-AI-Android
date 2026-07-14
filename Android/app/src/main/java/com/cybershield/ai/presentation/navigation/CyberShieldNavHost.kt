package com.cybershield.ai.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cybershield.ai.presentation.auth.AccountScreen
import com.cybershield.ai.presentation.awareness.AwarenessScreen
import com.cybershield.ai.presentation.cases.CaseDetailScreen
import com.cybershield.ai.presentation.cases.CasesScreen
import com.cybershield.ai.presentation.chat.ChatScreen
import com.cybershield.ai.presentation.emergency.EmergencyScreen
import com.cybershield.ai.presentation.evidence.CameraCaptureScreen
import com.cybershield.ai.presentation.evidence.EvidenceScreen
import com.cybershield.ai.presentation.home.HomeScreen
import com.cybershield.ai.presentation.legal.LegalScreen
import com.cybershield.ai.presentation.report.ReportScreen
import com.cybershield.ai.presentation.risk.RiskScreen
import com.cybershield.ai.presentation.timeline.TimelineScreen

@Composable
fun CyberShieldNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onChat = { caseId -> navController.navigate(Screen.Chat.create(caseId)) },
                onCases = { navController.navigate(Screen.Cases.route) },
                onAccount = { navController.navigate(Screen.Account.route) },
                onLegal = { navController.navigate(Screen.Legal.route) },
                onEmergency = { navController.navigate(Screen.Emergency.route) },
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
                }
            ),
        ) {
            ChatScreen(
                onBack = { navController.popBackStack() },
                onOpenCase = { id -> navController.navigate(Screen.CaseDetail.create(id)) },
            )
        }

        composable(Screen.Cases.route) {
            CasesScreen(
                onBack = { navController.popBackStack() },
                onOpenCase = { id -> navController.navigate(Screen.CaseDetail.create(id)) },
            )
        }

        composable(
            route = Screen.CaseDetail.route,
            arguments = listOf(navArgument("caseId") { type = NavType.StringType }),
        ) {
            CaseDetailScreen(
                onBack = { navController.popBackStack() },
                onChat = { id -> navController.navigate(Screen.Chat.create(id)) },
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
        composable(Screen.Legal.route) {
            LegalScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Emergency.route) {
            EmergencyScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Awareness.route) {
            AwarenessScreen(onBack = { navController.popBackStack() })
        }
    }
}
