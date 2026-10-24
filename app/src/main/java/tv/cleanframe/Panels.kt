package tv.cleanframe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val PanelInk = Color.White
private val PanelIdle = Color(0xFF5C5C5C)
private val PanelHint = Color(0xFF6A6A6A)

@Composable
fun AllAppsScreen(
    apps: List<TvApp>,
    home: Set<String>,
    onLaunch: (String) -> Unit,
    onEdit: () -> Unit,
) {
    val edit = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(40)
        runCatching { edit.requestFocus() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(start = 72.dp, top = 48.dp, end = 72.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("All apps", color = PanelHint, style = controlStyle(TypefaceChoice.Roboto))
        PanelLine(
            "Edit home",
            PanelInk,
            PanelIdle,
            Modifier
                .focusRequester(edit)
                .focusProperties { if (apps.isNotEmpty()) down = first }
                .quietClick(onEdit),
        )
        apps.forEachIndexed { index, app ->
            val suffix = if (app.packageName in home) "    On home" else ""
            PanelLine(
                app.label + suffix,
                PanelInk,
                PanelIdle,
                Modifier
                    .then(if (index == 0) Modifier.focusRequester(first).focusProperties { up = edit } else Modifier)
                    .quietClick { onLaunch(app.packageName) },
            )
        }
    }
}

@Composable
fun EditorScreen(
    apps: List<TvApp>,
    session: EditorSession,
    onStartMove: (String) -> Unit,
    onMove: (Int) -> Unit,
    onPlace: () -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
    onUndo: () -> Unit,
) {
    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    val home = session.home.mapNotNull { byPackage[it] }
    val homeIds = session.home.toSet()
    val recent = session.removed.mapNotNull { byPackage[it] }.filter { it.packageName !in homeIds }
    val others = recent + apps.filter { it.packageName !in homeIds && it.packageName !in recent.map { app -> app.packageName } }
    val moving = session.moving
    val undo = remember { FocusRequester() }
    val focusFor = remember { mutableMapOf<String, FocusRequester>() }
    var opened by remember { mutableStateOf(false) }
    fun focusOf(id: String) = focusFor.getOrPut(id) { FocusRequester() }

    LaunchedEffect(moving, home.joinToString { it.packageName }) {
        delay(40)
        val id = moving ?: if (!opened) home.firstOrNull()?.packageName else null
        opened = true
        if (id != null) runCatching { focusOf(id).requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(start = 56.dp, top = 36.dp, end = 56.dp, bottom = 24.dp),
    ) {
        Text(
            if (moving == null) "OK picks up an app. Right removes it." else "Up and down move it. OK places it.",
            color = PanelHint,
            style = controlStyle(TypefaceChoice.Roboto),
        )
        Row(Modifier.fillMaxWidth().weight(1f).padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text("On Home", color = PanelHint, style = controlStyle(TypefaceChoice.Roboto))
                home.forEach { app ->
                    val name = focusOf(app.packageName)
                    val remove = focusOf(app.packageName + "#remove")
                    val pickedUp = moving == app.packageName
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PanelLine(
                            if (pickedUp) "${app.label}  ·" else app.label,
                            PanelInk,
                            PanelIdle,
                            Modifier
                                .weight(1f)
                                .focusRequester(name)
                                .focusProperties { if (!pickedUp && home.size > 1) right = remove }
                                .onPreviewKeyEvent { event ->
                                    if (!pickedUp || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionUp -> {
                                            onMove(-1)
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            onMove(1)
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            onPlace()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .quietClick { if (pickedUp) onPlace() else onStartMove(app.packageName) },
                        )
                        if (home.size > 1 && !pickedUp) {
                            PanelLine(
                                "Remove",
                                PanelInk,
                                PanelIdle,
                                Modifier.focusRequester(remove).focusProperties { left = name }.quietClick { onRemove(app.packageName) },
                                compact = true,
                            )
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                Text("Not on Home", color = PanelHint, style = controlStyle(TypefaceChoice.Roboto))
                others.forEach { app ->
                    PanelLine(
                        app.label,
                        PanelInk,
                        PanelIdle,
                        Modifier.quietClick { onAdd(app.packageName) },
                    )
                }
            }
        }
        PanelLine(
            if (session.undo.isEmpty()) "Undo" else "Undo last change",
            PanelInk,
            if (session.undo.isEmpty()) Color(0xFF3E3E3E) else PanelIdle,
            Modifier.focusRequester(undo).quietClick { if (session.undo.isNotEmpty()) onUndo() },
        )
    }
}

@Composable
fun SettingsScreen(
    prefs: HomePreferences,
    onBackground: (BackgroundMode) -> Unit,
    onLayout: (HomeLayout) -> Unit,
    onClock: (ClockFormat) -> Unit,
    onEditHome: () -> Unit,
    onGoogleTv: () -> Unit,
    onAndroidSettings: () -> Unit,
    onUseAsHome: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(40)
        runCatching { first.requestFocus() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(start = 72.dp, top = 42.dp, end = 80.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Header("Background")
        Choice("Photo", prefs.background == BackgroundMode.Photo, Modifier.focusRequester(first)) { onBackground(BackgroundMode.Photo) }
        Choice("Dark", prefs.background == BackgroundMode.Dark) { onBackground(BackgroundMode.Dark) }
        Header("Layout")
        Choice("List", prefs.layout == HomeLayout.List) { onLayout(HomeLayout.List) }
        Choice("Dock", prefs.layout == HomeLayout.Dock) { onLayout(HomeLayout.Dock) }
        Header("Clock")
        Choice("12-hour clock", prefs.clock == ClockFormat.Hour12) { onClock(ClockFormat.Hour12) }
        Choice("24-hour clock", prefs.clock == ClockFormat.Hour24) { onClock(ClockFormat.Hour24) }
        Header("Home")
        Action("Arrange apps") { onEditHome() }
        Action("Use CleanFrame as Home") { onUseAsHome() }
        Action("Google TV") { onGoogleTv() }
        Action("Android settings") { onAndroidSettings() }
    }
}

@Composable
private fun Header(title: String) {
    Text(
        title,
        color = PanelHint,
        style = controlStyle(TypefaceChoice.Roboto),
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
    )
}

@Composable
private fun Choice(label: String, applied: Boolean, modifier: Modifier = Modifier, onApply: () -> Unit) {
    PanelLine(
        if (applied) "$label    ·" else label,
        PanelInk,
        PanelIdle,
        modifier.quietClick(onApply),
    )
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    PanelLine(label, PanelInk, PanelIdle, Modifier.quietClick(onClick))
}

@Composable
private fun PanelLine(
    label: String,
    focus: Color,
    idle: Color,
    modifier: Modifier,
    compact: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val color = focusColor(focused, focus, idle, durationMillis = 200)
    Text(
        label,
        color = color,
        style = homeNameStyle(TypefaceChoice.Roboto).copy(fontSize = if (compact) 18.sp else 26.sp, lineHeight = if (compact) 22.sp else 32.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.onFocusChanged { focused = it.isFocused }.then(modifier),
    )
}

@Composable
private fun Modifier.quietClick(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
