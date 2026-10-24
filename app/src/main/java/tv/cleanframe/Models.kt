package tv.cleanframe

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.ImageBitmap
import tv.cleanframe.R

enum class BackgroundMode { Photo, Light, Dark }

enum class HomeLayout { List, Dock }

enum class Presentation { Text, Icons, Both }

enum class ClockFormat { Hour12, Hour24 }

enum class TypefaceChoice { Roboto }

enum class Screen { Home, AllApps, Editor, Settings }

data class Wallpaper(
    val id: String,
    val name: String,
    @DrawableRes val image: Int,
    val credit: String,
)

val Wallpapers = listOf(
    Wallpaper("waterfall", "Waterfall", R.drawable.wallpaper_waterfall, "Unsplash"),
    Wallpaper("ripples", "Ripples", R.drawable.wallpaper_ripples, "Unsplash"),
    Wallpaper("desert", "Desert", R.drawable.wallpaper_desert, "Unsplash"),
    Wallpaper("aurora", "Aurora", R.drawable.wallpaper_aurora, "Unsplash"),
    Wallpaper("golden", "Golden", R.drawable.wallpaper_golden, "Unsplash"),
    Wallpaper("tower", "Tower", R.drawable.wallpaper_tower, "Unsplash"),
    Wallpaper("koi", "Koi", R.drawable.wallpaper_koi, "Unsplash"),
    Wallpaper("frog", "Frog", R.drawable.wallpaper_frog, "Unsplash"),
    Wallpaper("curve", "Curve", R.drawable.wallpaper_curve, "Unsplash"),
    Wallpaper("brick", "Brick", R.drawable.wallpaper_brick, "Unsplash"),
    Wallpaper("oculus", "Oculus", R.drawable.wallpaper_oculus, "Unsplash"),
    Wallpaper("library", "Library", R.drawable.wallpaper_library, "Unsplash"),
    Wallpaper("arches", "Arches", R.drawable.wallpaper_arches, "Unsplash"),
    Wallpaper("figure", "Figure", R.drawable.wallpaper_figure, "Unsplash"),
    Wallpaper("slats", "Slats", R.drawable.wallpaper_slats, "Unsplash"),
    Wallpaper("facade", "Facade", R.drawable.wallpaper_facade, "Unsplash"),
)

fun wallpaperById(id: String): Wallpaper = Wallpapers.firstOrNull { it.id == id } ?: Wallpapers.first()

class WallpaperDeck {
    private val order = ArrayDeque<String>()

    fun next(current: String?): String {
        if (order.isEmpty()) {
            order.addAll(Wallpapers.map { it.id }.shuffled())
        }
        if (order.size > 1 && order.first() == current) {
            order.addLast(order.removeFirst())
        }
        return order.removeFirst()
    }
}

data class HomePreferences(
    val background: BackgroundMode = BackgroundMode.Photo,
    val layout: HomeLayout = HomeLayout.List,
    val apps: Presentation = Presentation.Text,
    val controls: Presentation = Presentation.Icons,
    val clock: ClockFormat = ClockFormat.Hour12,
    val typeface: TypefaceChoice = TypefaceChoice.Roboto,
    val wallpaperId: String = Wallpapers.first().id,
    val homePackages: List<String> = emptyList(),
    val seeded: Boolean = false,
    val recentlyRemoved: List<String> = emptyList(),
    val artMode: Boolean = false,
)

data class Appearance(
    val background: BackgroundMode,
    val layout: HomeLayout,
    val apps: Presentation,
    val controls: Presentation,
    val clock: ClockFormat,
    val typeface: TypefaceChoice,
    val wallpaperId: String,
    val artMode: Boolean,
)

fun HomePreferences.appearance(): Appearance {
    if (!artMode) {
        return Appearance(background, layout, Presentation.Text, Presentation.Text, clock, typeface, wallpaperId, false)
    }
    return Appearance(
        background = BackgroundMode.Photo,
        layout = layout,
        apps = Presentation.Text,
        controls = Presentation.Text,
        clock = clock,
        typeface = typeface,
        wallpaperId = wallpaperId,
        artMode = true,
    )
}

data class TvApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
)

data class EditorSession(
    val home: List<String> = emptyList(),
    val removed: List<String> = emptyList(),
    val moving: String? = null,
    val moveOrigin: List<String>? = null,
    val undo: List<Pair<List<String>, List<String>>> = emptyList(),
)

const val VisibleHome = 7

val PreferredHome = listOf(
    "com.stremio.one",
    "com.stremio.tv",
    "com.netflix.ninja",
    "com.disney.disneyplus",
    "com.google.android.youtube.tv",
    "com.apple.atve.sony.appletv",
    "com.amazon.amazonvideo.livingroom",
    "com.wbd.stream",
    "se.svt.android.svtplay",
)
