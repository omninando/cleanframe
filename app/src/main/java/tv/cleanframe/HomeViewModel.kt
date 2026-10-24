package tv.cleanframe

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.homeStore by preferencesDataStore(name = "cleanframe")

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val store = app.homeStore
    private val catalog = AppCatalog(app)
    private val weatherClient = WeatherClient()
    private val wallpapers = WallpaperDeck()
    private var weatherAt = 0L
    private var weatherInFlight = false

    val preferences: StateFlow<HomePreferences> = store.data
        .map { it.decode() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomePreferences())

    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    val apps: StateFlow<List<TvApp>> = _apps

    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather

    private val _backChoice = MutableStateFlow(false)
    val backChoice: StateFlow<Boolean> = _backChoice

    private val _editor = MutableStateFlow<EditorSession?>(null)
    val editor: StateFlow<EditorSession?> = _editor

    init {
        refreshWeather()
        viewModelScope.launch {
            val loadedApps = catalog.load()
            _apps.value = loadedApps
            val current = store.data.first().decode()
            if (!current.seeded) {
                val installed = loadedApps.map { it.packageName }.toSet()
                val seeded = PreferredHome.filter { it in installed }.ifEmpty {
                    loadedApps.take(6).map { it.packageName }
                }
                store.edit {
                    it[HomeKey] = seeded.joinToString("|")
                    it[SeededKey] = true
                }
            }
        }
    }

    fun onHomeResumed() {
        refreshWeather()
    }

    fun refreshWeather() {
        val now = System.currentTimeMillis()
        if (weatherInFlight) return
        if (_weather.value != null && now - weatherAt < 20 * 60_000L) return
        weatherInFlight = true
        viewModelScope.launch {
            val next = withContext(Dispatchers.IO) { runCatching { weatherClient.current() }.getOrNull() }
            if (next != null) {
                weatherAt = System.currentTimeMillis()
                _weather.value = next
            }
            weatherInFlight = false
        }
    }

    fun reloadApps() {
        viewModelScope.launch {
            _apps.value = catalog.load()
        }
    }

    fun open(screen: Screen) {
        _backChoice.value = false
        if (screen == Screen.Editor) {
            val prefs = preferences.value
            _editor.value = EditorSession(home = prefs.homePackages, removed = prefs.recentlyRemoved)
        }
        _screen.value = screen
    }

    fun onBack() {
        val session = _editor.value
        if (_screen.value == Screen.Editor && session?.moving != null) {
            _editor.value = session.copy(moving = null)
            return
        }
        if (_backChoice.value) {
            _backChoice.value = false
            return
        }
        if (_screen.value != Screen.Home) {
            _screen.value = Screen.Home
            return
        }
        _backChoice.value = true
    }

    fun dismissBackChoice() {
        _backChoice.value = false
    }

    private var artToggleAt = 0L

    fun toggleArtMode() {
        val now = System.currentTimeMillis()
        if (now - artToggleAt < 700) return
        artToggleAt = now
        // Write an absolute value. DataStore may run the edit block more than
        // once, and inverting inside it would turn art mode straight back on.
        val turningOn = !preferences.value.artMode
        val next = if (turningOn) wallpapers.next(preferences.value.wallpaperId) else null
        edit { prefs ->
            prefs[ArtKey] = turningOn
            if (turningOn) {
                prefs[BackgroundKey] = BackgroundMode.Photo.name
                if (next != null) prefs[WallpaperKey] = next
            } else {
                prefs[BackgroundKey] = BackgroundMode.Dark.name
            }
        }
    }

    fun setBackground(mode: BackgroundMode) {
        if (mode == BackgroundMode.Light) return
        val next = if (mode == BackgroundMode.Photo) wallpapers.next(preferences.value.wallpaperId) else null
        edit {
            it[BackgroundKey] = mode.name
            if (mode == BackgroundMode.Dark) it[ArtKey] = false
            if (next != null) it[WallpaperKey] = next
        }
    }

    fun advanceWallpaper() {
        val next = wallpapers.next(preferences.value.wallpaperId)
        edit { it[WallpaperKey] = next }
    }

    fun setLayout(layout: HomeLayout) = edit { it[LayoutKey] = layout.name }
    fun setClock(format: ClockFormat) = edit { it[ClockKey] = format.name }

    fun launch(packageName: String): Boolean {
        val ok = launchPackage(getApplication(), packageName)
        if (!ok) note("Couldn't open that app")
        return ok
    }

    fun openGoogleTv() {
        if (!openGoogleTv(getApplication())) note("Google TV isn't available")
    }

    fun openSettings() = openSettings(getApplication())

    fun requestHomeRole() = requestHomeRole(getApplication())

    fun startMove(packageName: String) {
        val session = _editor.value ?: return
        if (packageName !in session.home || session.home.size < 2) return
        _editor.value = session.copy(moving = packageName, moveOrigin = session.home)
    }

    fun move(delta: Int) {
        val session = _editor.value ?: return
        val moving = session.moving ?: return
        val index = session.home.indexOf(moving)
        val target = index + delta
        if (index < 0 || target !in session.home.indices) return
        val next = session.home.toMutableList()
        next.removeAt(index)
        next.add(target, moving)
        _editor.value = session.copy(home = next)
    }

    fun place() {
        val session = _editor.value ?: return
        if (session.moving == null) return
        val origin = session.moveOrigin ?: session.home
        if (origin == session.home) {
            _editor.value = session.copy(moving = null, moveOrigin = null)
            return
        }
        persistEditor(
            before = session,
            next = session.copy(
                moving = null,
                moveOrigin = null,
                undo = session.undo + (origin to session.removed),
            ),
            rememberCurrent = false,
        )
    }

    fun cancelMove() {
        val session = _editor.value ?: return
        _editor.value = session.copy(home = session.moveOrigin ?: session.home, moving = null, moveOrigin = null)
    }

    fun removeFromHome(packageName: String) {
        val session = _editor.value ?: return
        if (session.home.size <= 1 || packageName !in session.home) {
            note("Keep at least one app on Home")
            return
        }
        val index = session.home.indexOf(packageName)
        val home = session.home.toMutableList().also { it.removeAt(index) }
        val removed = listOf(packageName) + session.removed.filterNot { it == packageName }
        persistEditor(session, EditorSession(home, removed, undo = session.undo + (session.home to session.removed)))
    }

    fun addToHome(packageName: String) {
        val session = _editor.value ?: return
        if (packageName in session.home) return
        val home = session.home + packageName
        val removed = session.removed.filterNot { it == packageName }
        persistEditor(session, EditorSession(home, removed, undo = session.undo + (session.home to session.removed)))
    }

    fun undo() {
        val session = _editor.value ?: return
        val previous = session.undo.lastOrNull() ?: return
        val restored = EditorSession(
            home = previous.first,
            removed = previous.second,
            undo = session.undo.dropLast(1),
        )
        persistEditor(session, restored, rememberCurrent = false)
    }

    private fun persistEditor(before: EditorSession, next: EditorSession, rememberCurrent: Boolean = true) {
        val withUndo = if (rememberCurrent && next.undo === before.undo) {
            next.copy(undo = before.undo + (before.home to before.removed))
        } else {
            next
        }
        viewModelScope.launch {
            val saved = runCatching {
                store.edit {
                    it[HomeKey] = withUndo.home.joinToString("|")
                    it[RemovedKey] = withUndo.removed.joinToString("|")
                }
            }.isSuccess
            if (saved) {
                _editor.value = withUndo
            } else {
                _editor.value = before
                note("Couldn't save that change")
            }
        }
    }

    private fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        viewModelScope.launch {
            val saved = runCatching { store.edit(block) }.isSuccess
            if (!saved) note("Couldn't save that change")
        }
    }

    private fun note(text: String) {
        _message.value = text
        viewModelScope.launch {
            delay(3500)
            if (_message.value == text) _message.value = null
        }
    }

    companion object {
        val BackgroundKey = stringPreferencesKey("background")
        val LayoutKey = stringPreferencesKey("layout")
        val AppsKey = stringPreferencesKey("apps")
        val ControlsKey = stringPreferencesKey("controls")
        val ClockKey = stringPreferencesKey("clock")
        val FontKey = stringPreferencesKey("font")
        val WallpaperKey = stringPreferencesKey("wallpaper")
        val RotationKey = stringPreferencesKey("rotation")
        val HomeKey = stringPreferencesKey("home")
        val SeededKey = booleanPreferencesKey("seeded")
        val RemovedKey = stringPreferencesKey("removed")
        val ArtKey = booleanPreferencesKey("art")
    }
}

