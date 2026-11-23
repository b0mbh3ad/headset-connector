package com.headsetconnector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.headsetconnector.agent.AgentService
import com.headsetconnector.agent.DeviceInfo
import com.headsetconnector.ui.Accent2
import com.headsetconnector.ui.Bg
import com.headsetconnector.ui.Card
import com.headsetconnector.ui.ChatScreen
import com.headsetconnector.ui.ChatsScreen
import com.headsetconnector.ui.FriendsScreen
import com.headsetconnector.ui.HeadsetScreen
import com.headsetconnector.ui.HeadsetTheme
import com.headsetconnector.ui.ProfileScreen
import com.headsetconnector.ui.TextSecondary
import com.headsetconnector.ui.WelcomeScreen
import com.headsetconnector.util.Updater
import com.headsetconnector.vm.ConnectorViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // На шлеме включаем встроенный агент (без root): телефон сможет подключаться.
        if (DeviceInfo.isHeadset()) {
            AgentService.start(this)
        }
        setContent {
            App()
        }
    }
}

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Чаты", Icons.Rounded.ChatBubble),
    Tab("Друзья", Icons.Rounded.People),
    Tab("Профиль", Icons.Rounded.Person),
)

@Composable
private fun App(vm: ConnectorViewModel = viewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    val currentChat by vm.currentChat.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val update by vm.updateAvailable.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var installing by remember { mutableStateOf(false) }

    val isHeadset = remember { DeviceInfo.isHeadset() }

    LaunchedEffect(Unit) { vm.checkForUpdate() }

    LaunchedEffect(message) {
        val text = message
        if (!text.isNullOrBlank()) {
            snackbar.showSnackbar(text)
            vm.clearMessage()
        }
    }

    BackHandler(enabled = currentChat != null) { vm.closeChat() }

    HeadsetTheme(theme) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        when {
            session == null -> WelcomeScreen(vm)

            currentChat != null -> ChatScreen(vm)

            else -> Scaffold(
                containerColor = Bg,
                bottomBar = {
                    NavigationBar(
                        containerColor = Card,
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        tabs.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Icon(item.icon, item.title) },
                                label = { Text(item.title, fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent2,
                                    selectedTextColor = Accent2,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = Accent2.copy(alpha = 0.12f),
                                ),
                            )
                        }
                        if (!isHeadset) {
                            NavigationBarItem(
                                selected = tab == 3,
                                onClick = { tab = 3 },
                                icon = { Icon(Icons.Rounded.VideogameAsset, "Шлем") },
                                label = { Text("Шлем", fontSize = 10.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent2,
                                    selectedTextColor = Accent2,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                    indicatorColor = Accent2.copy(alpha = 0.12f),
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (tab) {
                        0 -> ChatsScreen(vm)
                        1 -> FriendsScreen(vm)
                        3 -> HeadsetScreen()
                        else -> ProfileScreen(vm)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (currentChat == null && session != null) 88.dp else 20.dp)
                .padding(horizontal = 12.dp),
        )

        update?.let { upd ->
            AlertDialog(
                onDismissRequest = { vm.dismissUpdate() },
                containerColor = Card,
                shape = RoundedCornerShape(22.dp),
                title = { Text("Доступна версия ${upd.versionName}") },
                text = { Text(if (installing) "Скачиваю обновление…" else "Установить новую версию приложения?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            installing = true
                            scope.launch {
                                try {
                                    val file = Updater.download(context, upd.apkUrl)
                                    Updater.install(context, file)
                                    vm.dismissUpdate()
                                } catch (e: Exception) {
                                    installing = false
                                    vm.notifyLocal("Не удалось скачать обновление")
                                }
                            }
                        },
                        enabled = !installing,
                    ) {
                        Text(
                            if (installing) "Скачиваю…" else "Обновить",
                            color = Accent2,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissUpdate() }, enabled = !installing) {
                        Text("Позже", color = TextSecondary)
                    }
                },
            )
        }
    }
    }
}
