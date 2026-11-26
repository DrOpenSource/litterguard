package app.litterguard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.litterguard.feature.incidents.IncidentsRoute
import app.litterguard.feature.monitor.MonitorRoute
import app.litterguard.feature.setup.AlertsRoute
import app.litterguard.feature.setup.HomeNavigation
import app.litterguard.feature.setup.HomeRoute
import app.litterguard.feature.setup.ZoneRoute

private object Destination {
    const val HOME = "home"
    const val ZONE = "zone"
    const val ALERTS = "alerts"
    const val MONITOR = "monitor"
    const val INCIDENTS = "incidents"
}

@Composable
fun LitterGuardNavHost(modifier: Modifier = Modifier) {
    val controller = rememberNavController()
    NavHost(controller, startDestination = Destination.HOME, modifier = modifier) {
        composable(Destination.HOME) {
            HomeRoute(
                HomeNavigation(
                    drawZone = { controller.open(Destination.ZONE) },
                    setUpAlerts = { controller.open(Destination.ALERTS) },
                    watch = { controller.open(Destination.MONITOR) },
                    incidents = { controller.open(Destination.INCIDENTS) },
                ),
            )
        }
        composable(Destination.ZONE) { ZoneRoute(onDone = { controller.goHome() }) }
        composable(Destination.ALERTS) { AlertsRoute(onDone = { controller.goHome() }) }
        composable(Destination.MONITOR) { MonitorRoute(onClose = { controller.goHome() }) }
        composable(Destination.INCIDENTS) { IncidentsRoute(onClose = { controller.goHome() }) }
    }
}

private fun NavHostController.open(destination: String) = navigate(destination) { launchSingleTop = true }

/** Safe against a double tap: popping to home never empties the stack. */
private fun NavHostController.goHome() {
    popBackStack(Destination.HOME, inclusive = false)
}
