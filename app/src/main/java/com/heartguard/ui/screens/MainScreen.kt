package com.heartguard.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.heartguard.R
import com.heartguard.ui.theme.ConfirmGreen
import com.heartguard.data.local.AppDao
import com.heartguard.data.remote.AiGateway
import com.heartguard.data.remote.VivoAiRepository
import com.heartguard.reminder.ReminderLaunchEvent
import com.heartguard.ui.theme.SurfaceWarm
import com.heartguard.ui.theme.TextPrimary
import com.heartguard.ui.theme.TextSecondary
import com.heartguard.utils.NativeOCRHelper
import com.heartguard.utils.SettingsManager
import com.heartguard.viewmodel.ChatViewModel
import com.heartguard.viewmodel.FakeCallViewModel
import com.heartguard.viewmodel.FakeVideoCallViewModel
import com.heartguard.viewmodel.MedicationViewModel

@Composable
fun MainScreen(
    appDao: AppDao,
    settingsManager: SettingsManager = SettingsManager,
    initialReminderLaunchEvent: ReminderLaunchEvent? = null,
    initialEmergencyLaunchEventId: Long? = null,
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = MainRoutes.isMainTabRoute(currentRoute)
    val aiGateway: AiGateway = remember(applicationContext) {
        VivoAiRepository(applicationContext)
    }
    val medicationViewModel: MedicationViewModel = viewModel(
        key = "main_medication_view_model",
        factory = remember(applicationContext, appDao, aiGateway) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MedicationViewModel(
                        appDao = appDao,
                        aiGateway = aiGateway,
                        nativeOCRHelper = NativeOCRHelper(applicationContext),
                        appContext = applicationContext,
                    ) as T
                }
            }
        },
    )
    val fakeCallViewModel: FakeCallViewModel = viewModel(
        key = "main_fake_call_view_model",
        factory = remember(applicationContext, appDao, aiGateway) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FakeCallViewModel(
                        appDao = appDao,
                        aiGateway = aiGateway,
                        appContext = applicationContext,
                    ) as T
                }
            }
        },
    )
    val fakeVideoCallViewModel: FakeVideoCallViewModel = viewModel(
        key = "main_fake_video_call_view_model",
        factory = remember(applicationContext, appDao, aiGateway) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FakeVideoCallViewModel(
                        appDao = appDao,
                        aiGateway = aiGateway,
                        appContext = applicationContext,
                    ) as T
                }
            }
        },
    )
    LaunchedEffect(medicationViewModel) {
        medicationViewModel.reminderEvent.collect { event ->
            navController.navigateToMedication(event)
        }
    }

    LaunchedEffect(initialReminderLaunchEvent?.eventId) {
        val event = initialReminderLaunchEvent ?: return@LaunchedEffect
        navController.navigateToMedication(event)
    }

    LaunchedEffect(initialEmergencyLaunchEventId) {
        initialEmergencyLaunchEventId ?: return@LaunchedEffect
        navController.navigateToEmergency()
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = SurfaceWarm,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = MainRoutes.isBottomNavItemSelected(
                            currentRoute = currentRoute,
                            itemRoute = item.route,
                        )
                        val label = stringResource(item.labelRes)

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = label,
                                )
                            },
                            label = {
                                Text(text = label)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ConfirmGreen,
                                selectedTextColor = TextPrimary,
                                indicatorColor = ConfirmGreen.copy(alpha = 0.16f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextPrimary,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainRoutes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable(MainRoutes.HOME) {
                val chatViewModelFactory = remember(applicationContext, appDao, aiGateway) {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ChatViewModel(
                                appDao = appDao,
                                appContext = applicationContext,
                                aiGateway = aiGateway,
                            ) as T
                        }
                    }
                }
                HomeScreen(
                    viewModel = viewModel(
                        factory = chatViewModelFactory,
                    ),
                    onSosClick = {
                        navController.navigate(MainRoutes.EMERGENCY)
                    },
                )
            }
            composable(MainRoutes.ANTI_FRAUD) {
                AntiFraudScreen(
                    appDao = appDao,
                    onScenarioClick = { scenario ->
                        navController.navigate(MainRoutes.antiFraudDetailRoute(scenario.id))
                    },
                )
            }
            composable(
                route = MainRoutes.ANTI_FRAUD_DETAIL,
                arguments = listOf(
                    navArgument(MainRoutes.ANTI_FRAUD_SCENARIO_ID_ARG) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val scenarioId = backStackEntry.arguments
                    ?.getString(MainRoutes.ANTI_FRAUD_SCENARIO_ID_ARG)
                val scenario = findFraudScenarioById(scenarioId)
                AntiFraudScenarioDetailScreen(
                    scenario = scenario,
                    onBack = {
                        navController.popBackStack()
                    },
                    onStartPractice = { selectedScenario ->
                        when (selectedScenario.practiceType) {
                            PracticeType.VOICE -> {
                                fakeCallViewModel.startNewDrill(selectedScenario.id)
                                navController.navigate(MainRoutes.fakeCallRoute(selectedScenario.id))
                            }
                            PracticeType.VIDEO -> {
                                fakeVideoCallViewModel.startNewDrill(
                                    scenarioId = selectedScenario.id,
                                    scamText = selectedScenario.toCompactPracticeScript(),
                                )
                                navController.navigate(MainRoutes.fakeVideoCallRoute(selectedScenario.id))
                            }
                        }
                    },
                )
            }
            composable(MainRoutes.MEDICATION_LIST) {
                MedicationListScreen(
                    medicationViewModel = medicationViewModel,
                )
            }
            composable(
                route = MainRoutes.MEDICATION_DETAIL,
                arguments = listOf(
                    navArgument(MainRoutes.MEDICATION_ID_ARG) {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument(MainRoutes.MEDICATION_NAME_ARG) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(MainRoutes.MEDICATION_TIME_ARG) {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument(MainRoutes.MEDICATION_EVENT_ID_ARG) {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                ),
            ) { backStackEntry ->
                val medicationId = backStackEntry.arguments
                    ?.getLong(MainRoutes.MEDICATION_ID_ARG)
                    ?: -1L
                val medicationName = backStackEntry.arguments
                    ?.getString(MainRoutes.MEDICATION_NAME_ARG)
                    .orEmpty()
                val medicationTime = backStackEntry.arguments
                    ?.getString(MainRoutes.MEDICATION_TIME_ARG)
                    .orEmpty()
                val eventId = backStackEntry.arguments
                    ?.getLong(MainRoutes.MEDICATION_EVENT_ID_ARG)
                    ?: 0L
                val reminderLaunchEvent = remember(medicationId, medicationName, medicationTime, eventId) {
                    ReminderLaunchEvent(
                        itemName = medicationName,
                        reminderId = medicationId,
                        matchedTime = medicationTime,
                        eventId = eventId,
                    )
                }
                MedicationListScreen(
                    medicationViewModel = medicationViewModel,
                    reminderLaunchEvent = reminderLaunchEvent,
                )
            }
            composable(MainRoutes.SETTINGS) {
                SettingsScreen()
            }
            composable(MainRoutes.SETTINGS_EMERGENCY_CONTACTS) {
                SettingsScreen(
                    initialRoute = SettingsRoutes.EMERGENCY_CONTACTS,
                )
            }
            composable(MainRoutes.EMERGENCY) {
                EmergencyScreen(
                    onCallContact = { phoneNumber ->
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:${phoneNumber.replace(" ", "")}")
                        }
                        context.startActivity(intent)
                    },
                    onAddEmergencyContact = {
                        navController.navigateToEmergencyContactsSettings()
                    },
                )
            }
            composable(MainRoutes.FAKE_CALL) {
                FakeCallScreen(
                    fakeCallViewModel = fakeCallViewModel,
                    onDrillFinished = { userInitiatedHangUp ->
                        navController.navigateToFraudResult(
                            drillType = MainRoutes.FRAUD_RESULT_TYPE_PHONE,
                            userInitiatedHangUp = userInitiatedHangUp,
                        )
                    },
                )
            }
            composable(
                route = MainRoutes.FAKE_CALL_SCENARIO,
                arguments = listOf(
                    navArgument(MainRoutes.ANTI_FRAUD_SCENARIO_ID_ARG) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val scenarioId = backStackEntry.arguments
                    ?.getString(MainRoutes.ANTI_FRAUD_SCENARIO_ID_ARG)
                FakeCallScreen(
                    fakeCallViewModel = fakeCallViewModel,
                    scenarioId = scenarioId,
                    onDrillFinished = { userInitiatedHangUp ->
                        navController.navigateToFraudResult(
                            drillType = MainRoutes.FRAUD_RESULT_TYPE_PHONE,
                            userInitiatedHangUp = userInitiatedHangUp,
                            sourceRoute = MainRoutes.FAKE_CALL_SCENARIO,
                        )
                    },
                )
            }
            composable(MainRoutes.FAKE_VIDEO_CALL) {
                FakeVideoCallScreen(
                    fakeVideoCallViewModel = fakeVideoCallViewModel,
                    onDrillFinished = { userInitiatedHangUp ->
                        navController.navigateToFraudResult(
                            drillType = MainRoutes.FRAUD_RESULT_TYPE_VIDEO,
                            userInitiatedHangUp = userInitiatedHangUp,
                        )
                    },
                )
            }
            composable(
                route = MainRoutes.FAKE_VIDEO_CALL_SCENARIO,
                arguments = listOf(
                    navArgument(MainRoutes.ANTI_FRAUD_SCENARIO_ID_ARG) {
                        type = NavType.StringType
                    },
                ),
            ) { backStackEntry ->
                val scenarioId = backStackEntry.arguments
                    ?.getString(MainRoutes.ANTI_FRAUD_SCENARIO_ID_ARG)
                FakeVideoCallScreen(
                    fakeVideoCallViewModel = fakeVideoCallViewModel,
                    scenarioId = scenarioId,
                    onDrillFinished = { userInitiatedHangUp ->
                        navController.navigateToFraudResult(
                            drillType = MainRoutes.FRAUD_RESULT_TYPE_VIDEO,
                            userInitiatedHangUp = userInitiatedHangUp,
                            sourceRoute = MainRoutes.FAKE_VIDEO_CALL_SCENARIO,
                        )
                    },
                )
            }
            composable(
                route = MainRoutes.FRAUD_RESULT,
                arguments = listOf(
                    navArgument(MainRoutes.FRAUD_RESULT_TYPE_ARG) {
                        type = NavType.StringType
                    },
                    navArgument(MainRoutes.FRAUD_RESULT_USER_HANG_UP_ARG) {
                        type = NavType.BoolType
                    },
                ),
            ) { backStackEntry ->
                val drillType = backStackEntry.arguments
                    ?.getString(MainRoutes.FRAUD_RESULT_TYPE_ARG)
                    .orEmpty()
                val userInitiatedHangUp = backStackEntry.arguments
                    ?.getBoolean(MainRoutes.FRAUD_RESULT_USER_HANG_UP_ARG)
                    ?: false
                AntiFraudResultScreen(
                    userInitiatedHangUp = userInitiatedHangUp,
                    onPracticeAgain = {
                        if (drillType == MainRoutes.FRAUD_RESULT_TYPE_VIDEO) {
                            fakeVideoCallViewModel.startNewDrill()
                            navController.startFakeDrill(MainRoutes.FAKE_VIDEO_CALL)
                        } else {
                            fakeCallViewModel.startNewDrill()
                            navController.startFakeDrill(MainRoutes.FAKE_CALL)
                        }
                    },
                    onReturnHome = {
                        navController.returnToAntiFraud()
                    },
                )
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
)

private val HomeIcon = Icons.Filled.Home
private val ShieldIcon = Icons.Filled.Shield
private val NotificationsIcon = Icons.Filled.Notifications
private val SettingsIcon = Icons.Filled.Settings

private val bottomNavItems = listOf(
    BottomNavItem(
        route = MainRoutes.HOME,
        labelRes = R.string.nav_home,
        icon = HomeIcon,
    ),
    BottomNavItem(
        route = MainRoutes.ANTI_FRAUD,
        labelRes = R.string.nav_anti_fraud,
        icon = ShieldIcon,
    ),
    BottomNavItem(
        route = MainRoutes.MEDICATION_LIST,
        labelRes = R.string.nav_reminder,
        icon = NotificationsIcon,
    ),
    BottomNavItem(
        route = MainRoutes.SETTINGS,
        labelRes = R.string.nav_settings,
        icon = SettingsIcon,
    ),
)

private fun NavHostController.navigateToMedication(event: ReminderLaunchEvent) {
    navigate(MainRoutes.medicationDetailRoute(event)) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateToEmergency() {
    navigate(MainRoutes.EMERGENCY) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateToEmergencyContactsSettings() {
    navigate(MainRoutes.SETTINGS_EMERGENCY_CONTACTS) {
        launchSingleTop = true
    }
}

private fun NavHostController.returnToAntiFraud() {
    val returnedToExistingAntiFraud = popBackStack(
        route = MainRoutes.ANTI_FRAUD,
        inclusive = false,
    )
    if (!returnedToExistingAntiFraud) {
        navigate(MainRoutes.ANTI_FRAUD) {
            launchSingleTop = true
        }
    }
}

private fun NavHostController.navigateToFraudResult(
    drillType: String,
    userInitiatedHangUp: Boolean,
    sourceRoute: String? = null,
) {
    val routeToPop = sourceRoute ?: if (drillType == MainRoutes.FRAUD_RESULT_TYPE_VIDEO) {
        MainRoutes.FAKE_VIDEO_CALL
    } else {
        MainRoutes.FAKE_CALL
    }
    navigate(MainRoutes.fraudResultRoute(drillType, userInitiatedHangUp)) {
        popUpTo(routeToPop) {
            inclusive = true
        }
        launchSingleTop = true
    }
}

private fun NavHostController.startFakeDrill(route: String) {
    navigate(route) {
        popUpTo(MainRoutes.ANTI_FRAUD) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

private object MainRoutes {
    const val HOME = "home"
    const val ANTI_FRAUD = "anti_fraud"
    const val MEDICATION_LIST = "medication_list"
    const val MEDICATION_ID_ARG = "medicationId"
    const val MEDICATION_NAME_ARG = "medicationName"
    const val MEDICATION_TIME_ARG = "medicationTime"
    const val MEDICATION_EVENT_ID_ARG = "eventId"
    const val MEDICATION_DETAIL =
        MEDICATION_LIST + "/{" + MEDICATION_ID_ARG + "}?" +
            MEDICATION_NAME_ARG + "={" + MEDICATION_NAME_ARG + "}&" +
            MEDICATION_TIME_ARG + "={" + MEDICATION_TIME_ARG + "}&" +
            MEDICATION_EVENT_ID_ARG + "={" + MEDICATION_EVENT_ID_ARG + "}"
    const val SETTINGS = "settings"
    const val SETTINGS_EMERGENCY_CONTACTS = "$SETTINGS/emergency_contacts"
    const val EMERGENCY = "emergency"
    const val ANTI_FRAUD_SCENARIO_ID_ARG = "scenarioId"
    const val ANTI_FRAUD_DETAIL = ANTI_FRAUD + "_detail/{" + ANTI_FRAUD_SCENARIO_ID_ARG + "}"
    const val FAKE_CALL = "fake_call"
    const val FAKE_VIDEO_CALL = "fake_video_call"
    const val FAKE_CALL_SCENARIO = FAKE_CALL + "/{" + ANTI_FRAUD_SCENARIO_ID_ARG + "}"
    const val FAKE_VIDEO_CALL_SCENARIO = FAKE_VIDEO_CALL + "/{" + ANTI_FRAUD_SCENARIO_ID_ARG + "}"
    const val FRAUD_RESULT_TYPE_PHONE = "phone"
    const val FRAUD_RESULT_TYPE_VIDEO = "video"
    const val FRAUD_RESULT_TYPE_ARG = "drillType"
    const val FRAUD_RESULT_USER_HANG_UP_ARG = "userInitiatedHangUp"
    const val FRAUD_RESULT =
        "fraud_result/{" + FRAUD_RESULT_TYPE_ARG + "}/{" + FRAUD_RESULT_USER_HANG_UP_ARG + "}"

    fun medicationDetailRoute(event: ReminderLaunchEvent): String {
        return "$MEDICATION_LIST/${event.reminderId}?" +
            "$MEDICATION_NAME_ARG=${Uri.encode(event.itemName)}&" +
            "$MEDICATION_TIME_ARG=${Uri.encode(event.matchedTime)}&" +
            "$MEDICATION_EVENT_ID_ARG=${event.eventId}"
    }

    fun fraudResultRoute(
        drillType: String,
        userInitiatedHangUp: Boolean,
    ): String {
        return "fraud_result/$drillType/$userInitiatedHangUp"
    }

    fun antiFraudDetailRoute(scenarioId: String): String {
        return ANTI_FRAUD + "_detail/${Uri.encode(scenarioId)}"
    }

    fun fakeCallRoute(scenarioId: String): String {
        return "$FAKE_CALL/${Uri.encode(scenarioId)}"
    }

    fun fakeVideoCallRoute(scenarioId: String): String {
        return "$FAKE_VIDEO_CALL/${Uri.encode(scenarioId)}"
    }

    fun isMedicationRoute(route: String?): Boolean {
        return route == MEDICATION_LIST || route == MEDICATION_DETAIL
    }

    fun isSettingsRoute(route: String?): Boolean {
        return route == SETTINGS || route == SETTINGS_EMERGENCY_CONTACTS
    }

    fun isBottomNavItemSelected(
        currentRoute: String?,
        itemRoute: String,
    ): Boolean {
        return currentRoute == itemRoute ||
            (itemRoute == SETTINGS && isSettingsRoute(currentRoute))
    }

    fun isMainTabRoute(route: String?): Boolean {
        return route == HOME ||
            route == ANTI_FRAUD ||
            route == MEDICATION_LIST ||
            isSettingsRoute(route)
    }
}
