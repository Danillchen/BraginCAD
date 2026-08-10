package com.bragincad

import android.app.Activity
import android.content.*
import android.content.pm.ApplicationInfo
import android.database.Cursor
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bragincad.dwg.LibreDwgAdapter
import com.bragincad.model.*
import com.bragincad.settings.SettingsRepository
import com.bragincad.ui.BraginCadTheme
import com.bragincad.ui.BraginRadii
import com.bragincad.ui.BraginSpacing
import com.bragincad.view.DrawingView
import java.io.File
import java.io.IOException
import java.text.NumberFormat
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private var model by mutableStateOf<DrawingModel?>(null)
    private var fileLabel by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var errorText by mutableStateOf<String?>(null)
    private var regressionStressStarted=false

    private var drawingView: DrawingView? = null
    private val prefs by lazy { getSharedPreferences("bragincad", MODE_PRIVATE) }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private data class Recent(val uri: String, val label: String)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        enableEdgeToEdge()
        setContent { BraginCadApp() }
        val regressionFile = regressionFile(intent)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        Log.i("BraginCAD", "RegressionInput extra=${intent.getStringExtra("regression_file")} debuggable=$debuggable path=${regressionFile?.absolutePath} exists=${regressionFile?.isFile}")
        if (regressionFile?.isFile == true) loadPath(regressionFile, regressionFile.name)
        else handleViewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);val regression=regressionFile(intent);val viewport=intent.getStringExtra("regression_viewport");val area=intent.getStringExtra("regression_area");val hasFlags=intent.hasExtra("regression_lod")||intent.hasExtra("regression_culling")||intent.hasExtra("regression_hatch");when{regression!=null->loadPath(regression,regression.name);viewport!=null->drawingView?.setRegressionViewport(viewport);area!=null->drawingView?.setRegressionArea(area=="on");hasFlags->drawingView?.setRegressionRenderFlags(intent.stringFlag("regression_lod"),intent.stringFlag("regression_culling"),intent.stringFlag("regression_hatch"));else->handleViewIntent(intent)} }

    private fun regressionFile(source:Intent):File?{val debuggable=applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0;if(!debuggable)return null;val name=source.getStringExtra("regression_file")?.takeIf{it.endsWith(".dwg",true)&&File(it).name==it}?:return null;return File(filesDir,"regression/$name").takeIf{it.isFile}}

    private fun Intent.stringFlag(name:String):Boolean?=getStringExtra(name)?.let{it=="on"||it=="true"||it=="1"}

    @Composable
    private fun BraginCadApp() {
        val appSettings by settingsRepository.settings
        val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
        val dark = when (appSettings.theme) { 1 -> false; 2 -> true; else -> systemDark }
        SideEffect { enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark }, navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark }) }
        BraginCadTheme(dark = dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    model != null -> ViewerScreen(dark, appSettings.measurementDecimalPlaces)
                    else -> StartScreen()
                }
                errorText?.let { message -> AlertDialog(onDismissRequest = { errorText = null }, title = { Text(stringResource(R.string.unable_open_drawing)) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { errorText = null }) { Text(stringResource(R.string.close)) } }) }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun StartScreen() {
        val recentItems = remember(model, loading) { recent() }
        var settingsOpen by remember { mutableStateOf(false) }
        Scaffold(contentWindowInsets=WindowInsets.safeDrawing,topBar={TopAppBar(title={Text(stringResource(R.string.app_name),style=MaterialTheme.typography.titleLarge)},actions={IconButton(onClick={settingsOpen=true}){Icon(Icons.Default.Settings,stringResource(R.string.settings))}},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))}) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=BraginSpacing.XLarge)) {
                Spacer(Modifier.height(BraginSpacing.XLarge))
                Row(verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(BraginRadii.Large),color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(56.dp)){Icon(UxIcons.Brand,null,Modifier.padding(14.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)};Spacer(Modifier.width(BraginSpacing.Large));Column{Text(stringResource(R.string.app_name),style=MaterialTheme.typography.headlineSmall);Text(stringResource(R.string.about_text),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)}}
                Spacer(Modifier.height(BraginSpacing.XLarge))
                Button(onClick=::openDocument,enabled=!loading,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(BraginRadii.Medium)){if(loading)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)else Icon(UxIcons.Folder,null);Spacer(Modifier.width(BraginSpacing.Medium));Text(if(loading)stringResource(R.string.loading_drawing) else stringResource(R.string.open_drawing))}
                Spacer(Modifier.height(32.dp));Text(stringResource(R.string.recent),style=MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(BraginSpacing.Small));HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                if(recentItems.isEmpty())Box(Modifier.fillMaxWidth().height(72.dp),contentAlignment=Alignment.CenterStart){Text(stringResource(R.string.no_recent_files),color=MaterialTheme.colorScheme.onSurfaceVariant)}
                else LazyColumn(Modifier.fillMaxWidth().weight(1f)){items(recentItems,key={it.uri}){item->ListItem(headlineContent={Text(item.label,maxLines=1,overflow=TextOverflow.Ellipsis)},leadingContent={Icon(UxIcons.Document,null,tint=MaterialTheme.colorScheme.primary)},colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.background),modifier=Modifier.clickable{openDrawing(Uri.parse(item.uri),item.label,false)});HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)}}
            }
        }
        if(settingsOpen)SettingsDialog{settingsOpen=false}
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ViewerScreen(dark: Boolean, measurementDecimalPlaces: Int) {
        var menuOpen by remember { mutableStateOf(false) }
        var layersOpen by remember { mutableStateOf(false) }
        var measuring by remember { mutableStateOf(false) }
        var areaMode by remember { mutableStateOf(false) }
        var coordinateMode by remember { mutableStateOf(false) }
        var coordinatePoint by remember { mutableStateOf<Point2?>(null) }
        var coordinateZ by remember { mutableStateOf<Double?>(null) }
        var coordinateSnapped by remember { mutableStateOf(false) }
        var textHidden by remember { mutableStateOf(false) }
        var measurementPrompt by remember { mutableStateOf(getString(R.string.measurement_first)) }
        var measuredDistance by remember { mutableStateOf<Double?>(null) }
        var measuredArea by remember { mutableStateOf<Double?>(null) }
        var measuredUnit by remember { mutableStateOf(DrawingUnit.UNKNOWN) }
        var measurementSnapped by remember { mutableStateOf(false) }
        var areaPoints by remember { mutableIntStateOf(0) }
        var overflowOpen by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf<String?>(null) }
        var settingsOpen by remember { mutableStateOf(false) }
        val drawing = model ?: return
        var renderReady by remember(drawing) { mutableStateOf(false) }
        val measurement = when {
            measuredDistance != null -> formatDistance(measuredDistance!!, measuredUnit, measurementSnapped, measurementDecimalPlaces)
            measuredArea != null && areaPoints >= 3 -> formatArea(measuredArea!!, measuredUnit, measurementDecimalPlaces)
            else -> measurementPrompt
        }
        BackHandler(menuOpen || layersOpen || measuring) { when { layersOpen -> layersOpen = false; menuOpen -> menuOpen = false; measuring -> { drawingView?.cancelMeasurement(); measuring = false } } }
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            topBar = {
                TopAppBar(title = { Text(fileLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { IconButton(onClick = { drawingView?.cancelMeasurement(); model = null; fileLabel = "" }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) } }, actions = { Box { IconButton(onClick = { overflowOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }; DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) { DropdownMenuItem(text = { Text(stringResource(R.string.drawing_information)) }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { overflowOpen = false; dialog = "info" }); DropdownMenuItem(text = { Text(stringResource(R.string.statistics)) }, leadingIcon = { Icon(UxIcons.Chart, null) }, onClick = { overflowOpen = false; dialog = "stats" }); DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, leadingIcon = { Icon(Icons.Default.Settings, null) }, onClick = { overflowOpen = false; settingsOpen = true }); DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, leadingIcon = { Icon(UxIcons.Brand, null) }, onClick = { overflowOpen = false; dialog = "about" }) } } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)))
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AndroidView(factory = { context -> DrawingView(context).also { drawingView = it } }, update = { view ->
                    view.onRenderModelReady = { ms -> runOnUiThread { renderReady=true;DwgOpenDiagnostics.stage("08_RENDER_MODEL_END","buildMs=$ms") } }
                    view.onFirstRender = { ms -> DwgOpenDiagnostics.stage("10_FIRST_RENDER_END","renderMs=$ms");if(intent.getBooleanExtra("regression_stress",false)&&!regressionStressStarted){regressionStressStarted=true;view.startPerformanceStress()} }
                    if (view.model !== drawing) view.model = drawing
                    view.setMeasurementDecimalPlaces(measurementDecimalPlaces)
                    val bg = if (dark) AndroidColor.rgb(8,13,17) else AndroidColor.rgb(244,247,249)
                    val fg = if (dark) AndroidColor.rgb(224, 231, 235) else AndroidColor.rgb(20, 25, 31)
                    view.setPalette(bg, fg, AndroidColor.rgb(0, 150, 199))
                    view.onCanvasTap = { if (menuOpen) menuOpen = false }
                    view.onFirstMeasurePoint = { runOnUiThread { measurementPrompt = getString(R.string.measurement_second); measuredDistance=null } }
                    view.onMeasurement = { distance, unit, snapped -> runOnUiThread { measuredDistance=distance;measuredUnit=unit;measurementSnapped=snapped } }
                    view.onCoordinateSelected = { point, z, _, snapped -> runOnUiThread { coordinatePoint=point;coordinateZ=z;coordinateSnapped=snapped } }
                    view.onAreaChanged = { area, unit, points -> runOnUiThread { measuredArea=area;measuredUnit=unit;areaPoints=points;measurementPrompt=getString(R.string.area_points_needed,points) } }
                }, modifier = Modifier.fillMaxSize().semantics { contentDescription = getString(R.string.drawing_ready) })

                if(renderReady)Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(visible = menuOpen && !measuring, enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 }, exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 }) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                            MenuFab(UxIcons.Fit, stringResource(R.string.fit_drawing)) { drawingView?.fitToDrawing(); menuOpen = false }
                            MenuFab(UxIcons.Ruler, stringResource(R.string.measure)) { drawingView?.startMeasurement(); measuring=true;areaMode=false;coordinateMode=false;measuredDistance=null;measuredArea=null;measurementPrompt=getString(R.string.measurement_first);menuOpen=false }
                            MenuFab(UxIcons.Area, stringResource(R.string.area)) { drawingView?.startArea();measuring=true;areaMode=true;coordinateMode=false;measuredDistance=null;measuredArea=null;areaPoints=0;measurementPrompt=getString(R.string.area_points_needed,0);menuOpen=false }
                            MenuFab(UxIcons.Coordinates, stringResource(R.string.coordinates)) { drawingView?.startCoordinates();measuring=true;areaMode=false;coordinateMode=true;coordinatePoint=null;coordinateZ=null;menuOpen=false }
                            MenuFab(UxIcons.TextVisibility, stringResource(if(textHidden)R.string.show_text else R.string.hide_text)) { textHidden=!textHidden;drawingView?.setTextVisible(!textHidden);menuOpen=false }
                            MenuFab(UxIcons.Layers, stringResource(R.string.layers)) { layersOpen = true; menuOpen = false }
                            MenuFab(UxIcons.Folder, stringResource(R.string.open_drawing)) { menuOpen = false; openDocument() }
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                    if (!measuring) FloatingActionButton(onClick = { menuOpen = !menuOpen }, shape = CircleShape, modifier = Modifier.size(56.dp).semantics { contentDescription = getString(R.string.main_menu) }) { Icon(if (menuOpen) Icons.Default.Close else UxIcons.Brand, null) }
                }

                if(!renderReady)Surface(Modifier.align(Alignment.Center),shape=RoundedCornerShape(BraginRadii.Large),color=MaterialTheme.colorScheme.surfaceContainerHigh,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),tonalElevation=3.dp){Row(Modifier.padding(horizontal=BraginSpacing.Large,vertical=BraginSpacing.Medium),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp);Spacer(Modifier.width(BraginSpacing.Medium));Text(stringResource(R.string.loading_drawing))}}

                if(measuring&&coordinateMode)CoordinatePanel(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),coordinatePoint,coordinateZ,coordinateSnapped,drawing.units,measurementDecimalPlaces,onCopy={point->val text="X=${formatNumber(point.x,measurementDecimalPlaces,true)}; Y=${formatNumber(point.y,measurementDecimalPlaces,true)}"+(coordinateZ?.let{"; Z=${formatNumber(it,measurementDecimalPlaces,true)}"}?:"");(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText(getString(R.string.coordinates),text))},onClose={drawingView?.cancelMeasurement();measuring=false;coordinateMode=false})
                else if (measuring) MeasurementPanel(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp), measurement, areaMode, onCancel = { drawingView?.cancelMeasurement();measuring=false;areaMode=false }, onUndo={drawingView?.undoAreaPoint()}, onClear = { if(areaMode){drawingView?.clearArea();measuredArea=null;areaPoints=0;measurementPrompt=getString(R.string.area_points_needed,0)}else{drawingView?.startMeasurement();measuredDistance=null;measurementPrompt=getString(R.string.measurement_first)} }, onDone = { if(areaMode)drawingView?.finishArea() else drawingView?.cancelMeasurement();measuring=false;areaMode=false })
            }
        }
        if (layersOpen) ModalBottomSheet(onDismissRequest = { layersOpen = false }) { Text(stringResource(R.string.layers), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)); LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding()) { items(drawing.layers, key = { it.id }) { layer -> var checked by remember(layer.id) { mutableStateOf(drawingView?.isLayerVisible(layer.id) ?: layer.initiallyVisible) }; ListItem(headlineContent = { Text(layer.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }, trailingContent = { Switch(checked = checked, onCheckedChange = { checked = it; drawingView?.setLayerVisible(layer.id, it) }, modifier = Modifier.semantics { contentDescription = getString(if (checked) R.string.layer_visible else R.string.layer_hidden, layer.name) }) }, modifier = Modifier.clickable { checked = !checked; drawingView?.setLayerVisible(layer.id, checked) }) } } }
        dialog?.let { kind -> InfoDialog(kind, drawing) { dialog = null } }
        if (settingsOpen) SettingsDialog(onDismiss = { settingsOpen = false })
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun MenuFab(icon: ImageVector,label:String,action:()->Unit){val state=rememberTooltipState();TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),tooltip={PlainTooltip{Text(label)}},state=state){Surface(shape=RoundedCornerShape(BraginRadii.Large),color=MaterialTheme.colorScheme.surfaceContainerHigh,tonalElevation=3.dp,shadowElevation=3.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.size(50.dp).semantics{contentDescription=label}.clickable(onClick=action)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(22.dp))}}}}

    @Composable private fun MeasurementPanel(modifier:Modifier,value:String,area:Boolean,onCancel:()->Unit,onUndo:()->Unit,onClear:()->Unit,onDone:()->Unit){Surface(modifier,shape=RoundedCornerShape(BraginRadii.Floating),tonalElevation=4.dp,shadowElevation=4.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),color=MaterialTheme.colorScheme.surfaceContainerHigh){Row(Modifier.padding(BraginSpacing.Small),verticalAlignment=Alignment.CenterVertically){Icon(if(area)UxIcons.Area else UxIcons.Ruler,stringResource(if(area)R.string.area_mode else R.string.measurement_mode),tint=MaterialTheme.colorScheme.primary);Text(value,Modifier.padding(horizontal=BraginSpacing.Medium).weight(1f,fill=false),maxLines=2);if(area)IconButton(onClick=onUndo){Icon(Icons.Default.ArrowBack,stringResource(R.string.undo_point))};IconButton(onClick=onCancel){Icon(Icons.Default.Close,stringResource(R.string.cancel))};IconButton(onClick=onClear){Icon(Icons.Default.Delete,stringResource(R.string.clear))};FilledIconButton(onClick=onDone){Icon(Icons.Default.Check,stringResource(R.string.done))}}}}

    @Composable private fun CoordinatePanel(modifier:Modifier,point:Point2?,z:Double?,snapped:Boolean,unit:DrawingUnit,decimalPlaces:Int,onCopy:(Point2)->Unit,onClose:()->Unit){Surface(modifier,shape=RoundedCornerShape(BraginRadii.Floating),tonalElevation=4.dp,shadowElevation=4.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),color=MaterialTheme.colorScheme.surfaceContainerHigh){Row(Modifier.padding(BraginSpacing.Medium),verticalAlignment=Alignment.CenterVertically){Icon(UxIcons.Coordinates,stringResource(R.string.coordinates),tint=MaterialTheme.colorScheme.primary);Column(Modifier.padding(horizontal=BraginSpacing.Medium).weight(1f,false)){Text(if(point==null)stringResource(R.string.coordinate_tap) else "X: ${formatNumber(point.x,decimalPlaces,true)}\nY: ${formatNumber(point.y,decimalPlaces,true)}"+(z?.let{"\nZ: ${formatNumber(it,decimalPlaces,true)}"}?:""),maxLines=3);if(point!=null)Text((if(snapped)stringResource(R.string.endpoint_snapped)+" · " else "")+stringResource(R.string.coordinate_units,localizedUnitName(unit)),style=MaterialTheme.typography.labelSmall)};if(point!=null)TextButton(onClick={onCopy(point)}){Text(stringResource(R.string.copy))};IconButton(onClick=onClose){Icon(Icons.Default.Close,stringResource(R.string.close))}}}}

    @Composable private fun InfoDialog(kind: String, drawing: DrawingModel, dismiss: () -> Unit) { val title: String; val body: String; when (kind) { "stats" -> { title = stringResource(R.string.statistics); val s = drawing.stats; body = stringResource(R.string.stats_body, drawing.version, s.totalObjects, s.graphicalEntities, s.supportedEntities, formatNumber(100.0 * (s.visibleGraphicalEntities - s.unsupportedVisibleGraphicalEntities) / s.visibleGraphicalEntities, 2), s.renderPrimitiveCount) }; "about" -> { title = stringResource(R.string.about); body = stringResource(R.string.about_text) }; else -> { title = stringResource(R.string.drawing_information); body = stringResource(R.string.info_body, fileLabel, drawing.version, localizedUnitName(drawing.units), drawing.layers.size, drawing.stats.totalObjects) } }; AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(body) }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) } }) }

    @Composable private fun SettingsDialog(onDismiss: () -> Unit) {
        val appSettings by settingsRepository.settings
        AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.settings))},text={
            Column {
                Text(stringResource(R.string.measurements_settings),style=MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.measurement_precision),modifier=Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.decimal_places,appSettings.measurementDecimalPlaces),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    (0..4).forEach { places -> FilterChip(selected=appSettings.measurementDecimalPlaces==places,onClick={settingsRepository.setMeasurementDecimalPlaces(places)},label={Text(places.toString())}) }
                }
                HorizontalDivider(Modifier.padding(vertical=16.dp))
                Text(stringResource(R.string.theme),style=MaterialTheme.typography.titleSmall)
                listOf(R.string.theme_system,R.string.theme_light,R.string.theme_dark).forEachIndexed { index,id ->
                    Row(Modifier.fillMaxWidth().clickable { settingsRepository.setTheme(index) }.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                        RadioButton(selected=appSettings.theme==index,onClick={settingsRepository.setTheme(index)});Text(stringResource(id))
                    }
                }
            }
        },confirmButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.done))}})
    }

    private fun openDocument() { val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/acad", "application/x-acad", "application/dwg", "image/vnd.dwg", "application/octet-stream")) }; startActivityForResult(intent, OPEN_DWG) }
    @Deprecated("Activity result retained to keep the milestone dependency-free") override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode != OPEN_DWG || resultCode != Activity.RESULT_OK) return; val uri = data?.data ?: return; openDrawing(uri, displayName(uri) ?: "drawing.dwg", true) }
    private fun handleViewIntent(source: Intent) { if (source.action != Intent.ACTION_VIEW) return; val uri=source.data ?: return; openDrawing(uri, displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "drawing.dwg", true) }
    private fun openDrawing(uri: Uri, label: String, persistPermission: Boolean) {
        val normalizedLabel=label.ifBlank { "drawing.dwg" };val mime=contentResolver.getType(uri)
        if (!normalizedLabel.endsWith(".dwg",true)&&mime !in DWG_MIME_TYPES) { Toast.makeText(this,R.string.invalid_file,Toast.LENGTH_LONG).show();return }
        if(persistPermission)runCatching{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}.onFailure{Log.i("BraginCAD","URI permission is transient for $uri: ${it.message}")}
        loading=true;thread(name="dwg-copy-parse") { var local:File?=null;try{val dir=File(cacheDir,"opened").apply{mkdirs()};local=File.createTempFile("drawing_",".dwg",dir);contentResolver.openInputStream(uri).use{input->if(input==null)throw IOException("ContentResolver returned no stream for $uri");local.outputStream().buffered().use{output->input.copyTo(output)}};if(local.length()<6)throw IOException("DWG stream is empty or truncated");DwgOpenDiagnostics.begin(cacheDir,local,normalizedLabel);parse(local,normalizedLabel){addRecent(uri,normalizedLabel)}}catch(t:Throwable){showError(t)}finally{local?.delete()} }
    }
    private fun loadPath(file: File, label: String) { loading = true; thread(name = "dwg-parser") { try { DwgOpenDiagnostics.begin(cacheDir,file,label);parse(file, label) } catch (t: Throwable) { showError(t) } } }
    private fun parse(file: File, label: String, opened:()->Unit={}) { DwgOpenDiagnostics.stage("02_NATIVE_READ_BEGIN");val drawing = LibreDwgAdapter().read(file.absolutePath);DwgOpenDiagnostics.stage("07_DRAWING_MODEL_END","objects=${drawing.stats.totalObjects} primitives=${drawing.stats.renderPrimitiveCount}"); val unsupported = drawing.stats.unsupportedByType.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" };val breakdown=drawing.stats.byType.entries.sortedByDescending{it.value}.joinToString{ "${it.key}=${it.value}" }; Log.i("BraginCAD", "file=$label version=${drawing.version} objects=${drawing.stats.totalObjects} graphical=${drawing.stats.graphicalEntities} visibleGraphical=${drawing.stats.visibleGraphicalEntities} service=${drawing.stats.serviceObjects} structural=${drawing.stats.structuralEntities} supported=${drawing.stats.supportedEntities} unsupportedGraphical=${drawing.stats.unsupportedGraphicalEntities} unsupportedVisible=${drawing.stats.unsupportedVisibleGraphicalEntities} coverage=${"%.2f".format(Locale.US, 100.0 * drawing.stats.supportedEntities / drawing.stats.graphicalEntities)}% visibleCoverage=${"%.2f".format(Locale.US, 100.0 * (drawing.stats.visibleGraphicalEntities - drawing.stats.unsupportedVisibleGraphicalEntities) / drawing.stats.visibleGraphicalEntities)}% primitives=${drawing.stats.renderPrimitiveCount} layers=${drawing.layers.size} units=${drawing.units} modelBuildMs=${drawing.parseMs} unsupportedRanking=[$unsupported] entityBreakdown=[$breakdown]"); runOnUiThread { opened();DwgOpenDiagnostics.stage("08_RENDER_MODEL_BEGIN");model = drawing; fileLabel = label; loading = false } }
    private fun showError(t: Throwable) { DwgOpenDiagnostics.failure(t);Log.e("BraginCAD", "DWG open failed type=${t.javaClass.name}", t); runOnUiThread { loading = false; errorText = getString(R.string.unable_open_drawing) } }
    private fun formatDistance(value:Double,unit:DrawingUnit,snapped:Boolean,decimalPlaces:Int):String{val factor=millimetersPerUnit(unit);if(factor==null)return "${formatNumber(value,decimalPlaces,true)} ${getString(R.string.drawing_units)}";val mm=value*factor;val suffix=if(snapped)" • ${getString(R.string.endpoint_snapped)}" else "";return "${formatNumber(mm,decimalPlaces)} мм  ·  ${formatNumber(mm/1000,decimalPlaces,true)} м$suffix"}
    private fun formatArea(value:Double,unit:DrawingUnit,decimalPlaces:Int):String{val factor=millimetersPerUnit(unit)?:return "${formatNumber(value,decimalPlaces,true)} ${getString(R.string.square_drawing_units)}";val mm2=value*factor*factor;return "${formatNumber(mm2,decimalPlaces)} мм²  ·  ${formatNumber(mm2/1_000_000,decimalPlaces,true)} м²"}
    private fun millimetersPerUnit(unit:DrawingUnit):Double?=when(unit){DrawingUnit.INCHES->25.4;DrawingUnit.FEET->304.8;DrawingUnit.MILES->1_609_344.0;DrawingUnit.MILLIMETERS->1.0;DrawingUnit.CENTIMETERS->10.0;DrawingUnit.METERS->1000.0;DrawingUnit.KILOMETERS->1_000_000.0;DrawingUnit.MICROINCHES->.0000254;DrawingUnit.MILS->.0254;DrawingUnit.YARDS->914.4;DrawingUnit.MICRONS->.001;DrawingUnit.DECIMETERS->100.0;DrawingUnit.DEKAMETERS->10_000.0;DrawingUnit.HECTOMETERS->100_000.0;else->null}
    private fun localizedUnitName(unit:DrawingUnit):String=when(unit){DrawingUnit.MILLIMETERS->getString(R.string.unit_millimeters);DrawingUnit.CENTIMETERS->getString(R.string.unit_centimeters);DrawingUnit.METERS->getString(R.string.unit_meters);DrawingUnit.UNITLESS,DrawingUnit.UNKNOWN->getString(R.string.unit_unspecified);else->unit.symbol}
    private fun formatNumber(value:Double,decimals:Int,fixed:Boolean=false):String=NumberFormat.getNumberInstance(Locale("ru","RU")).apply{maximumFractionDigits=decimals;minimumFractionDigits=if(fixed)decimals else 0;isGroupingUsed=true}.format(value)
    private fun displayName(uri: Uri): String? { var cursor: Cursor? = null; return try { cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null); if (cursor?.moveToFirst() == true) cursor.getString(0) else null } finally { cursor?.close() } }
    private fun recent(): MutableList<Recent> = prefs.getString("recent", "").orEmpty().split('\u001e').filter { it.contains('\u001f') }.map { val p = it.split('\u001f', limit = 2); Recent(p[0], p[1]) }.toMutableList()
    private fun addRecent(uri: Uri, label: String) { val items = recent(); items.removeAll { it.uri == uri.toString() }; items.add(0, Recent(uri.toString(), label)); prefs.edit().putString("recent", items.take(10).joinToString("\u001e") { "${it.uri}\u001f${it.label}" }).apply() }
    companion object { private const val OPEN_DWG = 1001;private val DWG_MIME_TYPES=setOf("application/acad","application/x-acad","application/autocad_dwg","application/dwg","image/vnd.dwg","application/octet-stream") }
}
