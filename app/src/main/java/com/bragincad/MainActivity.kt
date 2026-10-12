package com.bragincad

import android.app.Activity
import android.content.*
import android.content.res.Configuration
import android.content.pm.ApplicationInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bragincad.dwg.LibreDwgAdapter
import com.bragincad.model.*
import com.bragincad.settings.SettingsRepository
import com.bragincad.settings.AppSettings
import com.bragincad.ui.BraginCadTheme
import com.bragincad.ui.BraginRadii
import com.bragincad.ui.BraginSpacing
import com.bragincad.ui.PixelFrame
import com.bragincad.ui.PixelFrameAccentPlacement
import com.bragincad.ui.PixelFrameStyle
import com.bragincad.view.DrawingView
import com.bragincad.view.OverlaySelection
import com.bragincad.view.ObjectSelection
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Executors
import org.json.JSONObject
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    private var model by mutableStateOf<DrawingModel?>(null)
    private var fileLabel by mutableStateOf("")
    private var loading by mutableStateOf(false)
    private var errorText by mutableStateOf<String?>(null)
    private var projectRevision by mutableIntStateOf(0)
    private var savedRevision by mutableIntStateOf(0)
    private var thumbnailRevision by mutableIntStateOf(0)
    private var currentDrawingFile:File?=null
    private var regressionStressStarted=false
    private val openGeneration=AtomicInteger()
    private val openExecutor=Executors.newSingleThreadExecutor{r->Thread(r,"dwg-open").apply{isDaemon=true}}

    private var drawingView: DrawingView? = null
    private val prefs by lazy { getSharedPreferences("bragincad", MODE_PRIVATE) }
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private data class Recent(val uri: String, val label: String)
    private data class CadProject(val name:String,val files:List<Recent>)
    private data class UpdateInfo(val current:String,val latest:String,val releaseUrl:String,val downloadUrl:String?,val available:Boolean,val error:Boolean=false)
    private var pendingProjectName:String?=null

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

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);val regression=regressionFile(intent);val viewport=intent.getStringExtra("regression_viewport");val area=intent.getStringExtra("regression_area");val overlays=intent.getStringExtra("regression_overlays");val hasFlags=intent.hasExtra("regression_lod")||intent.hasExtra("regression_culling")||intent.hasExtra("regression_hatch");when{regression!=null->loadPath(regression,regression.name);viewport!=null->drawingView?.setRegressionViewport(viewport);area!=null->drawingView?.setRegressionArea(area=="on");overlays!=null->drawingView?.setRegressionOverlays(overlays);hasFlags->drawingView?.setRegressionRenderFlags(intent.stringFlag("regression_lod"),intent.stringFlag("regression_culling"),intent.stringFlag("regression_hatch"));intent.getBooleanExtra("regression_stress",false)->drawingView?.startPerformanceStress();else->handleViewIntent(intent)} }

    private fun regressionFile(source:Intent):File?{val debuggable=applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0;if(!debuggable)return null;val name=source.getStringExtra("regression_file")?.takeIf{it.endsWith(".dwg",true)&&File(it).name==it}?:return null;return File(filesDir,"regression/$name").takeIf{it.isFile}}

    private fun Intent.stringFlag(name:String):Boolean?=getStringExtra(name)?.let{it=="on"||it=="true"||it=="1"}

    @Composable
    private fun BraginCadApp() {
        val appSettings by settingsRepository.settings
        val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
        val dark = when (appSettings.theme) { 1 -> false; 2 -> true; else -> systemDark }
        val darkSystemBars=dark
        SideEffect { enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { darkSystemBars }, navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { darkSystemBars }) }
        BraginCadTheme(dark = dark) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    model != null -> key(model) { ViewerScreen(dark, appSettings) }
                    else -> StartScreen()
                }
                errorText?.let { message -> AlertDialog(onDismissRequest = { errorText = null }, title = { Text(stringResource(R.string.unable_open_drawing)) }, text = { Text(message) }, confirmButton = { TextButton(onClick = { errorText = null }) { Text(stringResource(R.string.close)) } }) }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
    @Composable
    private fun StartScreen() {
        LegacyStartScreen()
        return
        val recentItems = remember(model, loading) { recent() }
        val projectItems = remember(projectRevision) { projects() }
        var settingsOpen by remember { mutableStateOf(false) }
        var aboutOpen by remember { mutableStateOf(false) }
        var createProject by remember { mutableStateOf(false) }
        var projectName by remember { mutableStateOf("") }
        var chooseRecentFor by remember { mutableStateOf<String?>(null) }
        var openedProject by remember { mutableStateOf<CadProject?>(null) }
        val backdrop=Brush.verticalGradient(listOf(Color(0xFF061927),Color(0xFF04131C),MaterialTheme.colorScheme.background))
        Scaffold(containerColor=Color.Transparent,contentWindowInsets=WindowInsets.safeDrawing,topBar={TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically){Image(painterResource(R.drawable.ic_bragincad_brand),null,Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)));Spacer(Modifier.width(10.dp));Text("Bragin",style=MaterialTheme.typography.titleLarge);Text("CAD",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)}},actions={IconButton(onClick={settingsOpen=true},modifier=Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)){Icon(Icons.Default.Settings,stringResource(R.string.settings),tint=MaterialTheme.colorScheme.primary)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=Color.Transparent))}) { padding ->
            Column(Modifier.fillMaxSize().background(backdrop).padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=16.dp)) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF087BC0),Color(0xFF064D7A),Color(0xFF0B3552)))).clickable(enabled=!loading,onClick=::openDocument).padding(14.dp)){Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(14.dp),color=Color(0xFF28B8F5).copy(alpha=.26f),modifier=Modifier.size(64.dp)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){if(loading)CircularProgressIndicator(Modifier.size(28.dp),strokeWidth=2.dp,color=Color.White)else Icon(UxIcons.Folder,null,Modifier.size(35.dp),tint=Color(0xFF72D6FF))}};Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(if(loading)stringResource(R.string.loading_drawing)else stringResource(R.string.open_drawing),style=MaterialTheme.typography.titleMedium,color=Color.White);Text(stringResource(R.string.open_drawing_hint),style=MaterialTheme.typography.bodySmall,color=Color(0xFFB9D5E5),modifier=Modifier.padding(top=3.dp))};Icon(Icons.Default.ArrowForward,null,tint=Color(0xFF65D0FF))}}
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){HomeShortcut(UxIcons.Clock,stringResource(R.string.recent),Modifier.weight(1f));HomeShortcut(Icons.Default.Star,stringResource(R.string.favorites),Modifier.weight(1f));HomeShortcut(UxIcons.Folder,stringResource(R.string.projects),Modifier.weight(1f),onClick={createProject=true});HomeShortcut(Icons.Default.Build,stringResource(R.string.soon),Modifier.weight(1f),enabled=false)}
                Spacer(Modifier.height(22.dp))
                HomeSectionHeader(stringResource(R.string.recent_files),recentItems.size)
                Spacer(Modifier.height(8.dp))
                if(recentItems.isEmpty())EmptyHomeCard(UxIcons.Document,stringResource(R.string.no_recent_files),stringResource(R.string.no_recent_hint))
                else recentItems.take(4).forEach{item->RecentFileCard(item){openDrawing(Uri.parse(item.uri),item.label,false)};Spacer(Modifier.height(8.dp))}
                Spacer(Modifier.height(16.dp))
                HomeSectionHeader(stringResource(R.string.projects),projectItems.size,onAdd={createProject=true})
                Spacer(Modifier.height(8.dp))
                if(projectItems.isEmpty())AddProjectCard{createProject=true}else FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp),maxItemsInEachRow=2){projectItems.forEach{project->ProjectCard(project,Modifier.width(164.dp)){openedProject=project}};ProjectAddTile(Modifier.width(164.dp)){createProject=true}}
                Row(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Lock,null,Modifier.size(14.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.width(6.dp));Text(stringResource(R.string.offline_short),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }
        if(settingsOpen)SettingsDialog(onDismiss={settingsOpen=false},onAbout={settingsOpen=false;aboutOpen=true})
        if(aboutOpen)AboutDialog{aboutOpen=false}
        if(createProject)AlertDialog(onDismissRequest={createProject=false},title={Text(stringResource(R.string.new_project))},text={OutlinedTextField(projectName,{projectName=it},label={Text(stringResource(R.string.project_name))},singleLine=true)},dismissButton={TextButton(onClick={createProject=false}){Text(stringResource(R.string.cancel))}},confirmButton={Row{TextButton(enabled=projectName.isNotBlank(),onClick={val name=projectName.trim();createProject=false;chooseRecentFor=name}){Text(stringResource(R.string.from_recent))};TextButton(enabled=projectName.isNotBlank(),onClick={val name=projectName.trim();createProject=false;requestProjectDocument(name)}){Text(stringResource(R.string.from_device))}}})
        chooseRecentFor?.let{name->AlertDialog(onDismissRequest={chooseRecentFor=null},title={Text(stringResource(R.string.select_file))},text={if(recentItems.isEmpty())Text(stringResource(R.string.no_recent_files))else LazyColumn(Modifier.heightIn(max=380.dp)){items(recentItems){item->ListItem(headlineContent={Text(item.label,maxLines=1,overflow=TextOverflow.Ellipsis)},leadingContent={Icon(UxIcons.Document,null)},modifier=Modifier.clickable{addProjectFile(name,item);projectRevision++;chooseRecentFor=null})}}},confirmButton={TextButton(onClick={chooseRecentFor=null}){Text(stringResource(R.string.cancel))}})}
        openedProject?.let{project->AlertDialog(onDismissRequest={openedProject=null},title={Text(project.name)},text={LazyColumn(Modifier.heightIn(max=400.dp)){items(project.files){item->ListItem(headlineContent={Text(item.label)},leadingContent={Icon(UxIcons.Document,null)},modifier=Modifier.clickable{openedProject=null;openDrawing(Uri.parse(item.uri),item.label,false)})}}},confirmButton={TextButton(onClick={openedProject=null}){Text(stringResource(R.string.close))}})}
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun LegacyStartScreen(){
        val recentItems=remember(model,loading){recent()}
        val savedItems=remember(model,loading,savedRevision){savedDrawings()}
        var settingsOpen by remember{mutableStateOf(false)}
        var aboutOpen by remember{mutableStateOf(false)}
        var deleteSaved by remember{mutableStateOf<Recent?>(null)}
        val colors=MaterialTheme.colorScheme;val background=colors.background;val card=colors.surface;val border=colors.outlineVariant;val accent=colors.primary;val text=colors.onSurface;val secondary=colors.onSurfaceVariant
        Scaffold(containerColor=background,contentWindowInsets=WindowInsets.safeDrawing,topBar={TopAppBar(title={Row(verticalAlignment=Alignment.CenterVertically){Text("Bragin",fontWeight=FontWeight.Bold,color=text,style=MaterialTheme.typography.titleLarge);Text("CAD",fontWeight=FontWeight.Bold,color=accent,style=MaterialTheme.typography.titleLarge)}},actions={IconButton(onClick={settingsOpen=true},modifier=Modifier.size(48.dp)){ToolImage(R.drawable.ic_settings,31.dp)}},colors=TopAppBarDefaults.topAppBarColors(containerColor=background))}){padding->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=28.dp)){
                item {
                    PixelFrame(PixelFrameStyle.Panel.copy(backgroundColor=card,borderColor=border,cornerAccentColor=accent), Modifier.fillMaxWidth().heightIn(min = 106.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Image(painterResource(R.drawable.ic_bragincad_brand), null, Modifier.size(72.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row { Text("Bragin", fontWeight = FontWeight.Bold, color = text, style = MaterialTheme.typography.titleLarge); Text("CAD", fontWeight = FontWeight.Bold, color = accent, style = MaterialTheme.typography.titleLarge) }
                                Text(stringResource(R.string.about_text), Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                item{PixelOpenButton(loading,accent,::openDocument)}
                item{HomeFileSectionHeader(stringResource(R.string.recent),accent,text,border)}
                if(recentItems.isEmpty())item{EmptyFileCard(stringResource(R.string.no_recent_files),card,border,secondary)}
                else items(recentItems.take(3),key={"recent:${it.uri}"}){item->DrawingFileRow(item,card,border,text,secondary,null){openDrawing(Uri.parse(item.uri),item.label,false)}}
                item{Spacer(Modifier.height(8.dp));HomeFileSectionHeader(stringResource(R.string.saved_drawings),accent,text,border)}
                if(savedItems.isEmpty())item{EmptyFileCard(stringResource(R.string.no_saved_drawings),card,border,secondary)}
                else items(savedItems,key={"saved:${it.uri}"}){item->DrawingFileRow(item,card,border,text,secondary,{deleteSaved=item}){openDrawing(Uri.parse(item.uri),item.label,false)}}
            }
        }
        if(settingsOpen)SettingsDialog(onDismiss={settingsOpen=false},onAbout={settingsOpen=false;aboutOpen=true})
        if(aboutOpen)AboutDialog{aboutOpen=false}
        deleteSaved?.let{item->AlertDialog(onDismissRequest={deleteSaved=null},title={Text(stringResource(R.string.delete_saved_copy))},text={Text(stringResource(R.string.delete_saved_copy_confirmation,item.label))},dismissButton={TextButton(onClick={deleteSaved=null}){Text(stringResource(R.string.cancel))}},confirmButton={TextButton(onClick={deleteSavedCopy(item);deleteSaved=null}){Text(stringResource(R.string.delete))}})}
    }

    @Composable private fun HomeFileSectionHeader(title:String,accent:Color,text:Color,border:Color){Column{Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.width(3.dp).height(22.dp).background(accent));Spacer(Modifier.width(9.dp));Text(title,fontWeight=FontWeight.SemiBold,color=text,style=MaterialTheme.typography.titleMedium)};HorizontalDivider(Modifier.padding(top=8.dp),color=border)}}
    @Composable private fun PixelOpenButton(loading:Boolean,accent:Color,onClick:()->Unit){
        PixelFrame(PixelFrameStyle.Primary.copy(enabled=!loading),Modifier.fillMaxWidth().height(76.dp),onClick){
            Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){if(loading)CircularProgressIndicator(Modifier.size(30.dp),strokeWidth=2.dp,color=accent)else ToolImage(R.drawable.ic_open_drawing,39.dp);Text(if(loading)stringResource(R.string.loading_drawing)else stringResource(R.string.open_drawing),Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,fontWeight=FontWeight.Bold,color=Color(0xFF101820),style=MaterialTheme.typography.titleLarge);Icon(Icons.Default.ArrowForward,null,Modifier.size(29.dp),tint=accent)}
        }
    }
    @Composable private fun EmptyFileCard(label:String,card:Color,border:Color,secondary:Color){Surface(Modifier.fillMaxWidth().height(70.dp),shape=RoundedCornerShape(10.dp),color=card,border=BorderStroke(1.dp,border)){Box(Modifier.fillMaxSize().padding(horizontal=16.dp),contentAlignment=Alignment.CenterStart){Text(label,color=secondary)}}}
    @Composable private fun DrawingFileRow(item:Recent,card:Color=MaterialTheme.colorScheme.surface,border:Color=MaterialTheme.colorScheme.outlineVariant,text:Color=MaterialTheme.colorScheme.onSurface,secondary:Color=MaterialTheme.colorScheme.onSurfaceVariant,onDelete:(()->Unit)?=null,onClick:()->Unit){PixelFrame(PixelFrameStyle.Card.copy(backgroundColor=card,borderColor=border),Modifier.fillMaxWidth().height(78.dp),onClick){Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){DrawingThumbnail(item);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(item.label,color=text,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);fileMeta(item)?.let{Text(it,color=secondary,style=MaterialTheme.typography.bodySmall,maxLines=1)}};onDelete?.let{IconButton(onClick=it){ToolImage(R.drawable.ic_delete,28.dp)}}}}}

    private fun fileMeta(item:Recent):String?{val file=runCatching{Uri.parse(item.uri).path?.let(::File)}.getOrNull()?.takeIf{it.isFile}?:return null;val size=when{file.length()>=1_048_576->String.format(Locale.US,"%.1f МБ",file.length()/1_048_576.0);file.length()>=1024->String.format(Locale.US,"%.0f КБ",file.length()/1024.0);else->"${file.length()} Б"};val date=SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(Date(file.lastModified()));return "$size · $date"}

    @Composable private fun DrawingThumbnail(item:Recent){
        val revision=thumbnailRevision
        val image=remember(item.uri,revision){BitmapFactory.decodeFile(thumbnailFile(item.uri).absolutePath)?.asImageBitmap()}
        if(image!=null)Image(image,null,Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),contentScale=ContentScale.Crop)
        else Surface(Modifier.size(52.dp),shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.primary.copy(alpha=.12f)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(UxIcons.Document,null,Modifier.size(25.dp),tint=MaterialTheme.colorScheme.primary)}}
    }

    @Composable private fun ToolImage(resource:Int,size:androidx.compose.ui.unit.Dp=24.dp){Image(painterResource(resource),null,Modifier.size(size))}

    @Composable private fun HomeShortcut(icon:ImageVector,label:String,modifier:Modifier=Modifier,enabled:Boolean=true,onClick:()->Unit={}){Surface(onClick=onClick,enabled=enabled,modifier=modifier.height(76.dp),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.94f),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Column(Modifier.fillMaxSize().padding(7.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(icon,null,Modifier.size(24.dp),tint=if(enabled)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(6.dp));Text(label,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)}}}
    @Composable private fun HomeSectionHeader(title:String,count:Int,onAdd:(()->Unit)?=null){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));if(onAdd!=null)IconButton(onClick=onAdd,modifier=Modifier.size(34.dp)){Icon(Icons.Default.Add,stringResource(R.string.add_project),tint=MaterialTheme.colorScheme.primary)};if(count>0)Text(count.toString(),Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal=9.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}}
    @Composable private fun EmptyHomeCard(icon:ImageVector,title:String,subtitle:String){Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.9f),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(13.dp));Column{Text(title,style=MaterialTheme.typography.titleSmall);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
    @Composable private fun RecentFileCard(item:Recent,onClick:()->Unit){Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().height(72.dp),shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.94f),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Row(Modifier.fillMaxSize().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(11.dp),color=MaterialTheme.colorScheme.primary.copy(alpha=.13f),modifier=Modifier.size(46.dp)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(UxIcons.Document,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)}};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(item.label,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall);Text("DWG · локальный файл",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Icon(Icons.Default.MoreVert,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)}}}
    @Composable private fun AddProjectCard(onClick:()->Unit){Surface(onClick=onClick,modifier=Modifier.fillMaxWidth().height(88.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.7f),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Row(Modifier.fillMaxSize().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){Icon(Icons.Default.Add,null,Modifier.size(26.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(9.dp));Text(stringResource(R.string.create_project),style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)}}}
    @Composable private fun ProjectCard(project:CadProject,modifier:Modifier,onClick:()->Unit){Surface(onClick=onClick,modifier=modifier.height(92.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainer,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Row(Modifier.fillMaxSize().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primary.copy(alpha=.15f),modifier=Modifier.size(44.dp)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(UxIcons.Folder,null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)}};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(project.name,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall);Text(stringResource(R.string.files_count,project.files.size),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
    @Composable private fun ProjectAddTile(modifier:Modifier,onClick:()->Unit){Surface(onClick=onClick,modifier=modifier.height(92.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.55f),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Icon(Icons.Default.Add,stringResource(R.string.add_project),Modifier.size(30.dp),tint=MaterialTheme.colorScheme.primary)}}}

    @OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
    @Composable
    private fun ViewerScreen(dark: Boolean, appSettings:AppSettings) {
        val measurementDecimalPlaces=appSettings.measurementDecimalPlaces
        var menuOpen by remember { mutableStateOf(false) }
        var layersOpen by remember { mutableStateOf(false) }
        var measuring by remember { mutableStateOf(false) }
        var areaMode by remember { mutableStateOf(false) }
        var coordinateMode by remember { mutableStateOf(false) }
        var coordinatePoint by remember { mutableStateOf<Point2?>(null) }
        var coordinateZ by remember { mutableStateOf<Double?>(null) }
        var coordinateSnapped by remember { mutableStateOf(false) }
        var offsetMode by remember { mutableStateOf(false) }
        var selectingObject by remember { mutableStateOf(false) }
        var selectedObject by remember(model) { mutableStateOf<ObjectSelection?>(null) }
        var offsetBase by remember { mutableStateOf<Point2?>(null) }
        var offsetDistance by remember { mutableStateOf("") }
        var offsetAngle by remember { mutableStateOf("0") }
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
        var saveCopyDialog by remember(drawing) { mutableStateOf(false) }
        var copyName by remember(drawing) { mutableStateOf(fileLabel.substringBeforeLast('.')) }
        var overlayCount by remember(drawing) { mutableIntStateOf(0) }
        var selectedOverlay by remember(drawing) { mutableStateOf<OverlaySelection?>(null) }
        var confirmDeleteAll by remember(drawing) { mutableStateOf(false) }
        val landscape=LocalConfiguration.current.orientation==Configuration.ORIENTATION_LANDSCAPE
        var renderReady by remember(drawing) { mutableStateOf(false) }
        val measurement = when {
            measuredDistance != null -> formatDistance(measuredDistance!!, measuredUnit, measurementSnapped, measurementDecimalPlaces)
            measuredArea != null && areaPoints >= 3 -> formatArea(measuredArea!!, measuredUnit, measurementDecimalPlaces)
            else -> measurementPrompt
        }
        BackHandler(menuOpen || layersOpen || measuring || selectingObject) { when { layersOpen -> layersOpen = false; menuOpen -> menuOpen = false; selectingObject->{drawingView?.stopObjectSelection();selectingObject=false;selectedObject=null};measuring -> { drawingView?.cancelMeasurement(); measuring = false;areaMode=false;coordinateMode=false;offsetMode=false } } }
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            topBar = {
                TopAppBar(title = { Text(fileLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { IconButton(onClick = { drawingView?.cancelMeasurement(); model = null; fileLabel = "";currentDrawingFile=null }) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) } }, actions = { Box { IconButton(onClick = { overflowOpen = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more_options)) }; DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) { DropdownMenuItem(text = { Text(stringResource(R.string.drawing_information)) }, leadingIcon = { ToolImage(R.drawable.ic_drawing_info,28.dp) }, onClick = { overflowOpen = false; dialog = "info" }); DropdownMenuItem(text = { Text(stringResource(R.string.statistics)) }, leadingIcon = { ToolImage(R.drawable.ic_statistics,28.dp) }, onClick = { overflowOpen = false; dialog = "stats" });DropdownMenuItem(text={Text(stringResource(R.string.save_offline_copy))},leadingIcon={ToolImage(R.drawable.ic_save_copy,28.dp)},onClick={overflowOpen=false;copyName=fileLabel.substringBeforeLast('.');saveCopyDialog=true});if(overlayCount>0)DropdownMenuItem(text = { Text(stringResource(R.string.clear_measurements)) }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { overflowOpen = false;confirmDeleteAll=true }); DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, leadingIcon = { ToolImage(R.drawable.ic_settings,28.dp) }, onClick = { overflowOpen = false; settingsOpen = true }); DropdownMenuItem(text = { Text(stringResource(R.string.about)) }, leadingIcon = { ToolImage(R.drawable.ic_about,28.dp) }, onClick = { overflowOpen = false; dialog = "about" }) } } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)))
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AndroidView(factory = { context -> DrawingView(context).also { drawingView = it } }, update = { view ->
                    view.onRenderModelReady = { ms -> runOnUiThread { renderReady=true;DwgOpenDiagnostics.stage("08_RENDER_MODEL_END","buildMs=$ms") } }
                    view.onFirstRender = { ms -> DwgOpenDiagnostics.stage("10_FIRST_RENDER_END","renderMs=$ms");currentDrawingFile?.let{file->view.post{ensureThumbnail(view,Uri.fromFile(file).toString())}};if(intent.getBooleanExtra("regression_stress",false)&&!regressionStressStarted){regressionStressStarted=true;view.startPerformanceStress()} }
                    view.onOverlayStateChanged = { count,selection -> runOnUiThread { overlayCount=count;selectedOverlay=selection } }
                    view.onObjectSelectionChanged = { selection -> runOnUiThread { selectedObject=selection } }
                    if (view.model !== drawing) view.model = drawing
                    view.setMeasurementDecimalPlaces(measurementDecimalPlaces)
                    view.setMeasurementTextSize(appSettings.measurementTextSizeSp)
                    view.setUseLayerColors(appSettings.useLayerColors)
                    val bg = when(appSettings.drawingBackground){AppSettings.BACKGROUND_BLACK->AndroidColor.BLACK;AppSettings.BACKGROUND_WHITE->AndroidColor.WHITE;AppSettings.BACKGROUND_NAVY->AndroidColor.rgb(9,28,43);else->if (dark) AndroidColor.rgb(8,13,17) else AndroidColor.rgb(244,247,249)}
                    val fg = when(appSettings.drawingBackground){AppSettings.BACKGROUND_BLACK,AppSettings.BACKGROUND_NAVY->AndroidColor.rgb(224,231,235);AppSettings.BACKGROUND_WHITE->AndroidColor.rgb(20,25,31);else->if(dark)AndroidColor.rgb(224,231,235)else AndroidColor.rgb(20,25,31)}
                    view.setPalette(bg, fg, AndroidColor.rgb(255, 48, 72))
                    view.onCanvasTap = { if (menuOpen) menuOpen = false }
                    view.onFirstMeasurePoint = { runOnUiThread { measurementPrompt = getString(R.string.measurement_second); measuredDistance=null } }
                    view.onMeasurement = { distance, unit, snapped -> runOnUiThread { measuredDistance=distance;measuredUnit=unit;measurementSnapped=snapped } }
                    view.onCoordinateSelected = { point, z, _, snapped -> runOnUiThread { coordinatePoint=point;coordinateZ=z;coordinateSnapped=snapped } }
                    view.onOffsetBaseSelected = { point, _, _, _, suggestedDistance -> runOnUiThread { offsetBase=point;offsetDistance=formatEditableNumber(suggestedDistance) } }
                    if(offsetMode)view.updateOffsetPreview(offsetDistance.replace(',','.').toDoubleOrNull(),offsetAngle.replace(',','.').toDoubleOrNull())
                    view.onAreaChanged = { area, unit, points -> runOnUiThread { measuredArea=area;measuredUnit=unit;areaPoints=points;measurementPrompt=getString(R.string.area_points_needed,points) } }
                }, modifier = Modifier.fillMaxSize().semantics { contentDescription = getString(R.string.drawing_ready) })

                if(renderReady)Column(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(visible = menuOpen && !measuring, enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 }, exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 }) {
                        if(landscape)Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
                            MenuFab(R.drawable.ic_fit_drawing, stringResource(R.string.fit_drawing),false) { drawingView?.fitToDrawing(); menuOpen = false }
                            MenuFab(R.drawable.ic_measure_distance, stringResource(R.string.measure),false) { drawingView?.startMeasurement(); measuring=true;areaMode=false;coordinateMode=false;offsetMode=false;measuredDistance=null;measuredArea=null;measurementPrompt=getString(R.string.measurement_first);menuOpen=false }
                            MenuFab(R.drawable.ic_measure_area, stringResource(R.string.area),false) { drawingView?.startArea();measuring=true;areaMode=true;coordinateMode=false;offsetMode=false;measuredDistance=null;measuredArea=null;areaPoints=0;measurementPrompt=getString(R.string.area_points_needed,0);menuOpen=false }
                            MenuFab(R.drawable.ic_coordinates, stringResource(R.string.coordinates),false) { drawingView?.startCoordinates();measuring=true;areaMode=false;coordinateMode=true;offsetMode=false;coordinatePoint=null;coordinateZ=null;menuOpen=false }
                            MenuFab(R.drawable.ic_offset_point, stringResource(R.string.offset_point),false) { drawingView?.startOffsetPoint();measuring=true;areaMode=false;coordinateMode=false;offsetMode=true;offsetBase=null;menuOpen=false }
                            MenuFab(R.drawable.ic_select_object, stringResource(R.string.select_object),false) { drawingView?.startObjectSelection();selectingObject=true;selectedObject=null;menuOpen=false }
                            MenuFab(R.drawable.ic_text_visibility, stringResource(if(textHidden)R.string.show_text else R.string.hide_text),false) { textHidden=!textHidden;drawingView?.setTextVisible(!textHidden);menuOpen=false }
                            MenuFab(R.drawable.ic_layers, stringResource(R.string.layers),false) { layersOpen = true; menuOpen = false }
                            MenuFab(R.drawable.ic_open_drawing, stringResource(R.string.open_drawing),false) { menuOpen = false; openDocument() }
                        }else Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
                            MenuFab(R.drawable.ic_fit_drawing, stringResource(R.string.fit_drawing)) { drawingView?.fitToDrawing(); menuOpen = false }
                            MenuFab(R.drawable.ic_measure_distance, stringResource(R.string.measure)) { drawingView?.startMeasurement(); measuring=true;areaMode=false;coordinateMode=false;offsetMode=false;measuredDistance=null;measuredArea=null;measurementPrompt=getString(R.string.measurement_first);menuOpen=false }
                            MenuFab(R.drawable.ic_measure_area, stringResource(R.string.area)) { drawingView?.startArea();measuring=true;areaMode=true;coordinateMode=false;offsetMode=false;measuredDistance=null;measuredArea=null;areaPoints=0;measurementPrompt=getString(R.string.area_points_needed,0);menuOpen=false }
                            MenuFab(R.drawable.ic_coordinates, stringResource(R.string.coordinates)) { drawingView?.startCoordinates();measuring=true;areaMode=false;coordinateMode=true;offsetMode=false;coordinatePoint=null;coordinateZ=null;menuOpen=false }
                            MenuFab(R.drawable.ic_offset_point, stringResource(R.string.offset_point)) { drawingView?.startOffsetPoint();measuring=true;areaMode=false;coordinateMode=false;offsetMode=true;offsetBase=null;menuOpen=false }
                            MenuFab(R.drawable.ic_select_object, stringResource(R.string.select_object)) { drawingView?.startObjectSelection();selectingObject=true;selectedObject=null;menuOpen=false }
                            MenuFab(R.drawable.ic_text_visibility, stringResource(if(textHidden)R.string.show_text else R.string.hide_text)) { textHidden=!textHidden;drawingView?.setTextVisible(!textHidden);menuOpen=false }
                            MenuFab(R.drawable.ic_layers, stringResource(R.string.layers)) { layersOpen = true; menuOpen = false }
                            MenuFab(R.drawable.ic_open_drawing, stringResource(R.string.open_drawing)) { menuOpen = false; openDocument() }
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                    if (!measuring) FloatingActionButton(onClick = { menuOpen = !menuOpen }, shape = CircleShape, containerColor=MaterialTheme.colorScheme.surfaceContainerHigh,modifier = Modifier.size(56.dp).semantics { contentDescription = getString(R.string.main_menu) }) { Image(painterResource(R.drawable.ic_bragincad_brand), null, Modifier.size(52.dp).clip(CircleShape)) }
                }

                if(!renderReady)Surface(Modifier.align(Alignment.Center),shape=RoundedCornerShape(BraginRadii.Large),color=MaterialTheme.colorScheme.surfaceContainerHigh,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),tonalElevation=3.dp){Row(Modifier.padding(horizontal=BraginSpacing.Large,vertical=BraginSpacing.Medium),verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp);Spacer(Modifier.width(BraginSpacing.Medium));Text(stringResource(R.string.loading_drawing))}}

                if(measuring&&offsetMode)OffsetPointPanel(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),offsetBase,offsetDistance,offsetAngle,onDistance={offsetDistance=it},onAngle={offsetAngle=it},onCancel={drawingView?.cancelMeasurement();measuring=false;offsetMode=false},onCreate={val distance=offsetDistance.replace(',','.').toDoubleOrNull();val angle=offsetAngle.replace(',','.').toDoubleOrNull();if(distance!=null&&angle!=null&&drawingView?.createOffsetPoint(distance,angle)!=null){measuring=false;offsetMode=false}})
                else if(measuring&&coordinateMode)CoordinatePanel(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),coordinatePoint,coordinateZ,coordinateSnapped,drawing.units,measurementDecimalPlaces,onCopy={point->val text="X=${formatNumber(point.x,measurementDecimalPlaces,true)}; Y=${formatNumber(point.y,measurementDecimalPlaces,true)}"+(coordinateZ?.let{"; Z=${formatNumber(it,measurementDecimalPlaces,true)}"}?:"");(getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText(getString(R.string.coordinates),text))},onClose={drawingView?.cancelMeasurement();measuring=false;coordinateMode=false})
                else if (measuring) MeasurementPanel(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp), measurement, areaMode, onCancel = { drawingView?.cancelMeasurement();measuring=false;areaMode=false;measuredDistance=null;measuredArea=null }, onUndo={drawingView?.undoAreaPoint()}, onDone = { if(areaMode)drawingView?.finishArea() else drawingView?.finishMeasurement();measuring=false;areaMode=false })
                else if(selectingObject)ObjectPropertiesPanel(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),selectedObject,drawing.units,onClose={drawingView?.stopObjectSelection();selectingObject=false;selectedObject=null},onColor={drawingView?.setSelectedObjectColor(it)})
                else selectedOverlay?.let{selection->SelectedOverlayPanel(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start=12.dp,end=84.dp,bottom=12.dp),selection,onTogglePrimary={drawingView?.toggleSelectedPrimary()},onToggleSecondary={drawingView?.toggleSelectedSecondary()}){drawingView?.deleteSelectedOverlay()}}
            }
        }
        var colorLayer by remember(drawing){mutableStateOf<Layer?>(null)}
        if (layersOpen) ModalBottomSheet(onDismissRequest = { layersOpen = false }) { Text(stringResource(R.string.layers), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)); LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding()) { items(drawing.layers, key = { it.id }) { layer -> var checked by remember(layer.id) { mutableStateOf(drawingView?.isLayerVisible(layer.id) ?: layer.initiallyVisible) };var shownColor by remember(layer.id){mutableIntStateOf(drawingView?.layerDisplayColor(layer.id)?:layer.dwgColor)}; ListItem(headlineContent = { Text(layer.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },leadingContent={Surface(Modifier.size(30.dp).clickable{colorLayer=layer},shape=CircleShape,color=Color(0xff000000 or shownColor.toLong()),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){Box(Modifier.fillMaxSize())}}, trailingContent = { Switch(checked = checked, onCheckedChange = { checked = it; drawingView?.setLayerVisible(layer.id, it) }, modifier = Modifier.semantics { contentDescription = getString(if (checked) R.string.layer_visible else R.string.layer_hidden, layer.name) }) }, modifier = Modifier.clickable { checked = !checked; drawingView?.setLayerVisible(layer.id, checked) });LaunchedEffect(colorLayer){shownColor=drawingView?.layerDisplayColor(layer.id)?:shownColor} } } }
        colorLayer?.let{layer->LayerColorDialog(layer,{colorLayer=null}){color->drawingView?.setLayerColor(layer.id,color);colorLayer=null}}
        dialog?.let { kind -> if(kind=="about") AboutDialog { dialog=null } else InfoDialog(kind, drawing) { dialog = null } }
        if (settingsOpen) SettingsDialog(onDismiss = { settingsOpen = false },onAbout={settingsOpen=false;dialog="about"})
        if(confirmDeleteAll)AlertDialog(onDismissRequest={confirmDeleteAll=false},title={Text(stringResource(R.string.clear_measurements))},text={Text(stringResource(R.string.clear_measurements_confirmation))},dismissButton={TextButton(onClick={confirmDeleteAll=false}){Text(stringResource(R.string.cancel))}},confirmButton={TextButton(onClick={drawingView?.clearMeasurementOverlays();confirmDeleteAll=false}){Text(stringResource(R.string.delete_all))}})
        if(saveCopyDialog){
            val normalized=normalizedSavedName(copyName)
            val duplicate=savedDrawings().any{it.label.equals(normalized,true)}
            AlertDialog(onDismissRequest={saveCopyDialog=false},title={Text(stringResource(R.string.save_copy_title))},text={OutlinedTextField(value=copyName,onValueChange={copyName=it},singleLine=true,label={Text(stringResource(R.string.copy_file_name))},suffix={Text(".dwg")},isError=duplicate,supportingText=if(duplicate){{Text(stringResource(R.string.copy_name_exists))}}else null)},dismissButton={TextButton(onClick={saveCopyDialog=false}){Text(stringResource(R.string.cancel))}},confirmButton={TextButton(enabled=copyName.trim().removeSuffix(".dwg").isNotBlank()&&!duplicate,onClick={saveCurrentCopy(copyName);saveCopyDialog=false}){Text(stringResource(R.string.save))}})
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun MenuFab(icon:Int,label:String,showLabel:Boolean=true,action:()->Unit){val state=rememberTooltipState();TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),tooltip={PlainTooltip{Text(label)}},state=state){Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.semantics{contentDescription=label}.clickable(onClick=action)){if(showLabel){Surface(shape=RoundedCornerShape(7.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha=.92f),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Text(label,Modifier.padding(horizontal=7.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall,maxLines=1)};Spacer(Modifier.width(6.dp))};Surface(shape=RoundedCornerShape(BraginRadii.Large),color=MaterialTheme.colorScheme.surfaceContainerHigh,tonalElevation=3.dp,shadowElevation=3.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.size(50.dp)){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){ToolImage(icon,32.dp)}}}}}

    @Composable private fun MeasurementPanel(modifier:Modifier,value:String,area:Boolean,onCancel:()->Unit,onUndo:()->Unit,onDone:()->Unit){Surface(modifier,shape=RoundedCornerShape(BraginRadii.Floating),tonalElevation=4.dp,shadowElevation=4.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),color=MaterialTheme.colorScheme.surfaceContainerHigh){Row(Modifier.padding(BraginSpacing.Small),verticalAlignment=Alignment.CenterVertically){ToolImage(if(area)R.drawable.ic_measure_area else R.drawable.ic_measure_distance,26.dp);Text(value,Modifier.padding(horizontal=BraginSpacing.Medium).weight(1f,fill=false),maxLines=2);if(area)IconButton(onClick=onUndo){Icon(Icons.Default.ArrowBack,stringResource(R.string.undo_point))};IconButton(onClick=onCancel){Icon(Icons.Default.Close,stringResource(R.string.cancel))};FilledIconButton(onClick=onDone){Icon(Icons.Default.Check,stringResource(R.string.done))}}}}

    @Composable private fun SelectedOverlayPanel(modifier:Modifier,selection:OverlaySelection,onTogglePrimary:()->Unit,onToggleSecondary:()->Unit,onDelete:()->Unit){Surface(modifier,shape=RoundedCornerShape(BraginRadii.Floating),tonalElevation=4.dp,shadowElevation=4.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),color=MaterialTheme.colorScheme.surfaceContainerHigh){Row(Modifier.padding(start=BraginSpacing.Medium,end=BraginSpacing.Small,top=BraginSpacing.Small,bottom=BraginSpacing.Small),verticalAlignment=Alignment.CenterVertically){Row(Modifier.weight(1f).padding(end=BraginSpacing.Small),verticalAlignment=Alignment.CenterVertically){Text(selection.primary,Modifier.clickable(onClick=onTogglePrimary).padding(4.dp),textDecoration=if(selection.primaryVisible)null else TextDecoration.LineThrough);selection.secondary?.let{Text(" · ");Text(it,Modifier.clickable(onClick=onToggleSecondary).padding(4.dp),textDecoration=if(selection.secondaryVisible)null else TextDecoration.LineThrough)}};FilledIconButton(onClick=onDelete){Icon(Icons.Default.Delete,stringResource(R.string.delete_measurement))}}}}

    @Composable private fun ObjectPropertiesPanel(modifier:Modifier,selection:ObjectSelection?,unit:DrawingUnit,onClose:()->Unit,onColor:(Int?)->Unit){
        val accent=MaterialTheme.colorScheme.primary
        PixelFrame(PixelFrameStyle.Panel.copy(backgroundColor=MaterialTheme.colorScheme.surfaceContainerHigh,borderColor=MaterialTheme.colorScheme.outlineVariant,cornerAccentColor=accent,contentPadding=PaddingValues(14.dp)),modifier.fillMaxWidth().widthIn(max=520.dp)){
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.object_properties),Modifier.weight(1f),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);IconButton(onClick=onClose,Modifier.size(32.dp)){Icon(Icons.Default.Close,stringResource(R.string.close))}}
                if(selection==null){Text(stringResource(R.string.select_object_hint),color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=18.dp))}else{
                    Row(verticalAlignment=Alignment.CenterVertically){ToolImage(R.drawable.ic_select_object,44.dp);Spacer(Modifier.width(10.dp));Column{Text(selection.type,fontWeight=FontWeight.SemiBold);Text(stringResource(R.string.selected_objects_count),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    PropertyRow(stringResource(R.string.object_layer),selection.layerName)
                    selection.length?.let{PropertyRow(stringResource(R.string.object_length),formatDistance(it,unit,false,settingsRepository.settings.value.measurementDecimalPlaces).substringBefore("  ·"))}
                    PropertyRow(stringResource(R.string.object_type),stringResource(R.string.continuous))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.object_color),Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant);Surface(Modifier.size(28.dp).clickable{onColor(null)},shape=CircleShape,color=MaterialTheme.colorScheme.surface,border=BorderStroke(2.dp,if(selection.color==null)accent else MaterialTheme.colorScheme.outline)){Box(Modifier.fillMaxSize())};listOf(0xFFFF3048.toInt(),0xFF2196F3.toInt(),0xFF4CAF50.toInt(),0xFFFFC107.toInt(),0xFFFFFFFF.toInt()).forEach{color->Spacer(Modifier.width(7.dp));Surface(Modifier.size(28.dp).clickable{onColor(color)},shape=CircleShape,color=Color(color),border=BorderStroke(if(selection.color==color)3.dp else 1.dp,if(selection.color==color)accent else MaterialTheme.colorScheme.outline)){Box(Modifier.fillMaxSize())}}}
                }
            }
        }
    }
    @Composable private fun PropertyRow(label:String,value:String){Row(Modifier.fillMaxWidth()){Text(label,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)}}

    @Composable private fun CoordinatePanel(modifier:Modifier,point:Point2?,z:Double?,snapped:Boolean,unit:DrawingUnit,decimalPlaces:Int,onCopy:(Point2)->Unit,onClose:()->Unit){Surface(modifier,shape=RoundedCornerShape(BraginRadii.Floating),tonalElevation=4.dp,shadowElevation=4.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),color=MaterialTheme.colorScheme.surfaceContainerHigh){Row(Modifier.padding(BraginSpacing.Medium),verticalAlignment=Alignment.CenterVertically){ToolImage(R.drawable.ic_coordinates,26.dp);Column(Modifier.padding(horizontal=BraginSpacing.Medium).weight(1f,false)){Text(if(point==null)stringResource(R.string.coordinate_tap) else "X: ${formatNumber(point.x,decimalPlaces,true)}\nY: ${formatNumber(point.y,decimalPlaces,true)}"+(z?.let{"\nZ: ${formatNumber(it,decimalPlaces,true)}"}?:""),maxLines=3);if(point!=null)Text((if(snapped)stringResource(R.string.endpoint_snapped)+" · " else "")+stringResource(R.string.coordinate_units,localizedUnitName(unit)),style=MaterialTheme.typography.labelSmall)};if(point!=null)TextButton(onClick={onCopy(point)}){Text(stringResource(R.string.copy))};IconButton(onClick=onClose){Icon(Icons.Default.Close,stringResource(R.string.close))}}}}

    @Composable private fun OffsetPointPanel(modifier:Modifier,base:Point2?,distance:String,angle:String,onDistance:(String)->Unit,onAngle:(String)->Unit,onCancel:()->Unit,onCreate:()->Unit){Surface(modifier,shape=RoundedCornerShape(BraginRadii.Floating),tonalElevation=4.dp,shadowElevation=4.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),color=MaterialTheme.colorScheme.surfaceContainerHigh){Column(Modifier.padding(BraginSpacing.Medium).widthIn(max=480.dp)){Text(if(base==null)stringResource(R.string.offset_select_base)else stringResource(R.string.offset_base_selected),style=MaterialTheme.typography.titleSmall);if(base!=null){Text(stringResource(R.string.offset_preview_hint),style=MaterialTheme.typography.labelSmall,color=Color(0xFFFFAA00));Row(Modifier.padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(distance,onDistance,Modifier.weight(1f),label={Text(stringResource(R.string.offset_distance))},singleLine=true);OutlinedTextField(angle,onAngle,Modifier.weight(1f),label={Text(stringResource(R.string.offset_angle))},singleLine=true)}};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick=onCancel){Text(stringResource(R.string.cancel))};if(base!=null)Button(onClick=onCreate){Text(stringResource(R.string.done))}}}}}

    @OptIn(ExperimentalLayoutApi::class)
    @Composable private fun LayerColorDialog(layer:Layer,onDismiss:()->Unit,onSelect:(Int?)->Unit){val colors=listOf(0xFFFFFF,0xFF4040,0xFFB000,0xFFFF40,0x40FF40,0x40FFFF,0x4080FF,0xC060FF);AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.layer_color,layer.name))},text={Column{TextButton(onClick={onSelect(null)}){Text(stringResource(R.string.original_dwg_color))};FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp),maxItemsInEachRow=4){colors.forEach{rgb->Surface(Modifier.size(38.dp).clickable{onSelect(rgb)},shape=CircleShape,color=Color(0xff000000 or rgb.toLong()),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline)){Box(Modifier.fillMaxSize())}}}}},confirmButton={TextButton(onClick=onDismiss){Text(stringResource(R.string.cancel))}})}

    @Composable private fun InfoDialog(kind: String, drawing: DrawingModel, dismiss: () -> Unit) { val title: String; val body: String; when (kind) { "stats" -> { title = stringResource(R.string.statistics); val s = drawing.stats; body = stringResource(R.string.stats_body, drawing.version, s.totalObjects, s.graphicalEntities, s.supportedEntities, formatNumber(100.0 * (s.visibleGraphicalEntities - s.unsupportedVisibleGraphicalEntities) / s.visibleGraphicalEntities, 2), s.renderPrimitiveCount) }; else -> { title = stringResource(R.string.drawing_information); body = stringResource(R.string.info_body, fileLabel, drawing.version, localizedUnitName(drawing.units), drawing.layers.size, drawing.stats.totalObjects) } }; AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(body) }, confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) } }) }

    @Composable private fun AboutDialog(dismiss:()->Unit){
        val context=LocalContext.current
        val version=remember(context){runCatching{context.packageManager.getPackageInfo(context.packageName,0).versionName}.getOrNull().orEmpty().ifBlank{"—"}}
        var licenseOpen by remember{mutableStateOf(false)}
        AlertDialog(onDismissRequest=dismiss,title={Row(verticalAlignment=Alignment.CenterVertically){Image(painterResource(R.drawable.ic_bragincad_brand),null,Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)));Spacer(Modifier.width(BraginSpacing.Medium));Text(stringResource(R.string.app_name))}},text={Column(verticalArrangement=Arrangement.spacedBy(BraginSpacing.Medium)){Text(stringResource(R.string.app_version,version),style=MaterialTheme.typography.bodyMedium);Column{Text(stringResource(R.string.developer_label),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(stringResource(R.string.developer_name),style=MaterialTheme.typography.bodyLarge)};Column{Text(stringResource(R.string.purpose_label),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(stringResource(R.string.app_purpose))};HorizontalDivider();Text(stringResource(R.string.offline_note),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick={licenseOpen=true},contentPadding=PaddingValues(0.dp)){Text(stringResource(R.string.licenses))}}},confirmButton={TextButton(onClick=dismiss){Text(stringResource(R.string.close))}})
        if(licenseOpen){val licenseText=remember{context.resources.openRawResource(R.raw.libredwg_gpl_v3).bufferedReader().use{it.readText()}};AlertDialog(onDismissRequest={licenseOpen=false},title={Text(stringResource(R.string.libredwg_license_title))},text={LazyColumn(Modifier.heightIn(max=460.dp)){item{Text(stringResource(R.string.software_used),style=MaterialTheme.typography.titleSmall);Text(stringResource(R.string.libredwg_license_summary),Modifier.padding(top=BraginSpacing.Small,bottom=BraginSpacing.Large));Text(licenseText,style=MaterialTheme.typography.bodySmall)}}},confirmButton={TextButton(onClick={licenseOpen=false}){Text(stringResource(R.string.close))}})}
    }

    @Composable private fun SettingsDialog(onDismiss: () -> Unit,onAbout:()->Unit) {
        val appSettings by settingsRepository.settings
        var confirmReset by remember{mutableStateOf(false)}
        var checkingUpdates by remember{mutableStateOf(false)}
        var updateInfo by remember{mutableStateOf<UpdateInfo?>(null)}
        val colors=MaterialTheme.colorScheme;val accent=colors.primary;val text=colors.onSurface;val secondary=colors.onSurfaceVariant;val border=colors.outlineVariant;val panel=colors.surfaceContainer;val card=colors.surface
        val precision=(0..4).map{it to if(it==0)stringResource(R.string.whole_number) else "$it ${stringResource(R.string.decimal_places_short)}"}
        val textSizes=listOf(12,15,18,21,24).map{it to "$it sp"}
        val backgrounds=listOf(
            AppSettings.BACKGROUND_THEME to stringResource(R.string.background_theme),
            AppSettings.BACKGROUND_BLACK to stringResource(R.string.background_black),
            AppSettings.BACKGROUND_WHITE to stringResource(R.string.background_white),
            AppSettings.BACKGROUND_NAVY to stringResource(R.string.background_navy),
        )
        val themes=listOf(
            AppSettings.THEME_SYSTEM to stringResource(R.string.theme_system),
            AppSettings.THEME_LIGHT to stringResource(R.string.theme_light),
            AppSettings.THEME_DARK to stringResource(R.string.theme_dark),
        )
        Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)){
            PixelFrame(
                PixelFrameStyle.Panel.copy(cornerSize=10.dp,backgroundColor=panel,borderColor=border,cornerAccentColor=accent,cornerAccentPlacement=PixelFrameAccentPlacement.ALL,cornerAccentLength=12.dp,contentPadding=PaddingValues(0.dp)),
                Modifier.fillMaxWidth().padding(horizontal=22.dp).widthIn(max=560.dp).heightIn(max=760.dp)
            ){
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=18.dp,vertical=20.dp)){
                    Text(stringResource(R.string.settings),color=text,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.headlineMedium)
                    SettingsSectionHeader(stringResource(R.string.measurements_settings),accent,text)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        SettingsChoiceCard(stringResource(R.string.measurement_precision),appSettings.measurementDecimalPlaces,precision,Modifier.weight(1f),accent,text,secondary){settingsRepository.setMeasurementDecimalPlaces(it)}
                        SettingsChoiceCard(stringResource(R.string.measurement_display_size),appSettings.measurementTextSizeSp,textSizes,Modifier.weight(1f),accent,text,secondary){settingsRepository.setMeasurementTextSize(it)}
                    }
                    SettingsSectionHeader(stringResource(R.string.appearance_settings),accent,text)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        SettingsChoiceCard(stringResource(R.string.drawing_background),appSettings.drawingBackground,backgrounds,Modifier.weight(1f),accent,text,secondary){settingsRepository.setDrawingBackground(it)}
                        SettingsChoiceCard(stringResource(R.string.theme),appSettings.theme,themes,Modifier.weight(1f),accent,text,secondary){settingsRepository.setTheme(it)}
                    }
                    Spacer(Modifier.height(12.dp))
                    PixelFrame(PixelFrameStyle.Card.copy(backgroundColor=card,borderColor=border,cornerAccentPlacement=PixelFrameAccentPlacement.TOP_LEFT,cornerAccentColor=accent,cornerAccentWidth=2.dp,cornerAccentLength=13.dp,contentPadding=PaddingValues(horizontal=14.dp)),Modifier.fillMaxWidth().height(78.dp),onClick={settingsRepository.setUseLayerColors(!appSettings.useLayerColors)}){
                        Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(stringResource(R.string.use_layer_colors),color=text,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyLarge);Text(stringResource(R.string.original_drawing_colors),color=secondary,style=MaterialTheme.typography.bodySmall)};Switch(appSettings.useLayerColors,{settingsRepository.setUseLayerColors(it)},colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=accent))}
                    }
                    HorizontalDivider(Modifier.padding(vertical=16.dp),color=border)
                    SettingsActionRow(null,stringResource(R.string.reset_settings),accent,text,imageResource=R.drawable.ic_reset){confirmReset=true}
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        SettingsActionTile(stringResource(R.string.about),R.drawable.ic_about,accent,text,Modifier.weight(1f),onAbout)
                        SettingsActionTile(stringResource(R.string.check_updates),R.drawable.ic_check_updates,accent,text,Modifier.weight(1f)){
                            if(!checkingUpdates){checkingUpdates=true;checkForUpdates{result->checkingUpdates=false;updateInfo=result}}
                        }
                    }
                    HorizontalDivider(Modifier.padding(top=16.dp,bottom=8.dp),color=border)
                    TextButton(onClick=onDismiss,Modifier.align(Alignment.End)){Text(stringResource(R.string.done),color=accent,fontWeight=FontWeight.Bold)}
                }
            }
        }
        if(confirmReset)AlertDialog(onDismissRequest={confirmReset=false},title={Text(stringResource(R.string.reset_settings))},text={Text(stringResource(R.string.reset_settings_confirmation))},dismissButton={TextButton(onClick={confirmReset=false}){Text(stringResource(R.string.cancel))}},confirmButton={TextButton(onClick={settingsRepository.reset();confirmReset=false}){Text(stringResource(R.string.reset_settings))}})
        if(checkingUpdates)AlertDialog(onDismissRequest={},title={Text(stringResource(R.string.checking_updates))},text={Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator()}},confirmButton={})
        updateInfo?.let{info->
            val title=when{info.error->stringResource(R.string.update_check_failed);info.available->stringResource(R.string.update_available);else->stringResource(R.string.update_not_available)}
            val body=when{info.error->stringResource(R.string.update_check_failed_body);info.available->stringResource(R.string.update_available_body,info.current,info.latest);else->stringResource(R.string.update_not_available_body,info.current)}
            AlertDialog(onDismissRequest={updateInfo=null},title={Text(title)},text={Text(body)},dismissButton={if(info.available)TextButton(onClick={updateInfo=null}){Text(stringResource(R.string.close))}},confirmButton={if(info.available)TextButton(onClick={val target=info.downloadUrl?:info.releaseUrl;startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(target)));updateInfo=null}){Text(stringResource(R.string.open_release))}else TextButton(onClick={updateInfo=null}){Text(stringResource(R.string.close))}})
        }
    }

    @Composable private fun SettingsSectionHeader(title:String,accent:Color,text:Color){Row(Modifier.padding(top=20.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.width(4.dp).height(26.dp).background(accent));Spacer(Modifier.width(10.dp));Text(title,color=text,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)}}

    @Composable private fun SettingsChoiceCard(label:String,value:Int,options:List<Pair<Int,String>>,modifier:Modifier,accent:Color,text:Color,secondary:Color,onSelect:(Int)->Unit){
        var expanded by remember{mutableStateOf(false)}
        BoxWithConstraints(modifier){
            PixelFrame(PixelFrameStyle.Card.copy(backgroundColor=MaterialTheme.colorScheme.surface,borderColor=MaterialTheme.colorScheme.outlineVariant,cornerAccentPlacement=PixelFrameAccentPlacement.TOP_LEFT,cornerAccentColor=accent,cornerAccentWidth=2.dp,cornerAccentLength=12.dp,contentPadding=PaddingValues(horizontal=14.dp,vertical=11.dp)),Modifier.fillMaxWidth().height(102.dp),onClick={expanded=true}){
                Column(Modifier.fillMaxSize()){Text(label,color=secondary,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis);Spacer(Modifier.weight(1f));Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(options.firstOrNull{it.first==value}?.second?:value.toString(),Modifier.weight(1f),color=text,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Icon(Icons.Default.KeyboardArrowDown,null,tint=accent)}}
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.width(maxWidth)){
                options.forEach{option->
                    val selected=option.first==value
                    DropdownMenuItem(
                        text={Text(option.second,color=if(selected)accent else MaterialTheme.colorScheme.onSurface,fontWeight=if(selected)FontWeight.Bold else FontWeight.Normal)},
                        onClick={onSelect(option.first);expanded=false},
                        modifier=Modifier.background(if(selected)accent.copy(alpha=.16f) else Color.Transparent)
                    )
                }
            }
        }
    }

    @Composable private fun SettingsActionRow(icon:ImageVector?,label:String,accent:Color,text:Color,imageResource:Int?=null,onClick:()->Unit){PixelFrame(PixelFrameStyle.Card.copy(backgroundColor=MaterialTheme.colorScheme.surface,borderColor=MaterialTheme.colorScheme.outlineVariant,cornerAccentPlacement=PixelFrameAccentPlacement.TOP_LEFT,cornerAccentColor=accent,cornerAccentWidth=2.dp,cornerAccentLength=12.dp,contentPadding=PaddingValues(horizontal=14.dp)),Modifier.fillMaxWidth().height(72.dp),onClick){Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){if(imageResource!=null)ToolImage(imageResource,36.dp)else if(icon!=null)Icon(icon,null,Modifier.size(36.dp),tint=text);Spacer(Modifier.width(14.dp));Text(label,Modifier.weight(1f),color=text,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium);Icon(Icons.Default.KeyboardArrowRight,null,tint=accent,modifier=Modifier.size(30.dp))}}}

    @Composable private fun SettingsActionTile(label:String,imageResource:Int,accent:Color,text:Color,modifier:Modifier,onClick:()->Unit){PixelFrame(PixelFrameStyle.Card.copy(backgroundColor=MaterialTheme.colorScheme.surface,borderColor=MaterialTheme.colorScheme.outlineVariant,cornerAccentPlacement=PixelFrameAccentPlacement.TOP_LEFT,cornerAccentColor=accent,cornerAccentWidth=2.dp,cornerAccentLength=12.dp,contentPadding=PaddingValues(horizontal=8.dp)),modifier.height(88.dp),onClick){Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically){ToolImage(imageResource,27.dp);Spacer(Modifier.width(6.dp));Text(label,Modifier.weight(1f),color=text,fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodySmall,maxLines=3);Icon(Icons.Default.KeyboardArrowRight,null,tint=accent,modifier=Modifier.size(18.dp))}}}

    private fun checkForUpdates(done:(UpdateInfo)->Unit){
        val current=runCatching{packageManager.getPackageInfo(packageName,0).versionName}.getOrNull().orEmpty().ifBlank{"1.0"}
        openExecutor.execute{
            val result=runCatching{
                val connection=(URL("https://api.github.com/repos/Danillchen/BraginCAD/releases?per_page=10").openConnection() as HttpURLConnection).apply{connectTimeout=10_000;readTimeout=12_000;requestMethod="GET";setRequestProperty("Accept","application/vnd.github+json");setRequestProperty("X-GitHub-Api-Version","2022-11-28");setRequestProperty("User-Agent","BraginCAD-Android/$current")}
                try{if(connection.responseCode !in 200..299)throw IOException("GitHub HTTP ${connection.responseCode}");val releases=JSONArray(connection.inputStream.bufferedReader().use{it.readText()});val json=(0 until releases.length()).map{releases.getJSONObject(it)}.firstOrNull{!it.optBoolean("draft",false)}?:throw IOException("GitHub has no published releases");val latest=json.getString("tag_name").removePrefix("v");val assets=json.optJSONArray("assets");var apk:String?=null;if(assets!=null)for(i in 0 until assets.length()){val asset=assets.getJSONObject(i);if(asset.optString("name").endsWith(".apk",true)){apk=asset.optString("browser_download_url").takeIf{it.isNotBlank()};break}};UpdateInfo(current,latest,json.getString("html_url"),apk,isVersionNewer(latest,current))}finally{connection.disconnect()}
            }.getOrElse{error->Log.w("BraginCAD","Update check failed",error);UpdateInfo(current,"","https://github.com/Danillchen/BraginCAD/releases",null,false,true)}
            runOnUiThread{done(result)}
        }
    }

    private fun isVersionNewer(candidate:String,current:String):Boolean{val a=candidate.removePrefix("v").split(Regex("[^0-9]+")).filter{it.isNotBlank()}.mapNotNull{it.toIntOrNull()};val b=current.removePrefix("v").split(Regex("[^0-9]+")).filter{it.isNotBlank()}.mapNotNull{it.toIntOrNull()};for(i in 0 until maxOf(a.size,b.size)){val av=a.getOrElse(i){0};val bv=b.getOrElse(i){0};if(av!=bv)return av>bv};return false}

    private fun openDocument() { val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/acad", "application/x-acad", "application/dwg", "image/vnd.dwg", "application/octet-stream")) }; startActivityForResult(intent, OPEN_DWG) }
    private fun requestProjectDocument(projectName:String){pendingProjectName=projectName;val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/acad","application/x-acad","application/dwg","image/vnd.dwg","application/octet-stream"))};startActivityForResult(intent,ADD_PROJECT_DWG)}
    @Deprecated("Activity result retained to keep the milestone dependency-free") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=Activity.RESULT_OK)return;val uri=data?.data?:return;when(requestCode){OPEN_DWG->openDrawing(uri,displayName(uri)?:"drawing.dwg",true);ADD_PROJECT_DWG->{val project=pendingProjectName?:return;pendingProjectName=null;runCatching{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)};val label=displayName(uri)?:"drawing.dwg";openExecutor.execute{runCatching{val stable=materializeRecent(uri,label).first;addProjectFile(project,Recent(Uri.fromFile(stable).toString(),label))}.onFailure{Log.e("BraginCAD","Cannot add project file",it)}}}}}
    private fun handleViewIntent(source: Intent) { if (source.action != Intent.ACTION_VIEW) return; val uri=source.data ?: return; openDrawing(uri, displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "drawing.dwg", true) }
    private fun openDrawing(uri: Uri, label: String, persistPermission: Boolean) {
        val normalizedLabel=label.ifBlank { "drawing.dwg" };val mime=contentResolver.getType(uri)
        if (!normalizedLabel.endsWith(".dwg",true)&&mime !in DWG_MIME_TYPES) { Toast.makeText(this,R.string.invalid_file,Toast.LENGTH_LONG).show();return }
        if(persistPermission)runCatching{contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}.onFailure{Log.i("BraginCAD","URI permission is transient for $uri: ${it.message}")}
        val generation=openGeneration.incrementAndGet();loading=true;openExecutor.execute { if(generation!=openGeneration.get())return@execute;var stable:File?=null;var created=false;try{val materialized=materializeRecent(uri,normalizedLabel);stable=materialized.first;created=materialized.second;if(stable.length()<6)throw IOException("DWG stream is empty or truncated");DwgOpenDiagnostics.begin(cacheDir,stable,normalizedLabel);parse(stable,normalizedLabel,generation){addRecent(Uri.fromFile(stable),normalizedLabel)}}catch(t:Throwable){if(created)stable?.delete();showError(t,generation)} }
    }
    private fun loadPath(file: File, label: String) { val generation=openGeneration.incrementAndGet();loading = true; openExecutor.execute { if(generation!=openGeneration.get())return@execute;try { DwgOpenDiagnostics.begin(cacheDir,file,label);parse(file, label,generation) } catch (t: Throwable) { showError(t,generation) } } }
    private fun parse(file: File, label: String,generation:Int, opened:()->Unit={}) { DwgOpenDiagnostics.stage("02_NATIVE_READ_BEGIN");val drawing = LibreDwgAdapter().read(file.absolutePath);DwgOpenDiagnostics.stage("07_DRAWING_MODEL_END","objects=${drawing.stats.totalObjects} primitives=${drawing.stats.renderPrimitiveCount}"); val unsupported = drawing.stats.unsupportedByType.entries.sortedByDescending { it.value }.joinToString { "${it.key}=${it.value}" };val breakdown=drawing.stats.byType.entries.sortedByDescending{it.value}.joinToString{ "${it.key}=${it.value}" }; Log.i("BraginCAD", "file=$label version=${drawing.version} objects=${drawing.stats.totalObjects} graphical=${drawing.stats.graphicalEntities} visibleGraphical=${drawing.stats.visibleGraphicalEntities} service=${drawing.stats.serviceObjects} structural=${drawing.stats.structuralEntities} supported=${drawing.stats.supportedEntities} unsupportedGraphical=${drawing.stats.unsupportedGraphicalEntities} unsupportedVisible=${drawing.stats.unsupportedVisibleGraphicalEntities} coverage=${"%.2f".format(Locale.US, 100.0 * drawing.stats.supportedEntities / drawing.stats.graphicalEntities)}% visibleCoverage=${"%.2f".format(Locale.US, 100.0 * (drawing.stats.visibleGraphicalEntities - drawing.stats.unsupportedVisibleGraphicalEntities) / drawing.stats.visibleGraphicalEntities)}% primitives=${drawing.stats.renderPrimitiveCount} layers=${drawing.layers.size} units=${drawing.units} modelBuildMs=${drawing.parseMs} unsupportedRanking=[$unsupported] entityBreakdown=[$breakdown]"); runOnUiThread { if(generation!=openGeneration.get()){Log.i("BraginCAD","Discarding stale DWG result file=$label generation=$generation current=${openGeneration.get()}");return@runOnUiThread};opened();DwgOpenDiagnostics.stage("08_RENDER_MODEL_BEGIN");currentDrawingFile=file;model = drawing; fileLabel = label; loading = false } }
    private fun showError(t: Throwable,generation:Int) { DwgOpenDiagnostics.failure(t);Log.e("BraginCAD", "DWG open failed type=${t.javaClass.name}", t); runOnUiThread { if(generation==openGeneration.get()){loading = false; errorText = getString(R.string.unable_open_drawing)} } }

    override fun onDestroy(){openGeneration.incrementAndGet();openExecutor.shutdownNow();super.onDestroy()}
    private fun formatDistance(value:Double,unit:DrawingUnit,snapped:Boolean,decimalPlaces:Int):String{val factor=millimetersPerUnit(unit);if(factor==null)return "${formatNumber(value,decimalPlaces,true)} ${getString(R.string.drawing_units)}";val mm=value*factor;val suffix=if(snapped)" • ${getString(R.string.endpoint_snapped)}" else "";return "${formatNumber(mm,decimalPlaces)} мм  ·  ${formatNumber(mm/1000,decimalPlaces,true)} м$suffix"}
    private fun formatArea(value:Double,unit:DrawingUnit,decimalPlaces:Int):String{val factor=millimetersPerUnit(unit)?:return "${formatNumber(value,decimalPlaces,true)} ${getString(R.string.square_drawing_units)}";val mm2=value*factor*factor;return "${formatNumber(mm2,decimalPlaces)} мм²  ·  ${formatNumber(mm2/1_000_000,decimalPlaces,true)} м²"}
    private fun millimetersPerUnit(unit:DrawingUnit):Double?=when(unit){DrawingUnit.INCHES->25.4;DrawingUnit.FEET->304.8;DrawingUnit.MILES->1_609_344.0;DrawingUnit.MILLIMETERS->1.0;DrawingUnit.CENTIMETERS->10.0;DrawingUnit.METERS->1000.0;DrawingUnit.KILOMETERS->1_000_000.0;DrawingUnit.MICROINCHES->.0000254;DrawingUnit.MILS->.0254;DrawingUnit.YARDS->914.4;DrawingUnit.MICRONS->.001;DrawingUnit.DECIMETERS->100.0;DrawingUnit.DEKAMETERS->10_000.0;DrawingUnit.HECTOMETERS->100_000.0;else->null}
    private fun localizedUnitName(unit:DrawingUnit):String=when(unit){DrawingUnit.MILLIMETERS->getString(R.string.unit_millimeters);DrawingUnit.CENTIMETERS->getString(R.string.unit_centimeters);DrawingUnit.METERS->getString(R.string.unit_meters);DrawingUnit.UNITLESS,DrawingUnit.UNKNOWN->getString(R.string.unit_unspecified);else->unit.symbol}
    private fun formatNumber(value:Double,decimals:Int,fixed:Boolean=false):String=NumberFormat.getNumberInstance(Locale("ru","RU")).apply{maximumFractionDigits=decimals;minimumFractionDigits=if(fixed)decimals else 0;isGroupingUsed=true}.format(value)
    private fun formatEditableNumber(value:Double):String=String.format(Locale.US,"%.2f",value).trimEnd('0').trimEnd('.')
    private fun displayName(uri: Uri): String? { var cursor: Cursor? = null; return try { cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null); if (cursor?.moveToFirst() == true) cursor.getString(0) else null } finally { cursor?.close() } }
    private fun recent(): MutableList<Recent> = prefs.getString("recent", "").orEmpty().split('\u001e').filter { it.contains('\u001f') }.map { val p = it.split('\u001f', limit = 2); Recent(p[0], p[1]) }.toMutableList()
    private fun savedDrawings():MutableList<Recent> = prefs.getString("saved_drawings","").orEmpty().split('\u001e').filter{it.contains('\u001f')}.mapNotNull{record->val p=record.split('\u001f',limit=2);val uri=Uri.parse(p[0]);val file=uri.path?.let(::File);if(uri.scheme=="file"&&file?.isFile==true)Recent(p[0],p[1])else null}.toMutableList()
    private fun deleteSavedCopy(item:Recent){runCatching{val savedDir=File(filesDir,"saved-dwg").canonicalFile;val file=Uri.parse(item.uri).path?.let(::File)?.canonicalFile;if(file!=null&&file.parentFile==savedDir&&file.exists()&&!file.delete())throw IOException("Cannot delete saved drawing");thumbnailFile(item.uri).delete()}.onFailure{Log.e("BraginCAD","Cannot delete saved copy",it);Toast.makeText(this,R.string.copy_save_failed,Toast.LENGTH_LONG).show();return};val items=savedDrawings().filterNot{it.uri==item.uri};prefs.edit().putString("saved_drawings",items.joinToString("\u001e"){"${it.uri}\u001f${it.label}"}).apply();savedRevision++;thumbnailRevision++}
    private fun normalizedSavedName(value:String):String{val raw=value.trim().let{if(it.endsWith(".dwg",true))it.dropLast(4)else it};val stem=raw.replace(Regex("[^A-Za-zА-Яа-я0-9._ -]"),"_").take(72).trim().ifBlank{"drawing"};return "$stem.dwg"}
    private fun saveCurrentCopy(requestedName:String){
        val source=currentDrawingFile?.takeIf{it.isFile}?:run{Toast.makeText(this,R.string.copy_save_failed,Toast.LENGTH_LONG).show();return}
        val label=normalizedSavedName(requestedName)
        if(savedDrawings().any{it.label.equals(label,true)}){Toast.makeText(this,R.string.copy_name_exists,Toast.LENGTH_LONG).show();return}
        openExecutor.execute{runCatching{
            if(savedDrawings().any{it.label.equals(label,true)})throw DuplicateSavedNameException()
            val dir=File(filesDir,"saved-dwg").apply{mkdirs()};val stem=label.dropLast(4);val target=File(dir,label);if(target.exists())throw DuplicateSavedNameException();val temp=File(dir,".$stem.part")
            source.inputStream().buffered().use{input->temp.outputStream().buffered().use{output->input.copyTo(output)}};if(temp.length()<6)throw IOException("DWG copy is empty or truncated");if(!temp.renameTo(target)){temp.copyTo(target,false);temp.delete()}
            val item=Recent(Uri.fromFile(target).toString(),label);val sourceThumb=thumbnailFile(Uri.fromFile(source).toString());if(sourceThumb.isFile){val targetThumb=thumbnailFile(item.uri);targetThumb.parentFile?.mkdirs();sourceThumb.copyTo(targetThumb,false)}
            val items=savedDrawings().toMutableList().apply{add(0,item)};prefs.edit().putString("saved_drawings",items.joinToString("\u001e"){"${it.uri}\u001f${it.label}"}).apply();runOnUiThread{savedRevision++;thumbnailRevision++;if(!thumbnailFile(item.uri).isFile)drawingView?.let{ensureThumbnail(it,item.uri)};Toast.makeText(this,R.string.copy_saved,Toast.LENGTH_SHORT).show()}
        }.onFailure{error->Log.e("BraginCAD","Cannot save offline copy",error);runOnUiThread{Toast.makeText(this,if(error is DuplicateSavedNameException)R.string.copy_name_exists else R.string.copy_save_failed,Toast.LENGTH_LONG).show()}}}
    }
    private class DuplicateSavedNameException:IOException("Saved drawing name already exists")
    private fun thumbnailFile(uri:String)=File(File(filesDir,"drawing-thumbnails"),"v2_${Integer.toHexString(uri.hashCode())}.jpg")
    private fun ensureThumbnail(view:DrawingView,uri:String){
        val target=thumbnailFile(uri);if(target.isFile)return
        val bitmap=view.captureThumbnail(256)?:return
        openExecutor.execute{runCatching{target.parentFile?.mkdirs();val temp=File(target.parentFile,".${target.name}.part");temp.outputStream().buffered().use{bitmap.compress(Bitmap.CompressFormat.JPEG,82,it)};if(!temp.renameTo(target)){temp.copyTo(target,true);temp.delete()};runOnUiThread{thumbnailRevision++}}.onFailure{Log.w("BraginCAD","Cannot create drawing thumbnail",it)}.also{bitmap.recycle()}}
    }
    private fun projects():List<CadProject> = prefs.getString("projects","").orEmpty().split("|||").mapNotNull{record->if(!record.contains("::"))return@mapNotNull null;val parts=record.split("::",limit=2);val name=Uri.decode(parts[0]);val files=parts[1].split(",,").mapNotNull{file->if(!file.contains("=="))null else file.split("==",limit=2).let{Recent(Uri.decode(it[0]),Uri.decode(it[1]))}};CadProject(name,files)}
    private fun addProjectFile(name:String,file:Recent){val all=projects().toMutableList();val index=all.indexOfFirst{it.name.equals(name,true)};if(index>=0){val current=all[index];all[index]=current.copy(files=current.files.filterNot{it.uri==file.uri}+file)}else all+=CadProject(name,listOf(file));prefs.edit().putString("projects",all.joinToString("|||"){project->Uri.encode(project.name)+"::"+project.files.joinToString(",,"){Uri.encode(it.uri)+"=="+Uri.encode(it.label)}}).apply();runOnUiThread{projectRevision++}}
    private fun materializeRecent(uri:Uri,label:String):Pair<File,Boolean>{
        val dir=File(filesDir,"recent-dwg").apply{mkdirs()}
        if(uri.scheme=="file"){val source=File(requireNotNull(uri.path));val savedDir=File(filesDir,"saved-dwg");if(source.isFile&&(source.canonicalPath.startsWith(dir.canonicalPath+File.separator)||source.canonicalPath.startsWith(savedDir.canonicalPath+File.separator)))return source to false}
        val stem=label.substringBeforeLast('.').replace(Regex("[^A-Za-zА-Яа-я0-9._ -]"),"_").take(72).ifBlank{"drawing"}
        val target=File(dir,"${stem}_${Integer.toHexString(uri.toString().hashCode())}.dwg")
        val temp=File(dir,".${target.name}.part")
        try{contentResolver.openInputStream(uri).use{input->if(input==null)throw IOException("ContentResolver returned no stream for $uri");temp.outputStream().buffered().use{output->input.copyTo(output)}};if(temp.length()<6)throw IOException("DWG stream is empty or truncated");if(target.exists()&&!target.delete())throw IOException("Cannot replace cached drawing");if(!temp.renameTo(target)){temp.copyTo(target,true);temp.delete()};return target to true}catch(t:Throwable){temp.delete();throw t}
    }
    private fun addRecent(uri: Uri, label: String) { val current=uri.toString();val items=recent();val removed=items.filter{it.uri==current||it.label.equals(label,true)};items.removeAll { it.uri==current||it.label.equals(label,true) };items.add(0, Recent(current, label));val kept=items.take(10);(removed+items.drop(10)).mapNotNull{runCatching{Uri.parse(it.uri)}.getOrNull()}.filter{it.scheme=="file"&&it.toString()!=current}.forEach{old->runCatching{File(requireNotNull(old.path)).takeIf{file->file.parentFile==File(filesDir,"recent-dwg")}?.delete()}}; prefs.edit().putString("recent", kept.joinToString("\u001e") { "${it.uri}\u001f${it.label}" }).apply() }
    companion object { private const val OPEN_DWG = 1001;private const val ADD_PROJECT_DWG=1002;private val DWG_MIME_TYPES=setOf("application/acad","application/x-acad","application/autocad_dwg","application/dwg","image/vnd.dwg","application/octet-stream") }
}
