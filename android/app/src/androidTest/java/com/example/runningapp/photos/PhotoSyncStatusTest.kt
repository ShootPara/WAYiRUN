package com.example.runningapp.photos

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.runningapp.account.*
import com.example.runningapp.domain.*
import com.example.runningapp.storage.*
import com.example.runningapp.ui.SyncPanel
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File
import java.util.UUID

class PhotoSyncStatusTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun pendingPhotosAndSafeErrorRemainReadableAndClearAfterReceipt() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,RunDatabase::class.java).build()
        val dao=db.runs();val id=UUID.randomUUID().toString();val visible=mutableStateOf(true)
        val dark=mutableStateOf(false)
        try {
            runBlocking {
                val run=RunController(id,RunSettings(RunMode.INDOOR,RunUnits.MILES,0,RunGoal.None,null),RunClock {RunTime(0,0)})
                run.start();run.finish();RunRepository(dao).save(run.checkpoint(),"local","UTC",0,false,cloudOwnerId="alice")
                dao.putOperation(dao.operation(id)!!.copy(status="SYNCED"))
                dao.putPhoto(RunPhoto(id,"revision",byteArrayOf(),"{}",false,syncError="REJECTED"))
            }
            compose.activityRule.scenario.onActivity {it.setTurnScreenOn(true);it.setShowWhenLocked(true)}
            compose.setContent {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                    MaterialTheme(colorScheme=if(dark.value)darkColorScheme() else lightColorScheme()) {
                        if(visible.value)Surface {Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            SyncPanel(AccountView(AccountSession("alice","Test runner",null,"synthetic",Long.MAX_VALUE)),true,{}, {},dao)
                        }}
                    }
                }
            }
            compose.waitUntil(10000) {compose.onAllNodesWithText("Photos waiting to upload: 1").fetchSemanticsNodes().isNotEmpty()}
            for(mode in listOf(false,true)) {
                compose.runOnIdle {dark.value=mode}
                compose.onNodeWithText(photoSyncMessage("REJECTED")).performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("retry-sync").performScrollTo().assertIsDisplayed()
                val output=File(context.getExternalFilesDir(null),"photo-sync-${if(mode) "dark" else "light"}-200.png")
                compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
                    output.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                }
            }
            runBlocking {dao.photoUploadResult(id,"revision","alice",true,null)}
            compose.waitUntil(10000) {compose.onAllNodesWithText("Photos waiting to upload: 1").fetchSemanticsNodes().isEmpty()}
            compose.onNodeWithText(photoSyncMessage("REJECTED")).assertDoesNotExist()
        } finally {compose.runOnIdle {visible.value=false};compose.waitForIdle();db.close()}
    }
}
