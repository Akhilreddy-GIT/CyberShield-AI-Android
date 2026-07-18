package com.cybershield.ai.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cybershield.ai.presentation.navigation.Screen
import com.cybershield.ai.presentation.theme.Emerald
import com.cybershield.ai.presentation.theme.HairlineBorder
import com.cybershield.ai.presentation.theme.ObsidianSurfaceContainerLowest
import com.cybershield.ai.presentation.theme.OnEmerald
import com.cybershield.ai.presentation.theme.TextMuted

enum class MainTab(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home("Home", Screen.Home.route, Icons.Filled.Home, Icons.Outlined.Home),
    Guardian("Guardian", "chat?caseId=", Icons.Filled.Shield, Icons.Outlined.Shield),
    Crisis("Crisis", Screen.Emergency.route, Icons.Filled.Place, Icons.Outlined.Place),
    Vault("Vault", "vault", Icons.Filled.FolderSpecial, Icons.Outlined.FolderSpecial),
    Cases("Cases", Screen.Cases.route, Icons.Filled.MenuBook, Icons.Outlined.MenuBook),
}

@Composable
fun CyberShieldBottomBar(
    currentRoute: String?,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = MainTab.entries.find { tab ->
        when (tab) {
            MainTab.Guardian -> currentRoute?.startsWith("chat") == true
            MainTab.Vault -> currentRoute == "vault" || currentRoute?.startsWith("evidence") == true
            else -> currentRoute == tab.route
        }
    } ?: MainTab.Home

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .border(1.dp, HairlineBorder),
        color = ObsidianSurfaceContainerLowest,
        shape = RoundedCornerShape(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                BottomTabItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onTabSelected(tab) },
                )
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val pillWidth by animateDpAsState(
        targetValue = if (selected) 64.dp else 48.dp,
        animationSpec = tween(250),
        label = "pillW",
    )
    val bg by animateColorAsState(
        targetValue = if (selected) Emerald else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(250),
        label = "pillBg",
    )
    // Per DESIGN.md: primary buttons use solid Emerald fill with black text,
    // not white — the selected tab icon follows the same rule.
    val contentColor by animateColorAsState(
        targetValue = if (selected) OnEmerald else TextMuted,
        label = "tabFg",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .width(pillWidth)
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = tab.label,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            tab.label.uppercase(),
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Emerald else TextMuted,
        )
    }
}

fun isMainTabRoute(route: String?): Boolean {
    if (route == null) return false
    return route == Screen.Home.route ||
        route.startsWith("chat") ||
        route == Screen.Emergency.route ||
        route == "vault" ||
        route == Screen.Cases.route
}
