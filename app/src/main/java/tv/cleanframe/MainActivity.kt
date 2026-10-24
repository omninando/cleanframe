package tv.cleanframe

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: HomeViewModel = viewModel()
            CleanFrameApp(model)
        }
    }
}

@Composable
fun CleanFrameApp(model: HomeViewModel = viewModel()) {
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val apps by model.apps.collectAsStateWithLifecycle()
    val screen by model.screen.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val backChoice by model.backChoice.collectAsStateWithLifecycle()
    val editor by model.editor.collectAsStateWithLifecycle()
    val weather by model.weather.collectAsStateWithLifecycle()
    val look = prefs.appearance()
    val showingPhoto = look.background == BackgroundMode.Photo
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                model.reloadApps()
                model.onHomeResumed()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(20 * 60_000L)
            model.refreshWeather()
        }
    }

    LaunchedEffect(showingPhoto, screen) {
        if (screen != Screen.Home || !showingPhoto) return@LaunchedEffect
        while (true) {
            delay(5 * 60_000L)
            model.advanceWallpaper()
        }
    }

    androidx.activity.compose.BackHandler { model.onBack() }

    val byPackage = remember(apps) { apps.associateBy { it.packageName } }
    val homeApps = prefs.homePackages.mapNotNull { byPackage[it] }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (screen) {
            Screen.Home -> HomeScreen(
                apps = homeApps,
                look = look,
                message = message,
                backChoice = backChoice,
                weather = weather,
                onLaunch = model::launch,
                onArt = model::toggleArtMode,
                onAllApps = { model.open(Screen.AllApps) },
                onSettings = { model.open(Screen.Settings) },
                onStay = model::dismissBackChoice,
                onGoogleTv = model::openGoogleTv,
            )
            Screen.AllApps -> AllAppsScreen(
                apps = apps,
                home = prefs.homePackages.toSet(),
                onLaunch = model::launch,
                onEdit = { model.open(Screen.Editor) },
            )
            Screen.Editor -> EditorScreen(
                apps = apps,
                session = editor ?: EditorSession(),
                onStartMove = model::startMove,
                onMove = model::move,
                onPlace = model::place,
                onRemove = model::removeFromHome,
                onAdd = model::addToHome,
                onUndo = model::undo,
            )
            Screen.Settings -> SettingsScreen(
                prefs = prefs,
                onBackground = model::setBackground,
                onLayout = model::setLayout,
                onClock = model::setClock,
                onEditHome = { model.open(Screen.Editor) },
                onGoogleTv = model::openGoogleTv,
                onAndroidSettings = model::openSettings,
                onUseAsHome = model::requestHomeRole,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    apps: List<TvApp>,
    look: Appearance,
    message: String?,
    backChoice: Boolean,
    weather: Weather?,
    onLaunch: (String) -> Unit,
    onArt: () -> Unit,
    onAllApps: () -> Unit,
    onSettings: () -> Unit,
    onStay: () -> Unit,
    onGoogleTv: () -> Unit,
) {
    val ink = inkFor(look.background)
    val listFocus = remember { FocusRequester() }
    val dockFocus = remember { FocusRequester() }
    val railRequesters = remember { List(3) { FocusRequester() } }
    var railIndex by remember { mutableIntStateOf(0) }
    val safeRail = railIndex.coerceIn(0, 2)

    var artSeen by remember { mutableStateOf(false) }
    LaunchedEffect(look.layout, apps.map { it.packageName }) {
        if (apps.isEmpty()) return@LaunchedEffect
        delay(40)
        val target = if (look.layout == HomeLayout.List) listFocus else dockFocus
        runCatching { target.requestFocus() }
    }
    LaunchedEffect(look.artMode) {
        if (!artSeen) {
            artSeen = true
            return@LaunchedEffect
        }
        delay(40)
        runCatching { railRequesters[safeRail].requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        HomeBackground(look)
        val appTarget = when {
            look.layout == HomeLayout.List && apps.isNotEmpty() -> listFocus
            look.layout == HomeLayout.Dock && apps.isNotEmpty() -> dockFocus
            else -> FocusRequester.Default
        }
        val artLabel = if (look.background == BackgroundMode.Dark) "Dark" else "Art Mode"
        val controls = listOf(artLabel to onArt, "All apps" to onAllApps, "Settings" to onSettings)
        if (look.layout == HomeLayout.List) {
            ClockCluster(
                weather = weather,
                look = look,
                color = ink.focus,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 56.dp),
            )
            AppCarousel(
                apps = apps,
                typeface = look.typeface,
                ink = ink,
                rail = railRequesters[safeRail],
                focusRequester = listFocus,
                onLaunch = onLaunch,
                modifier = Modifier.padding(start = 56.dp, top = 36.dp).width(600.dp),
            )
            Column(
                Modifier.align(Alignment.BottomEnd).padding(end = 56.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.End,
            ) {
                controls.forEachIndexed { index, (label, action) ->
                    ControlEntry(
                        label = label,
                        typeface = look.typeface,
                        ink = ink,
                        modifier = Modifier
                            .focusRequester(railRequesters[index])
                            .focusProperties { left = appTarget }
                            .onFocusChanged { if (it.isFocused) railIndex = index }
                            .quietClick(action),
                    )
                }
            }
        } else {
            Row(
                Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                ClockCluster(weather, look, ink.focus)
                Spacer(Modifier.width(12.dp))
                controls.forEachIndexed { index, (label, action) ->
                    ControlEntry(
                        label = label,
                        typeface = look.typeface,
                        ink = ink,
                        modifier = Modifier
                            .focusRequester(railRequesters[index])
                            .focusProperties { down = appTarget }
                            .onFocusChanged { if (it.isFocused) railIndex = index }
                            .quietClick(action),
                    )
                }
            }
            DockCarousel(
                apps = apps,
                typeface = look.typeface,
                ink = ink,
                rail = railRequesters[safeRail],
                focusRequester = dockFocus,
                onLaunch = onLaunch,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 36.dp),
            )
        }

        if (message != null) {
            Text(
                message,
                color = ink.hint,
                style = controlStyle(look.typeface),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            )
        }
        if (backChoice) {
            BackChoice(ink, look.typeface, onStay, onGoogleTv)
        }
    }
}

@Composable
private fun BackChoice(
    ink: Ink,
    typeface: TypefaceChoice,
    onStay: () -> Unit,
    onGoogleTv: () -> Unit,
) {
    val stay = remember { FocusRequester() }
    val leave = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { stay.requestFocus() } }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(start = 72.dp, bottom = 36.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Choice("Stay", ink, typeface, Modifier.focusRequester(stay).focusProperties { right = leave }.quietClick(onStay))
        Choice("Google TV", ink, typeface, Modifier.focusRequester(leave).focusProperties { left = stay }.quietClick(onGoogleTv))
    }
}

