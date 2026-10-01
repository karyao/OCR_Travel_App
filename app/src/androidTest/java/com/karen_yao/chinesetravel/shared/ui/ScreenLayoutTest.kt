package com.karen_yao.chinesetravel.shared.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.karen_yao.chinesetravel.MainActivity
import com.karen_yao.chinesetravel.R
import com.karen_yao.chinesetravel.core.database.entities.PlaceSnap
import com.karen_yao.chinesetravel.databinding.ItemSnapBinding
import com.karen_yao.chinesetravel.features.home.ui.SnapsAdapter
import com.karen_yao.chinesetravel.features.home.ui.SnapsViewHolder
import com.karen_yao.chinesetravel.features.textselection.ui.TextOptionAdapter
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.views.MapView

/** Geometry checks run at real Android font scales without changing the user's device settings. */
@RunWith(AndroidJUnit4::class)
class ScreenLayoutTest {
    @Test fun repeatedInsetsReplacePaddingAndAccountForCutouts() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = FrameLayout(activity).apply {
                    setPadding(2, 3, 4, 5)
                    applyScreenInsets()
                }
                val first = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 28))
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(18, 32, 0, 0)).build()
                repeat(3) { ViewCompat.dispatchApplyWindowInsets(view, first) }
                assertEquals(Rect(20, 35, 4, 33), padding(view))
                val second = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 12, 0, 16)).build()
                ViewCompat.dispatchApplyWindowInsets(view, second)
                assertEquals(Rect(2, 15, 4, 21), padding(view))
            }
        }
    }

    @Test fun allScreenBoundariesAndGuttersAdaptToWidthAndFontSize() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val sizes = listOf(320 to 640, 360 to 640, 412 to 640, 640 to 320)
                for ((width, height) in sizes) for (fontScale in listOf(1f, 2f)) {
                    val config = Configuration(activity.resources.configuration).apply {
                        densityDpi = 160
                        screenWidthDp = width
                        screenHeightDp = height
                        this.fontScale = fontScale
                        orientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                    }
                    val context = ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_ChineseTravel)
                    val inflater = LayoutInflater.from(activity).cloneInContext(context)
                    val screens = listOf(
                        R.layout.fragment_home to "home", R.layout.fragment_capture to "capture",
                        R.layout.fragment_text_selection to "selection", R.layout.fragment_tutorial to "tutorial",
                        R.layout.fragment_welcome to "welcome", R.layout.fragment_map to "map"
                    )
                    for ((layout, name) in screens) {
                        val root = inflater.inflate(layout, null)
                        prepare(root, name)
                        measure(root, width, height)
                        checkBoundaries(root, name)
                        if (name == "home") checkHome(root, width, height)
                        if (name == "selection") checkSelection(root, width, height)
                        if (name == "capture") checkCapture(root, width, height)
                        if (name == "map") {
                            val zoom = root.findViewById<View>(R.id.mapZoomControls)
                            val attribution = root.findViewById<View>(R.id.tvOsmAttribution)
                            assertTrue(bounds(root, zoom).right <= bounds(root, attribution).left)
                        }
                        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                            ?.let(::File) ?: activity.getExternalFilesDir("ui-verification")!!
                        saveLayout(root, File(output, "$name-${width}x$height-font$fontScale.png"))
                        root.findViewById<MapView>(R.id.mapView)?.onDetach()
                    }
                }
            }
        }
    }

    @Test fun actionsStackForLongLabelsAndUnstackWhenSpaceReturns() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.layoutInflater.inflate(R.layout.fragment_text_selection, null)
                val actions = root.findViewById<AdaptiveActionLayout>(R.id.selectionActions)
                root.findViewById<TextView>(R.id.btnConfirmSelection).text = "A much longer confirmation action"
                measure(root, 640, 1600)
                assertEquals(LinearLayout.VERTICAL, actions.orientation)
                root.findViewById<TextView>(R.id.btnConfirmSelection).text = "Save"
                measure(root, 1080, 1600)
                assertEquals(LinearLayout.HORIZONTAL, actions.orientation)
            }
        }
    }

    @Test fun missingMetadataCollapsesDividerAndRecycledRowsRestoreIt() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val binding = ItemSnapBinding.inflate(activity.layoutInflater)
                val holder = SnapsViewHolder(binding) { }
                holder.bind(snap(0).copy(address = null, googleMapsLink = null), true)
                assertEquals(View.GONE, binding.locationMetadata.visibility)
                holder.bind(snap(1), true)
                assertEquals(View.VISIBLE, binding.locationMetadata.visibility)
                holder.bind(snap(2).copy(address = null), true)
                assertEquals(View.VISIBLE, binding.locationMetadata.visibility)
                assertEquals(View.GONE, binding.locationRow.visibility)
            }
        }
    }

    @Test fun tutorialKeepsPreviousSlotAndEveryStepFooterBelowContent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.layoutInflater.inflate(R.layout.fragment_tutorial, null)
                val previous = root.findViewById<View>(R.id.btnPrevious)
                val next = root.findViewById<View>(R.id.btnNext)
                val start = root.findViewById<View>(R.id.btnGetStarted)
                for (step in 0..3) {
                    previous.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE
                    next.visibility = if (step < 3) View.VISIBLE else View.GONE
                    start.visibility = if (step == 3) View.VISIBLE else View.GONE
                    measure(root, 1080, 1920)
                    val footer = root.findViewById<View>(R.id.tutorialActions)
                    val scroll = root.findViewById<View>(R.id.tutorialContent)
                    assertTrue(bounds(root, scroll).bottom <= bounds(root, footer).top)
                    assertTrue(previous.width > 0)
                    assertTrue(previous.height > 0)
                }
            }
        }
    }

    private fun prepare(root: View, screen: String) {
        root.findViewById<TextView>(R.id.tvHeaderTitle)?.text = when (screen) {
            "home" -> "Your Travel Collection"
            "capture" -> "📸 Capture Chinese Text"
            "selection" -> "📝 Select Text"
            "tutorial" -> "📚 How to Use Chinese Travel"
            else -> "🗺️ Travel Map"
        }
        if (screen == "home") {
            root.findViewById<TextView>(R.id.tvHeaderRight).apply { text = "8 snaps"; visibility = View.VISIBLE }
            root.findViewById<View>(R.id.btnHeaderOverflow).visibility = View.VISIBLE
            root.findViewById<View>(R.id.loadingIndicator).visibility = View.GONE
            val list = root.findViewById<RecyclerView>(R.id.recycler)
            list.visibility = View.VISIBLE
            list.layoutManager = LinearLayoutManager(root.context)
            list.addItemDecoration(CardSpacingDecoration(root.resources.getDimensionPixelSize(R.dimen.list_gap)))
            list.adapter = SnapsAdapter { }.apply { submitList((0..7).map(::snap)) }
            root.findViewById<View>(R.id.floatingActions).reserveFloatingActionSpace(list)
        }
        if (screen == "selection") {
            root.findViewById<RecyclerView>(R.id.rvTextOptions).apply {
                layoutManager = LinearLayoutManager(root.context)
                addItemDecoration(CardSpacingDecoration(resources.getDimensionPixelSize(R.dimen.list_gap)))
                adapter = TextOptionAdapter((0..39).map { "永庆坊八邑酒楼 — 第${it + 1}行" }) { }.apply { render(1, true) }
            }
            root.findViewById<View>(R.id.btnConfirmSelection).isEnabled = true
        }
        if (screen == "capture") {
            root.findViewById<TextView>(R.id.tvCaptureStatus).apply {
                setText(R.string.capture_loading_image)
                visibility = View.VISIBLE
            }
        }
        if (screen == "map") {
            root.findViewById<View>(R.id.mapView).visibility = View.INVISIBLE
            root.findViewById<View>(R.id.mapZoomControls).visibility = View.VISIBLE
            root.findViewById<View>(R.id.tvOsmAttribution).visibility = View.VISIBLE
        }
    }

    private fun checkBoundaries(root: View, screen: String) {
        val header = root.findViewById<View>(R.id.headerLayout) ?: return
        val contentId = when (screen) {
            "home" -> R.id.homeContent
            "capture" -> R.id.captureContent
            "selection" -> R.id.selectionContent
            "tutorial" -> R.id.tutorialContent
            else -> R.id.mapContent
        }
        val content = root.findViewById<View>(contentId)
        assertTrue("$screen body overlaps header", bounds(root, content).top >= bounds(root, header).bottom)
        assertTrue("$screen body has no usable height", content.height > 0)
        val footerId = when (screen) {
            "capture" -> R.id.captureControls
            "selection" -> R.id.selectionActions
            "tutorial" -> R.id.tutorialActions
            else -> null
        }
        if (footerId != null) {
            val footer = root.findViewById<View>(footerId)
            assertTrue("$screen body overlaps footer", bounds(root, content).bottom <= bounds(root, footer).top)
            assertTrue("$screen footer is outside screen", bounds(root, footer).bottom <= root.height)
            fun checkLabels(view: View) {
                if (view.visibility != View.VISIBLE) return
                if (view is TextView) {
                    val layout = view.layout ?: return
                    val label = view.transformationMethod?.getTransformation(view.text, view) ?: view.text
                    assertEquals("$screen action label is clipped", label.length, layout.getLineEnd(layout.lineCount - 1))
                    for (line in 0 until layout.lineCount) {
                        assertEquals("$screen action label is ellipsized", 0, layout.getEllipsisCount(line))
                        assertTrue("$screen ${view.resources.getResourceEntryName(view.id)} label '${view.text}' " +
                            "is too wide: ${layout.getLineWidth(line)} > ${layout.width} at font ${view.resources.configuration.fontScale}",
                            layout.getLineWidth(line) <= layout.width + 1)
                    }
                    if (view.id == R.id.btnGallery || view.id == R.id.btnShoot) {
                        val word = label.toString().substringAfterLast(' ')
                        assertTrue("Capture action word was split across lines", (0 until layout.lineCount).any {
                            label.subSequence(layout.getLineStart(it), layout.getLineEnd(it)).contains(word)
                        })
                    }
                } else if (view is ViewGroup) {
                    for (index in 0 until view.childCount) checkLabels(view.getChildAt(index))
                }
            }
            checkLabels(footer)
        }
    }

    private fun checkCapture(root: View, width: Int, height: Int) {
        val scroll = root.findViewById<NestedScrollView>(R.id.captureContent)
        val status = root.findViewById<TextView>(R.id.tvCaptureStatus)
        val preview = root.findViewById<View>(R.id.previewView)
        scroll.scrollTo(0, scroll.getChildAt(0).height)
        measure(root, width, height)
        // Descendant coordinate conversion also subtracts the scroll view's own
        // scroll offset; add it back when measuring its fixed viewport bounds.
        val viewport = bounds(root, scroll).apply { offset(scroll.scrollX, scroll.scrollY) }
        assertTrue("Capture status overlaps preview", bounds(root, status).top >= bounds(root, preview).bottom)
        assertTrue("Capture status is obscured by controls at ${width}x$height font ${root.resources.configuration.fontScale}: " +
            "status=${bounds(root, status)}, viewport=$viewport",
            bounds(root, status).bottom <= viewport.bottom)
        assertTrue("Capture status cannot be reached by scrolling", bounds(root, status).top >= viewport.top)
        val layout = status.layout
        assertEquals(status.text.length, layout.getLineEnd(layout.lineCount - 1))
        for (line in 0 until layout.lineCount) {
            assertEquals(0, layout.getEllipsisCount(line))
            assertTrue(layout.getLineWidth(line) <= layout.width + 1)
        }
    }

    private fun checkHome(root: View, width: Int, height: Int) {
        val list = root.findViewById<RecyclerView>(R.id.recycler)
        val actions = root.findViewById<View>(R.id.floatingActions)
        val gutter = root.resources.getDimensionPixelSize(R.dimen.screen_gutter)
        val first = list.getChildAt(0)
        assertNotNull(first)
        assertEquals(gutter, bounds(root, first).left)
        assertEquals(root.width - gutter, bounds(root, first).right)
        list.scrollToPosition(7)
        measure(root, width, height)
        list.scrollBy(0, Int.MAX_VALUE)
        val last = list.findViewHolderForAdapterPosition(7)!!.itemView
        assertTrue("Last card cannot clear floating actions", bounds(root, last).bottom <= bounds(root, actions).top - gutter)
    }

    private fun checkSelection(root: View, width: Int, height: Int) {
        val list = root.findViewById<RecyclerView>(R.id.rvTextOptions)
        assertFalse(list.isNestedScrollingEnabled)
        assertNotNull(list.findViewHolderForAdapterPosition(39))
        val scroll = root.findViewById<NestedScrollView>(R.id.selectionContent)
        scroll.scrollTo(0, scroll.getChildAt(0).height)
        measure(root, width, height)
        val last = list.findViewHolderForAdapterPosition(39)!!.itemView
        val gutter = root.resources.getDimensionPixelSize(R.dimen.screen_gutter)
        assertEquals(gutter, bounds(root, last).left)
        assertTrue("Final option obscured by footer", bounds(root, last).bottom <= bounds(root, root.findViewById(R.id.selectionActions)).top)
        assertTrue("Final option scrolled out of the viewport",
            bounds(root, last).bottom >= bounds(root, scroll).top + scroll.paddingTop)
    }

    private fun measure(root: View, width: Int, height: Int) {
        repeat(2) {
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, width, height)
        }
    }

    private fun bounds(root: View, child: View): Rect = Rect(0, 0, child.width, child.height).also {
        (root as ViewGroup).offsetDescendantRectToMyCoords(child, it)
    }
    private fun padding(view: View) = Rect(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
    private fun saveLayout(root: View, file: File) {
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun snap(index: Int) = PlaceSnap(
        id = "layout-$index", imagePath = "", nameCn = "永庆坊八邑酒楼 — 第${index + 1}站",
        namePinyin = "yǒng qìng fāng bā yì jiǔ lóu", lat = null, longitude = null,
        address = "A long address that wraps across multiple lines in a narrow travel collection card",
        translation = "A place with a longer English translation that should stay readable",
        googleMapsLink = "https://maps.google.com/?q=23,113"
    )
}
