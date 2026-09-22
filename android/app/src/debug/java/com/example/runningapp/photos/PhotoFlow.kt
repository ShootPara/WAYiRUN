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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.*

/** Decode at a bounded size; ImageDecoder applies EXIF orientation. Re-encoding omits source metadata. */
internal fun decodePhoto(context: android.content.Context, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val factor = min(1.0, 1600.0 / max(info.size.width, info.size.height))
        decoder.setTargetSize(max(1, (info.size.width * factor).toInt()), max(1, (info.size.height * factor).toInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

internal fun renderPhoto(source: Bitmap, s: RunSnapshot, route: List<RoutePoint>, flags: List<Boolean>): ByteArray {
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
    val panel=spacing*(lines.size+1)
    paint.color=Color.argb(190,0,0,0); c.drawRect(0f,h-panel,w,h,paint)
    paint.color=Color.WHITE;paint.textSize=textSize;paint.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
    lines.forEachIndexed { i,line -> c.drawText(line,w*.05f,h-panel+spacing*(i+1),paint) }
    if(flags[3] && route.size>1) {
        val lat=route.map { it.latitude }; val lon=route.map { it.longitude }
        val latitude=(lat.min()+lat.max())/2; val factor=cos(Math.toRadians(latitude)).coerceAtLeast(.01)
        var previous: Double?=null
        val xs=lon.map { raw -> val value=raw+360*round(((previous?:raw)-raw)/360);previous=value;value*factor }; val ys=lat.map { -it }
        val minX=xs.min();val minY=ys.min()
        val dx=xs.max()-xs.min();val dy=ys.max()-ys.min()
        val size=min(w*.38f,(h-panel)*.7f)
        if(size>0 && max(dx,dy)>0) {
            val scale=size/max(dx,dy);val left=w-size-w*.07f;val top=h-panel-size-h*.04f
            paint.color=Color.argb(170,0,0,0);c.drawRoundRect(left-12,top-12,left+size+12,top+size+12,12f,12f,paint)
            paint.color=Color.rgb(200,245,100);paint.strokeWidth=max(3f,w/250);paint.style=Paint.Style.STROKE
            for(i in 1 until route.size) if(route[i].segmentId==route[i-1].segmentId && route[i].monotonicMs-route[i-1].monotonicMs in 1..10000) {
                c.drawLine((left+(xs[i-1]-minX)*scale).toFloat(),(top+(ys[i-1]-minY)*scale).toFloat(),(left+(xs[i]-minX)*scale).toFloat(),(top+(ys[i]-minY)*scale).toFloat(),paint)
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
    if(open) PhotoDialog(s) { open=false }
}

@Composable
private fun PhotoDialog(s: RunSnapshot, close:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val dao=remember { RunDatabase.get(context).runs() }
    var source by remember { mutableStateOf<Bitmap?>(null) };var preview by remember { mutableStateOf<Bitmap?>(null) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) };var kept by remember { mutableStateOf<RunPhoto?>(null) }
    var route by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    var flags by rememberSaveable { mutableStateOf(listOf(true,true,true,false)) }
    var public by rememberSaveable { mutableStateOf(true) };var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val draft=remember(s.runId) { File(File(context.cacheDir,"photos").apply {mkdirs()},"draft-${s.runId}.jpg") }
    fun done() { draft.delete();cameraPath?.let {File(it).delete()};close() }
    fun load(uri: Uri) { scope.launch { busy=true;error=null
        try { source=withContext(Dispatchers.IO) {
            val image=decodePhoto(context,uri)
            val atomic=android.util.AtomicFile(draft);val stream=atomic.startWrite()
            try {check(image.compress(Bitmap.CompressFormat.JPEG,95,stream));atomic.finishWrite(stream)}
            catch(e:Exception) {atomic.failWrite(stream);throw e}
            cameraPath?.let {File(it).delete()}
            image
        } }
        catch(_:Exception) { error="Could not open that photo. Please try another." }
        finally { busy=false }
    } }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if(ok) cameraPath?.let { load(Uri.fromFile(File(it))) } else cameraPath?.let { File(it).delete() } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(::load) }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val data=kept?.jpeg
        if(uri!=null && data!=null) scope.launch { try { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(data) } ?: error("Unable to save") };error="Photo saved." } catch(_:Exception) {error="Could not save the photo. Please try again."} }
    }
    LaunchedEffect(s.runId) {
        route=dao.route(s.runId)
        if(source==null && draft.exists()) {
            try { source=withContext(Dispatchers.IO) { decodePhoto(context,Uri.fromFile(draft)) } }
            catch(_:Exception) {draft.delete()}
        }
        while(true) {kept=dao.photo(s.runId);kotlinx.coroutines.delay(3000)}
    }
    LaunchedEffect(source,flags,route) {
        val image=source ?: return@LaunchedEffect
        busy=true
        try { val data=withContext(Dispatchers.Default) { renderPhoto(image,s,route,flags) };bytes=data;preview=BitmapFactory.decodeByteArray(data,0,data.size) }
        catch(_:Exception) {bytes=null;error="Could not prepare that photo. Please try another."}
        finally {busy=false}
    }
    Dialog(onDismissRequest={if(!busy)done()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Your run photo",style=MaterialTheme.typography.headlineMedium)
            error?.let { Text(it) }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val visible=preview ?: kept?.jpeg?.let { BitmapFactory.decodeByteArray(it,0,it.size) }
            visible?.let { Image(it.asImageBitmap(),"Run photo preview",Modifier.fillMaxWidth().heightIn(max=360.dp)) }
            Button(enabled=!busy,onClick={
                try {
                    val file=File(File(context.cacheDir,"photos").apply {mkdirs()},"capture-${s.runId}-${UUID.randomUUID()}.jpg")
                    cameraPath=file.absolutePath
                    camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.photos",file))
                } catch(_:Exception) {error="No camera is available. Choose an existing photo instead."}
            }) { Text(if(source==null) "Take Photo" else "Retake") }
            Text("Use the camera's switch button for front or rear camera.")
            OutlinedButton(enabled=!busy,onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) {Text("Choose Existing Photo")}
            if(source!=null) {
                listOf("Time","Distance","Pace","Route").forEachIndexed { i,label ->
                    Row { Checkbox(flags[i],onCheckedChange={ value -> flags=flags.mapIndexed { j,v -> if(i==j)value else v } },enabled=!busy && (i!=3 || route.size>1));Text(label,Modifier.padding(top=12.dp)) }
                }
                Row {Checkbox(public,{public=it},enabled=!busy);Text("Make this run public",Modifier.padding(top=12.dp))}
                Text(if(public) "Keeping publishes this photo, run location, route and splits when synced." else "This photo will stay private. Any previously shared version is removed when this replacement syncs.")
                Button(enabled=!busy && bytes!=null,onClick={scope.launch {
                    busy=true;error=null
                    try {
                        val record=RunPhoto(s.runId,UUID.randomUUID().toString(),bytes!!,JSONObject().put("time",flags[0]).put("distance",flags[1]).put("pace",flags[2]).put("route",flags[3]).toString(),public)
                        dao.putPhoto(record);draft.delete();kept=record;source=null;preview=null;bytes=null;SyncScheduler.enqueue(context)
                    } catch(_:Exception) {error="Could not keep the photo. Your run is still saved."}
                    finally {busy=false}
                }}) {Text("Keep Photo")}
            } else if(kept!=null) {
                Text(if(kept!!.synced) "Saved on this phone and in your account." else "Saved on this phone. Cloud sync pending.")
                kept?.publicUrl?.let { link ->
                    OutlinedButton(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,link)},"Share run link"))}) {Text("Share run link")}
                }
                Button(onClick={save.launch("WAYiRUN-${s.runId}.jpg")}) {Text("Save photo")}
                OutlinedButton(onClick={scope.launch {
                    try {
                        val file=withContext(Dispatchers.IO) {File(File(context.cacheDir,"photos").apply {mkdirs()},"share-${s.runId}.jpg").apply {writeBytes(kept!!.jpeg)}}
                        val uri=FileProvider.getUriForFile(context,"${context.packageName}.photos",file)
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="image/jpeg";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri("Run photo",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share run photo"))
                    } catch(_:Exception) {error="Could not share the photo. Please try again."}
                }}) {Text("Share photo")}
            }
            TextButton(enabled=!busy,onClick=::done) {Text(if(kept==null) "Skip" else "Done")}
        } }
    }
}