@Composable
private fun Choice(label: String, ink: Ink, typeface: TypefaceChoice, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val color = focusColor(focused, ink.focus, ink.idle, durationMillis = 200)
    Text(
        label,
        color = color,
        style = homeNameStyle(typeface),
        modifier = Modifier.onFocusChanged { focused = it.isFocused }.then(modifier),
    )
}

@Composable
private fun HomeBackground(look: Appearance) {
    AnimatedContent(
        targetState = look.background to look.wallpaperId,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            fadeIn(tween(640, easing = MotionEase)) togetherWith fadeOut(tween(640, easing = MotionEase))
        },
        label = "background",
    ) { (mode, wallpaperId) ->
        when (mode) {
            BackgroundMode.Dark -> Box(Modifier.fillMaxSize().background(Color.Black))
            BackgroundMode.Light -> Box(Modifier.fillMaxSize().background(Color(0xFFF4F1EC)))
            BackgroundMode.Photo -> {
                Image(
                    painter = painterResource(wallpaperById(wallpaperId).image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.28f),
                            0.42f to Color.Black.copy(alpha = 0.05f),
                            1f to Color.Transparent,
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun ClockCluster(
    weather: Weather?,
    look: Appearance,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val place = if (look.background == BackgroundMode.Photo) wallpaperById(look.wallpaperId).place else null
    Column(modifier, horizontalAlignment = Alignment.End) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (weather != null) WeatherReadout(weather, look.typeface, color)
            ClockFace(look.clock, look.typeface, color)
        }
        AnimatedContent(
            targetState = place,
            modifier = Modifier
                .height(0.dp)
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .padding(top = 4.dp),
            transitionSpec = {
                ContentTransform(
                    fadeIn(tween(640, easing = MotionEase)),
                    fadeOut(tween(640, easing = MotionEase)),
                    sizeTransform = null,
                )
            },
            label = "place",
        ) { label ->
            Text(
                text = label.orEmpty(),
                color = color.copy(alpha = if (label == null) 0f else 0.78f),
                style = placeStyle(look.typeface),
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WeatherReadout(weather: Weather, typeface: TypefaceChoice, color: Color) {
    val ink = color.copy(alpha = 0.92f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Symbol(weatherSymbol(weather.code), ink, 24.sp)
        Text("${weather.celsius}°", color = ink, style = clockStyle(typeface))
    }
}

@Composable
private fun ClockFace(format: ClockFormat, typeface: TypefaceChoice, color: Color) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            val wait = ((60 - now.second) * 1000L - now.nano / 1_000_000).coerceAtLeast(250)
            delay(wait)
        }
    }
    val minute = now.minute.toString().padStart(2, '0')
    val text = if (format == ClockFormat.Hour24) {
        "${now.hour}:$minute"
    } else {
        val hour = now.hour % 12
        "${if (hour == 0) 12 else hour}:$minute"
    }
    Text(text, color = color.copy(alpha = 0.92f), style = clockStyle(typeface))
}

private val HomeRowHeight = 64.dp
private val HomeListHeight = 472.dp

@Composable
private fun AppCarousel(
    apps: List<TvApp>,
    typeface: TypefaceChoice,
    ink: Ink,
    rail: FocusRequester,
    focusRequester: FocusRequester,
    onLaunch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) return
    val faded = apps.size >= VisibleHome
    val windowRows = if (faded) HomeListHeight.value / HomeRowHeight.value else apps.size.toFloat()
    val center = (windowRows - 1f) / 2f
    val reach = kotlin.math.ceil(windowRows / 2f).toInt() + 1
    val firstDelta = -reach
    val lastDelta = reach
    var index by remember(apps.map { it.packageName }) { mutableIntStateOf(0) }
    var queued by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val shift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun step(direction: Int) {
        if (apps.size <= 1) return
        if (shift.isRunning) {
            queued = direction
            return
        }
        scope.launch {
            var cursor = index
            var next = direction
            while (next != 0) {
                shift.animateTo(next.toFloat(), tween(durationMillis = 240, easing = MotionEase))
                cursor = wrap(cursor + next, apps.size)
                index = cursor
                shift.snapTo(0f)
                next = queued
                queued = 0
            }
        }
    }

    Box(
        modifier
            .height(if (faded) HomeListHeight else HomeRowHeight * apps.size)
            .focusRequester(focusRequester)
            .focusProperties { right = rail }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        step(1)
                        true
                    }
                    Key.DirectionUp -> {
                        step(-1)
                        true
                    }
                    else -> false
                }
            }
            .quietClick { onLaunch(apps[index].packageName) }
            .then(if (faded) Modifier.listFade() else Modifier)
            .clipToBounds(),
    ) {
        val travel = shift.value
        for (delta in firstDelta..lastDelta) {
            val distance = delta - travel
            Box(
                modifier = Modifier
                    .offset(y = HomeRowHeight * (center + distance))
                    .height(HomeRowHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    apps[wrap(index + delta, apps.size)].label,
                    color = rowColor(focused, distance, ink),
                    style = homeNameStyle(typeface),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun wrap(value: Int, size: Int): Int = ((value % size) + size) % size

private const val VisibleDock = 5

@Composable
private fun DockCarousel(
    apps: List<TvApp>,
    typeface: TypefaceChoice,
    ink: Ink,
    rail: FocusRequester,
    focusRequester: FocusRequester,
    onLaunch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (apps.isEmpty()) return
    val more = apps.size > VisibleDock
    val slots = if (more) VisibleDock else apps.size
    val center = if (more) (VisibleDock - 1) / 2f else ((apps.size - 1) / 2).toFloat()
    val firstDelta = if (more) -VisibleDock / 2 - 1 else -((apps.size - 1) / 2) - 1
    val lastDelta = if (more) VisibleDock / 2 + 1 else apps.size - ((apps.size - 1) / 2)
    var index by remember(apps.map { it.packageName }) { mutableIntStateOf(0) }
    var queued by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val shift = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun step(direction: Int) {
        if (apps.size <= 1) return
        if (shift.isRunning) {
            queued = direction
            return
        }
        scope.launch {
            var cursor = index
            var next = direction
            while (next != 0) {
                shift.animateTo(next.toFloat(), tween(durationMillis = 240, easing = MotionEase))
                cursor = wrap(cursor + next, apps.size)
                index = cursor
                shift.snapTo(0f)
                next = queued
                queued = 0
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(HomeRowHeight)
            .focusRequester(focusRequester)
            .focusProperties { up = rail }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionRight -> {
                        step(1)
                        true
                    }
                    Key.DirectionLeft -> {
                        step(-1)
                        true
                    }
                    else -> false
                }
            }
            .quietClick { onLaunch(apps[index].packageName) }
            .then(if (more) Modifier.fadeEdges(horizontal = true, edge = 0.5f / slots) else Modifier)
            .clipToBounds(),
    ) {
        val travel = shift.value
        val slot = maxWidth / slots
        for (delta in firstDelta..lastDelta) {
            val distance = delta - travel
            Box(
                modifier = Modifier
                    .offset(x = slot * (center + distance))
                    .width(slot)
                    .height(HomeRowHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    apps[wrap(index + delta, apps.size)].label,
                    color = rowColor(focused, distance, ink),
                    style = homeNameStyle(typeface),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun Modifier.listFade(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edge = listOf(
            0f to 0f,
            0.0217f to 0.0156f,
            0.0434f to 0.0625f,
            0.0651f to 0.1406f,
            0.0869f to 0.25f,
            0.1086f to 0.3906f,
            0.1303f to 0.5625f,
            0.1520f to 0.7656f,
            0.1737f to 1f,
        )
        val stops = (edge + edge.asReversed().map { (1f - it.first) to it.second }).toTypedArray()
        drawRect(
            brush = Brush.verticalGradient(*stops.map { it.first to Color.Black.copy(alpha = it.second) }.toTypedArray()),
            blendMode = BlendMode.DstIn,
        )
    }

private fun Modifier.fadeEdges(horizontal: Boolean, edge: Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val brush = if (horizontal) {
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    edge to Color.Black,
                    1f - edge to Color.Black,
                    1f to Color.Transparent,
                )
            } else {
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    edge to Color.Black,
                    1f - edge to Color.Black,
                    1f to Color.Transparent,
                )
            }
            drawRect(brush = brush, blendMode = BlendMode.DstIn)
        }

@Composable
private fun ControlEntry(
    label: String,
    typeface: TypefaceChoice,
    ink: Ink,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val emphasis by animateFloatAsState(
        targetValue = if (focused) 1f else IdleEmphasis,
        animationSpec = tween(durationMillis = 160, easing = MotionEase),
        label = "control",
    )
    Text(
        label,
        color = ink.focus.copy(alpha = ink.focus.alpha * emphasis),
        style = controlStyle(typeface),
        modifier = Modifier.onFocusChanged { focused = it.isFocused }.then(modifier),
    )
}

@Composable
private fun Modifier.quietClick(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

private const val IdleEmphasis = 0.42f

private fun rowColor(focused: Boolean, distance: Float, ink: Ink): Color {
    val falloff = abs(distance).coerceIn(0f, 1f)
    val emphasis = if (focused) 1f - falloff * (1f - IdleEmphasis) else IdleEmphasis
    return ink.focus.copy(alpha = ink.focus.alpha * emphasis)
}

private data class Ink(val focus: Color, val idle: Color, val hint: Color)

private fun inkFor(background: BackgroundMode): Ink = when (background) {
    BackgroundMode.Light -> Ink(Color(0xFF171717), Color(0xFF171717).copy(alpha = 0.46f), Color(0xFF171717).copy(alpha = 0.4f))
    BackgroundMode.Dark -> Ink(Color.White, Color(0xFF5C5C5C), Color(0xFF6A6A6A))
    BackgroundMode.Photo -> Ink(Color.White, Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.66f))
}
