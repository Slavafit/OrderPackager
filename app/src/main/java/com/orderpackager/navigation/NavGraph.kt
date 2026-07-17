package com.orderpackager.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.orderpackager.repository.AppRepository
import com.orderpackager.ui.ThemeMode
import com.orderpackager.ui.screen.*

sealed class Route(val path: String) {
    object ClientSelection : Route("client_selection")
    object WorkingScreen   : Route("working/{orderId}/{clientName}") {
        fun create(orderId: Long, clientName: String) = "working/$orderId/$clientName"
    }
    object OrderCompletion : Route("order_completion/{orderId}/{printSummaryLabel}") {
        fun create(orderId: Long, printSummaryLabel: Boolean) = "order_completion/$orderId/$printSummaryLabel"
    }
    object EditClients     : Route("edit_clients")
    object EditCyclicList  : Route("edit_cyclic_list")
    object OrderHistory    : Route("order_history")
    object Settings        : Route("settings")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    themeMode:     ThemeMode,
    onThemeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val repo    = AppRepository.getInstance(context)

    NavHost(navController = navController, startDestination = Route.ClientSelection.path) {

        composable(Route.ClientSelection.path) {
            ClientSelectionScreen(
                repo          = repo,
                onStartWork   = { orderId, clientName ->
                    navController.navigate(Route.WorkingScreen.create(orderId, clientName))
                },
                onEditClients = { navController.navigate(Route.EditClients.path) },
                onEditCyclic  = { navController.navigate(Route.EditCyclicList.path) },
                onHistory     = { navController.navigate(Route.OrderHistory.path) },
                onSettings    = { navController.navigate(Route.Settings.path) }
            )
        }

        composable(
            route     = Route.WorkingScreen.path,
            arguments = listOf(
                navArgument("orderId")     { type = NavType.LongType },
                navArgument("clientName")  { type = NavType.StringType }
            )
        ) { back ->
            val orderId    = back.arguments!!.getLong("orderId")
            val clientName = back.arguments!!.getString("clientName") ?: ""
            WorkingScreen(
                repo          = repo,
                orderId       = orderId,
                clientName    = clientName,
                onFinishOrder = { printSummaryLabel ->
                    navController.navigate(Route.OrderCompletion.create(orderId, printSummaryLabel)) {
                        popUpTo(Route.ClientSelection.path)
                    }
                },
                onBack        = { navController.popBackStack() }
            )
        }

        composable(
            route     = Route.OrderCompletion.path,
            arguments = listOf(
                navArgument("orderId") { type = NavType.LongType },
                navArgument("printSummaryLabel") { type = NavType.BoolType }
            )
        ) { back ->
            val orderId = back.arguments!!.getLong("orderId")
            val printSummaryLabel = back.arguments!!.getBoolean("printSummaryLabel")
            OrderCompletionScreen(
                repo              = repo,
                orderId           = orderId,
                printSummaryLabel = printSummaryLabel,
                onDone            = {
                    navController.navigate(Route.ClientSelection.path) {
                        popUpTo(Route.ClientSelection.path) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.EditClients.path) {
            EditClientsScreen(repo = repo, onBack = { navController.popBackStack() })
        }

        composable(Route.EditCyclicList.path) {
            EditCyclicListScreen(repo = repo, onBack = { navController.popBackStack() })
        }

        composable(Route.OrderHistory.path) {
            OrderHistoryScreen(
                repo = repo,
                onBack = { navController.popBackStack() },
                onContinueOrder = { orderId, clientName ->
                    navController.navigate(Route.WorkingScreen.create(orderId, clientName))
                }
            )
        }

        composable(Route.Settings.path) {
            SettingsScreen(
                currentTheme  = themeMode,
                onThemeChange = onThemeChange,
                onBack        = { navController.popBackStack() }
            )
        }
    }
}
