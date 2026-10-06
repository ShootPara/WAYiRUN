package com.example.runningapp.photos

import android.content.ClipData
import android.content.Intent
import android.graphics.*
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.runningapp.domain.RunSnapshot
import com.example.runningapp.domain.RunUnits
import com.example.runningapp.storage.*
import com.example.runningapp.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.*

val LocalExternalRunAction = staticCompositionLocalOf<(String?) -> Unit> { {} }
internal data class PreparedPhoto(val source: Bitmap, val editorId: String, val recipe: PhotoRecipe, val jpeg: ByteArray)

/** Decode at a bounded size; ImageDecoder applies EXIF orientation. Re-encoding omits source metadata. */
internal fun decodePhoto(context: android.content.Context, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val factor = min(1.0, 1600.0 / max(info.size.width, info.size.height))
        decoder.setTargetSize(max(1, (info.size.width * factor).toInt()), max(1, (info.size.height * factor).toInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

internal fun renderPhoto(source: Bitmap, s: RunSnapshot, route: List<RoutePoint>, flags: List<Boolean>, weather: PhotoWeather? = null): ByteArray {
    val out = source.copy(Bitmap.Config.ARGB_8888, true)
    val c = Canvas(out); val w = out.width.toFloat(); val h = out.height.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val unit = if (s.settings.units == RunUnits.MILES) "mi" else "km"
    fun duration(ms: Long): String { val n=ms/1000; return if(n>=3600) String.format(Locale.US,"%d:%02d:%02d",n/3600,n/60%60,n%60) else String.format(Locale.US,"%d:%02d",n/60,n%60) }
    val lines = mutableListOf("WAYiRUN")
    if(flags[0]) lines += "Time  ${duration(s.activeDurationMs)}"
    if(flags[1]) lines += String.format(Locale.US,"Distance  %.2f %s",s.distanceMeters/s.settings.units.metersPerUnit,unit)
    if(flags[2]) lines += "Pace  ${s.averagePaceMsPerUnit?.let { duration(it.toLong()) } ?: "—"} /$unit"
    val textSize = min(w/20f,h/16f); val spacing=textSize*1.45f
    val selectedWeather=weather.takeIf { flags.getOrNull(4)==true }
    val creditSize=min(w*.021f,h*.024f)
    val creditHeight=if(selectedWeather!=null) creditSize*3.4f else 0f
    val panel=spacing*(lines.size+1)+creditHeight
    paint.color=Color.argb(190,0,0,0); c.drawRect(0f,h-panel,w,h,paint)
    paint.color=Color.WHITE;paint.textSize=textSize;paint.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
    lines.forEachIndexed { i,line ->
        paint.textSize=textSize
        if(paint.measureText(line)>w*.9f)paint.textSize*=w*.9f/paint.measureText(line)
        c.drawText(line,w*.05f,h-panel+spacing*(i+1),paint)
    }
    if(selectedWeather!=null) {
        val value=selectedWeather.snapshot
        paint.typeface=Typeface.DEFAULT;paint.textAlign=Paint.Align.RIGHT
        paint.setShadowLayer(max(2f,w/400),0f,1f,Color.BLACK)
        paint.textSize=textSize*1.15f
        c.drawText(value.emoji,w*.94f,h*.05f+paint.textSize,paint)
        paint.textSize=textSize*.62f
        c.drawText(String.format(Locale.US,"%.1f °F",value.temperatureF),w*.94f,h*.05f+textSize*2.1f,paint)
        c.drawText(String.format(Locale.US,"%.1f °C",value.temperatureC),w*.94f,h*.05f+textSize*2.9f,paint)
        paint.clearShadowLayer();paint.textAlign=Paint.Align.LEFT
        listOf("Weather: Open-Meteo.com · CC BY 4.0", "creativecommons.org/licenses/by/4.0/ · Rounded estimate + emoji").forEachIndexed { i,line ->
            paint.textSize=creditSize
            if(paint.measureText(line)>w*.9f)paint.textSize*=w*.9f/paint.measureText(line)
            c.drawText(line,w*.05f,h-creditSize*(2.1f-i*1.2f),paint)
        }
    }
    if(flags[3] && route.size>1) {
        val lat=route.map { it.latitude }; val lon=route.map { it.longitude }
        val latitude=(lat.min()+lat.max())/2; val factor=cos(Math.toRadians(latitude)).coerceAtLeast(.01)
        var previous: Double?=null
        val xs=lon.map { raw -> val value=raw+360*round(((previous?:raw)-raw)/360);previous=value;value*factor }; val ys=lat.map { -it }
        val minX=xs.min();val minY=ys.min()
        val dx=xs.max()-xs.min();val dy=ys.max()-ys.min()
        val routeTop=if(selectedWeather!=null)h*.05f+textSize*3.3f else h*.04f
        val size=min(w*.38f,max(0f,h-panel-h*.04f-routeTop))
        if(size>0 && max(dx,dy)>0) {
            val scale=size/max(dx,dy);val left=w-size-w*.07f+(size-dx*scale).toFloat()/2;val top=h-panel+(panel-creditHeight-(dy*scale).toFloat())/2
            paint.style=Paint.Style.STROKE;paint.strokeCap=Paint.Cap.ROUND
            for(i in 1 until route.size) if(route[i].segmentId==route[i-1].segmentId && route[i].monotonicMs-route[i-1].monotonicMs in 1..10000) {
                for(halo in listOf(true,false)) {
                    paint.color=if(halo)Color.argb(200,0,0,0) else Color.rgb(200,245,100)
                    paint.strokeWidth=max(3f,w/250)*(if(halo)2f else 1f)
                    c.drawLine((left+(xs[i-1]-minX)*scale).toFloat(),(top+(ys[i-1]-minY)*scale).toFloat(),(left+(xs[i]-minX)*scale).toFloat(),(top+(ys[i]-minY)*scale).toFloat(),paint)
                }
            }
        }
    }
    var bytes=byteArrayOf()
    for(quality in listOf(90,80,65,45,25)) {
        val stream=ByteArrayOutputStream();out.compress(Bitmap.CompressFormat.JPEG,quality,stream);bytes=stream.toByteArray()
        if(bytes.size<=1_000_000) break
    }
    out.recycle();require(bytes.size<=1_000_000) { "Photo is too large; choose a different image." };return bytes
}

@Composable
fun RunPhotoButton(s: RunSnapshot) {
    var open by rememberSaveable(s.runId) { mutableStateOf(false) }
    Button(onClick={open=true},modifier=Modifier.fillMaxWidth()) { Text("Take / choose a run photo") }
    if(open) key(s.runId) { PhotoDialog(s, close={open=false}) }
}

@Composable
internal fun PhotoDialog(s: RunSnapshot, close:()->Unit, daoOverride: RunDao? = null,
    weatherApi: com.example.runningapp.sync.SyncApi? = null,
    sessionReader: (() -> com.example.runningapp.account.AccountSession?)? = null, onKept: (() -> Unit)? = null) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val dao=daoOverride ?: remember { RunDatabase.get(context).runs() }
    val sessions=remember { com.example.runningapp.account.SessionStore(context) }
    val readSession=sessionReader ?: sessions::read
    val api=weatherApi ?: remember { com.example.runningapp.sync.CloudSyncApi() }
    val identity=rememberSaveable(s.runId) { photoSessionIdentity(readSession()) }
    val externalRunAction = LocalExternalRunAction.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var source by remember { mutableStateOf<Bitmap?>(null) };var preview by remember { mutableStateOf<Bitmap?>(null) }
    var prepared by remember { mutableStateOf<PreparedPhoto?>(null) };var kept by remember { mutableStateOf<RunPhoto?>(null) }
    var editorRun by remember { mutableStateOf<StoredRun?>(null) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var priorRevision by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var acceptingWeather by rememberSaveable { mutableStateOf(true) }
    var weatherJson by rememberSaveable { mutableStateOf<String?>(null) }
    var weatherRetry by remember { mutableIntStateOf(0) }
    var weatherLoading by remember { mutableStateOf(false) }
    var weatherReason by rememberSaveable { mutableStateOf("provider_unavailable") }
    var weatherRetryAfter by rememberSaveable { mutableLongStateOf(0L) }
    var weatherWaitingForSync by remember { mutableStateOf(false) }
    var weatherNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val weatherFailure = PhotoWeatherResult(waitingForSync=weatherWaitingForSync,reason=weatherReason,retryAfter=weatherRetryAfter)
    var route by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    var flags by rememberSaveable { mutableStateOf(listOf(true,true,true,false,true)) }
    val weather=remember(weatherJson) { PhotoWeather.parse(weatherJson) }
    val recipe=remember(flags,weather) { PhotoRecipe(flags,weather.takeIf {flags.getOrNull(4)==true}) }
    var busy by remember { mutableStateOf(false) }
    var sourceLoading by remember { mutableStateOf(false) }
    val controlsBusy=busy || sourceLoading
    var error by remember { mutableStateOf<String?>(null) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val draft=remember(s.runId) { File(File(context.cacheDir,"photos").apply {mkdirs()},"draft-${s.runId}.jpg") }
    fun done() { acceptingWeather=false;editorId=UUID.randomUUID().toString();draft.delete();cameraPath?.let {File(it).delete()};close() }
    fun load(uri: Uri) {
        editorId=UUID.randomUUID().toString();acceptingWeather=true;weatherJson=null;prepared=null;sourceLoading=true
        priorRevision=kept?.revision
        scope.launch { error=null
        try { source=withContext(Dispatchers.IO) {
            val image=decodePhoto(context,uri)
            val atomic=android.util.AtomicFile(draft);val stream=atomic.startWrite()
            try {check(image.compress(Bitmap.CompressFormat.JPEG,95,stream));atomic.finishWrite(stream)}
            catch(e:Exception) {atomic.failWrite(stream);throw e}
            cameraPath?.let {File(it).delete()}
            image
        } }
        catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
        catch(_:Exception) { error="Could not open that photo. Please try another." }
        finally { sourceLoading=false }
    } }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if(ok) cameraPath?.let { load(Uri.fromFile(File(it))) } else cameraPath?.let { File(it).delete() } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(::load) }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val data=kept?.jpeg
        if(uri!=null && data!=null) scope.launch { try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: error("Unable to save") };error="Photo saved." } catch(_:Exception) {error="Could not save the photo. Please try again."} }
    }
    LaunchedEffect(s.runId) {
        editorRun=dao.get(s.runId)
        if(photoSessionIdentity(readSession())!=identity || editorRun?.cloudOwnerId?.let { it!=readSession()?.ownerId }==true) {
            done();return@LaunchedEffect
        }
        kept=dao.photo(s.runId)
        if(!initialized) {priorRevision=kept?.revision;initialized=true}
        route=dao.route(s.runId)
        if(source==null && draft.exists()) {
            try { source=withContext(Dispatchers.IO) { decodePhoto(context,Uri.fromFile(draft)) } }
            catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
            catch(_:Exception) {draft.delete()}
        }
        while(true) {
            if(photoSessionIdentity(readSession())!=identity || (editorRun!=null && dao.get(s.runId)==null)) {done();return@LaunchedEffect}
            val latest=dao.photo(s.runId)
            if(source!=null && latest?.revision!=priorRevision) {done();return@LaunchedEffect}
            kept=latest;kotlinx.coroutines.delay(3000)
        }
    }
    LaunchedEffect(source!=null,editorId,weatherRetry,acceptingWeather,route.isNotEmpty()) {
        if(source==null || !acceptingWeather || weather!=null || s.settings.mode!=com.example.runningapp.domain.RunMode.OUTDOOR || route.isEmpty()) return@LaunchedEffect
        if(System.currentTimeMillis()<weatherRetryAfter) return@LaunchedEffect
        val requestEditor=editorId
        try {
            while(acceptingWeather && editorId==requestEditor && photoSessionIdentity(readSession())==identity) {
                weatherLoading=true
                val result=lookupPhotoWeather(s.runId,dao,readSession,api)
                if(!acceptingWeather || editorId!=requestEditor || photoSessionIdentity(readSession())!=identity) return@LaunchedEffect
                weatherLoading=false
                weatherWaitingForSync=result.waitingForSync
                if(result.waitingForSync) {kotlinx.coroutines.delay(3000);continue}
                weatherJson=result.weather?.json;weatherReason=result.reason;weatherRetryAfter=result.retryAfter
                break
            }
        } finally {weatherLoading=false}
    }
    LaunchedEffect(weatherRetryAfter) {
        weatherNow=System.currentTimeMillis()
        while(weatherNow<weatherRetryAfter) {
            kotlinx.coroutines.delay(minOf(1000L,weatherRetryAfter-weatherNow))
            weatherNow=System.currentTimeMillis()
        }
    }
    LaunchedEffect(source,recipe,route,editorId) {
        val image=source ?: return@LaunchedEffect
        val renderingId=editorId
        busy=true;prepared=null
        try {
            val data=withContext(Dispatchers.Default) { renderPhoto(image,s,route,recipe.flags,recipe.selectedWeather) }
            prepared=PreparedPhoto(image,renderingId,recipe,data);preview=BitmapFactory.decodeByteArray(data,0,data.size)
        }
        catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
        catch(_:Exception) {prepared=null;error="Could not prepare that photo. Please try another."}
        finally {busy=false}
    }
    Dialog(onDismissRequest={if(!controlsBusy)done()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize().testTag("photo-editor")) { Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Your run photo",style=MaterialTheme.typography.headlineMedium)
            error?.let { Text(it) }
            if(controlsBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val visible=preview ?: kept?.jpeg?.let { BitmapFactory.decodeByteArray(it,0,it.size) }
            visible?.let { Image(it.asImageBitmap(),"Run photo preview",Modifier.fillMaxWidth().heightIn(max=360.dp)) }
            Button(enabled=!controlsBusy,onClick={
                try {
                    val file=File(File(context.cacheDir,"photos").apply {mkdirs()},"capture-${s.runId}-${UUID.randomUUID()}.jpg")
                    cameraPath=file.absolutePath
                    externalRunAction(s.runId)
                    camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.photos",file))
                } catch(_:Exception) {externalRunAction(null);error="No camera is available. Choose an existing photo instead."}
            }) { Text(if(source==null) "Take Photo" else "Retake") }
            Text("Use the camera's switch button for front or rear camera.")
            OutlinedButton(enabled=!controlsBusy,onClick={
                try { externalRunAction(s.runId);picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                catch(_:Exception) {externalRunAction(null);error="Could not open the photo picker. Please try again."}
            }) {Text("Choose Existing Photo")}
            if(source!=null) {
                listOf("Time","Distance","Pace","Route","Weather").forEachIndexed { i,label ->
                    Row { Checkbox(flags[i] && (i!=4 || weather!=null),onCheckedChange={ value -> flags=flags.mapIndexed { j,v -> if(i==j)value else v } },enabled=!controlsBusy && (i!=3 || route.size>1) && (i!=4 || weather!=null),modifier=Modifier.testTag("photo-overlay-$label"));Text(label,Modifier.padding(top=12.dp)) }
                }
                if(weather==null) {
                    Text(if(weatherLoading) "Getting weather…" else weatherFailure.message(weatherNow),style=MaterialTheme.typography.bodySmall)
                    if(s.settings.mode==com.example.runningapp.domain.RunMode.OUTDOOR && route.isNotEmpty())
                        TextButton(enabled=!weatherLoading && !controlsBusy && weatherFailure.canRetry(weatherNow),onClick={
                            if(weatherFailure.canRetry(System.currentTimeMillis())) weatherRetry++
                        }) {Text("Retry weather")}
                }
                Button(enabled=!controlsBusy && prepared?.let {it.source===source && it.editorId==editorId && it.recipe==recipe}==true,onClick={
                    val ready=prepared ?: return@Button
                    if(busy || sourceLoading || ready.source!==source || ready.editorId!=editorId || ready.recipe!=recipe) return@Button
                    acceptingWeather=false;busy=true;error=null
                    scope.launch {
                    try {
                        check(photoSessionIdentity(readSession())==identity)
                        val run=requireNotNull(dao.get(s.runId))
                        check(run.cloudOwnerId==null || run.cloudOwnerId==readSession()?.ownerId)
                        check(photoSessionIdentity(readSession())==identity)
                        val record=RunPhoto(s.runId,UUID.randomUUID().toString(),ready.jpeg,ready.recipe.options,false,weather=ready.recipe.selectedWeather?.json)
                        check(dao.keepPhoto(record,run.ownerId,run.cloudOwnerId,priorRevision))
                        draft.delete();kept=record;priorRevision=record.revision;source=null;preview=null;prepared=null
                        if(onKept!=null)onKept() else SyncScheduler.enqueue(context)
                    } catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
                    catch(_:Exception) {acceptingWeather=true;error="Could not keep the photo. Your run is still saved."}
                    finally {busy=false}
                }}) {Text("Keep Photo")}
            } else if(kept!=null) {
                Text(if(kept!!.synced) "Saved on this phone and in your account." else "Saved on this phone. Cloud sync pending.")
                kept!!.syncError?.let { Text(photoSyncMessage(it),style=MaterialTheme.typography.bodySmall) }
                com.example.runningapp.sharing.RunSharing(s.runId)
                Button(onClick={
                    try { externalRunAction(s.runId);save.launch("WAYiRUN-${s.runId}.jpg") }
                    catch(_:Exception) {externalRunAction(null);error="Could not open photo saving. Please try again."}
                }) {Text("Save photo")}
                OutlinedButton(onClick={scope.launch {
                    try {
                        val session = com.example.runningapp.account.SessionStore(context).read()?.token
                        val intent = com.example.runningapp.sharing.queueRunPublication(context,s.runId,shared=true)
                        val file=withContext(Dispatchers.IO) {File(File(context.cacheDir,"photos").apply {mkdirs()},"share-${s.runId}.jpg").apply {writeBytes(kept!!.jpeg)}}
                        if(session != com.example.runningapp.account.SessionStore(context).read()?.token || dao.get(s.runId)==null ||
                            dao.publication(s.runId)?.intentId != intent.intentId ||
                            !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return@launch
                        val uri=FileProvider.getUriForFile(context,"${context.packageName}.photos",file)
                        externalRunAction(s.runId)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="image/jpeg";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri("Run photo",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share run photo"))
                    } catch(cancel:kotlinx.coroutines.CancellationException) {throw cancel}
                    catch(_:Exception) {externalRunAction(null);error="Could not share the photo. Please try again."}
                }}) {Text("Share photo")}
            }
            TextButton(enabled=!controlsBusy,onClick=::done) {Text(if(kept==null) "Skip" else "Done")}
        } }
    }
}
