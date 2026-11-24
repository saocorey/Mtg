package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.LedgerRepository
import com.example.data.attemptAutoSignIn
import com.example.data.authStateFlow
import com.example.data.signOutUser
import com.example.model.Deck
import com.example.model.Opponent
import com.example.ui.AuthScreen
import com.example.ui.DeckDetailSheet
import com.example.ui.DeckEditorSheet
import com.example.ui.DecksScreen
import com.example.ui.LedgerData
import com.example.ui.LedgerViewModel
import com.example.ui.LogMatchSheet
import com.example.ui.MatchesScreen
import com.example.ui.OpponentDetailSheet
import com.example.ui.OpponentEditorSheet
import com.example.ui.OpponentsScreen
import com.example.ui.RankingsScreen
import com.example.ui.UiState
import com.example.ui.VaporwaveBackdrop
import com.example.ui.theme.CommandLedgerTheme
import com.example.ui.theme.SynthSurface
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CommandLedgerTheme {
                AppNavigation()
            }
        }
    }
}

private enum class LedgerTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    RANKINGS("rankings", "Rankings", Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard),
    DECKS("decks", "Decks", Icons.Filled.Style, Icons.Outlined.Style),
    OPPONENTS("opponents", "Opponents", Icons.Filled.Groups, Icons.Outlined.Groups),
    MATCHES("matches", "Matches", Icons.Filled.History, Icons.Outlined.History)
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {},
            onUnauthenticated = {},
            scope = scope
        )
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedLedgerScreen(
                user = user,
                onSignOut = {
                    signOutUser(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedLedgerScreen(
    user: FirebaseUser,
    onSignOut: () -> Unit
) {
    val viewModel: LedgerViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                LedgerViewModel(LedgerRepository(db), user.uid)
            }
        }
    )

    val uiState by viewModel.ledgerState.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: LedgerTab.RANKINGS.route

    var showDeckEditor by rememberSaveable { mutableStateOf(false) }
    var editingDeckId by rememberSaveable { mutableStateOf<String?>(null) }

    var showOpponentEditor by rememberSaveable { mutableStateOf(false) }
    var editingOpponentId by rememberSaveable { mutableStateOf<String?>(null) }

    var showLogMatchSheet by rememberSaveable { mutableStateOf(false) }
    var detailDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailOpponentId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAccountMenu by remember { mutableStateOf(false) }

    val ledgerData = (uiState as? UiState.Success)?.data

    if (detailDeckId != null && ledgerData != null) {
        BackHandler { detailDeckId = null }
        val targetDeck = ledgerData.decks.firstOrNull { it.id == detailDeckId }
        if (targetDeck != null) {
            DeckDetailSheet(
                deck = targetDeck,
                data = ledgerData,
                onDismiss = { detailDeckId = null },
                onEdit = {
                    editingDeckId = targetDeck.id
                    detailDeckId = null
                    showDeckEditor = true
                },
                onToggleArchive = {
                    viewModel.setDeckArchived(targetDeck.id, !targetDeck.archived)
                    detailDeckId = null
                },
                onDelete = {
                    viewModel.deleteDeck(targetDeck.id) {
                        detailDeckId = null
                    }
                }
            )
        }
    }

    if (detailOpponentId != null && ledgerData != null) {
        BackHandler { detailOpponentId = null }
        val targetOpp = ledgerData.opponents.firstOrNull { it.id == detailOpponentId }
        if (targetOpp != null) {
            OpponentDetailSheet(
                opponent = targetOpp,
                data = ledgerData,
                onDismiss = { detailOpponentId = null },
                onEdit = {
                    editingOpponentId = targetOpp.id
                    detailOpponentId = null
                    showOpponentEditor = true
                },
                onDelete = {
                    viewModel.deleteOpponent(targetOpp.id) {
                        detailOpponentId = null
                    }
                }
            )
        }
    }

    if (showDeckEditor) {
        val editingDeck = editingDeckId?.let { id -> ledgerData?.decks?.firstOrNull { it.id == id } }
        DeckEditorSheet(
            existingDeck = editingDeck,
            isSaving = isSaving,
            onDismiss = {
                showDeckEditor = false
                editingDeckId = null
            },
            onSave = { name, commander, partner, colors, bracket, notes ->
                viewModel.saveDeck(
                    existingDeckId = editingDeck?.id,
                    name = name,
                    commander = commander,
                    partnerCommander = partner,
                    colorIdentity = colors,
                    bracket = bracket,
                    notes = notes
                ) { success ->
                    if (success) {
                        showDeckEditor = false
                        editingDeckId = null
                    }
                }
            }
        )
    }

    if (showOpponentEditor) {
        val editingOpp = editingOpponentId?.let { id -> ledgerData?.opponents?.firstOrNull { it.id == id } }
        OpponentEditorSheet(
            existingOpponent = editingOpp,
            isSaving = isSaving,
            onDismiss = {
                showOpponentEditor = false
                editingOpponentId = null
            },
            onSave = { name, favCmd, hex, notes ->
                viewModel.saveOpponent(
                    existingOpponentId = editingOpp?.id,
                    name = name,
                    favoriteCommander = favCmd,
                    avatarColorHex = hex,
                    notes = notes
                ) { createdId ->
                    if (createdId != null) {
                        showOpponentEditor = false
                        editingOpponentId = null
                    }
                }
            }
        )
    }

    if (showLogMatchSheet && ledgerData != null) {
        LogMatchSheet(
            decks = ledgerData.decks,
            opponents = ledgerData.opponents,
            isSaving = isSaving,
            onDismiss = { showLogMatchSheet = false },
            onQuickAddOpponent = { name, favCmd, colorHex, onCreated ->
                viewModel.saveOpponent(
                    existingOpponentId = null,
                    name = name,
                    favoriteCommander = favCmd,
                    avatarColorHex = colorHex,
                    notes = null,
                    onComplete = onCreated
                )
            },
            onOpenNewDeck = {
                showLogMatchSheet = false
                editingDeckId = null
                showDeckEditor = true
            },
            onSaveMatch = { deck, selectedOpps, oppCmds, userWon, winningOpps, isDraw, turnCount, notes, playedAtMillis ->
                viewModel.recordMatch(
                    deck = deck,
                    selectedOpponents = selectedOpps,
                    opponentCommanders = oppCmds,
                    userWon = userWon,
                    winningOpponents = winningOpps,
                    isDraw = isDraw,
                    turnCount = turnCount,
                    notes = notes,
                    playedAtMillis = playedAtMillis
                ) { success ->
                    if (success) {
                        showLogMatchSheet = false
                    }
                }
            }
        )
    }

    fun navigateTab(tab: LedgerTab) {
        navController.navigate(tab.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        VaporwaveBackdrop {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = "Command Ledger",
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = user.displayName ?: user.email ?: "Commander Player",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                    actions = {
                        Box {
                            IconButton(
                                onClick = { showAccountMenu = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("account_menu_button")
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = (user.displayName ?: user.email ?: "C")
                                                .firstOrNull()?.uppercase() ?: "C",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            DropdownMenu(
                                expanded = showAccountMenu,
                                onDismissRequest = { showAccountMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sign out") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = "Sign out"
                                        )
                                    },
                                    onClick = {
                                        showAccountMenu = false
                                        onSignOut()
                                    },
                                    modifier = Modifier.testTag("sign_out_menu_item")
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = SynthSurface.copy(alpha = 0.94f)
                    ) {
                        LedgerTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (ledgerData != null) {
                    ExtendedFloatingActionButton(
                        onClick = { showLogMatchSheet = true },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Log pod match"
                            )
                        },
                        text = { Text("Log Match") },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.testTag("log_match_fab")
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LedgerTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                    ) {
                        when (val state = uiState) {
                            UiState.Loading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                        Text(
                                            text = "Loading Commander Ledger...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            is UiState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Could not load ledger",
                                            style = MaterialTheme.typography.headlineSmall
                                        )
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(onClick = onSignOut) {
                                            Text("Switch Account")
                                        }
                                    }
                                }
                            }

                            is UiState.Success -> {
                                val data: LedgerData = state.data
                                NavHost(
                                    navController = navController,
                                    startDestination = LedgerTab.RANKINGS.route
                                ) {
                                    composable(LedgerTab.RANKINGS.route) {
                                        RankingsScreen(
                                            data = data,
                                            onDeckClick = { deck -> detailDeckId = deck.id },
                                            onOpponentClick = { opp -> detailOpponentId = opp.id },
                                            onOpenLogMatch = { showLogMatchSheet = true },
                                            onOpenAddDeck = {
                                                editingDeckId = null
                                                showDeckEditor = true
                                            }
                                        )
                                    }
                                    composable(LedgerTab.DECKS.route) {
                                        DecksScreen(
                                            data = data,
                                            onDeckClick = { deck -> detailDeckId = deck.id },
                                            onAddDeckClick = {
                                                editingDeckId = null
                                                showDeckEditor = true
                                            }
                                        )
                                    }
                                    composable(LedgerTab.OPPONENTS.route) {
                                        OpponentsScreen(
                                            data = data,
                                            onOpponentClick = { opp -> detailOpponentId = opp.id },
                                            onAddOpponentClick = {
                                                editingOpponentId = null
                                                showOpponentEditor = true
                                            }
                                        )
                                    }
                                    composable(LedgerTab.MATCHES.route) {
                                        MatchesScreen(
                                            data = data,
                                            isBusy = isSaving,
                                            onDeleteMatch = { matchId -> viewModel.deleteMatch(matchId) },
                                            onOpenLogMatch = { showLogMatchSheet = true },
                                            onImportBackup = { bundle -> viewModel.importBackup(bundle) },
                                            onShowMessage = { msg -> viewModel.notifyUser(msg) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