private fun Preferences.decode(): HomePreferences {
    fun raw(key: Preferences.Key<String>, fallback: String) = this[key] ?: fallback
    return HomePreferences(
        background = enumValue<BackgroundMode>(raw(HomeViewModel.BackgroundKey, BackgroundMode.Photo.name)).let {
            if (it == BackgroundMode.Light) BackgroundMode.Dark else it
        },
        layout = enumValue(raw(HomeViewModel.LayoutKey, HomeLayout.List.name)),
        apps = enumValue(raw(HomeViewModel.AppsKey, Presentation.Text.name)),
        controls = enumValue(raw(HomeViewModel.ControlsKey, Presentation.Icons.name)),
        clock = enumValue(raw(HomeViewModel.ClockKey, ClockFormat.Hour12.name)),
        typeface = TypefaceChoice.Roboto,
        wallpaperId = raw(HomeViewModel.WallpaperKey, Wallpapers.first().id),
        homePackages = (this[HomeViewModel.HomeKey] ?: "").split('|').filter { it.isNotBlank() },
        seeded = this[HomeViewModel.SeededKey] ?: false,
        recentlyRemoved = (this[HomeViewModel.RemovedKey] ?: "").split('|').filter { it.isNotBlank() },
        artMode = this[HomeViewModel.ArtKey] ?: false,
    )
}

private inline fun <reified T : Enum<T>> enumValue(raw: String): T {
    return enumValues<T>().firstOrNull { it.name == raw } ?: enumValues<T>().first()
}
