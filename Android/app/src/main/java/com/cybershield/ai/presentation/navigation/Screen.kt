package com.cybershield.ai.presentation.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Login : Screen("login")
    data object Home : Screen("home")
    data object Chat : Screen("chat?caseId={caseId}") {
        fun create(caseId: String? = null) =
            if (caseId.isNullOrBlank()) "chat?caseId=" else "chat?caseId=$caseId"
    }
    data object Cases : Screen("cases")
    data object CaseDetail : Screen("cases/{caseId}") {
        fun create(caseId: String) = "cases/$caseId"
    }
    data object Evidence : Screen("evidence/{caseId}") {
        fun create(caseId: String) = "evidence/$caseId"
    }
    data object Camera : Screen("camera/{caseId}") {
        fun create(caseId: String) = "camera/$caseId"
    }
    data object Report : Screen("report/{caseId}") {
        fun create(caseId: String) = "report/$caseId"
    }
    data object Risk : Screen("risk/{caseId}") {
        fun create(caseId: String) = "risk/$caseId"
    }
    data object Timeline : Screen("timeline/{caseId}") {
        fun create(caseId: String) = "timeline/$caseId"
    }
    data object Vault : Screen("vault")
    data object Account : Screen("account")
    data object Profile : Screen("profile")
    data object Privacy : Screen("privacy")
    data object Legal : Screen("legal")
    data object Emergency : Screen("emergency")
    data object Awareness : Screen("awareness")
}

