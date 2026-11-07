package com.intentgate.app.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock
import android.os.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AndroidDetectionClock:DetectionClock {
    override fun wallMillis()=System.currentTimeMillis()
    override fun elapsedMillis()=SystemClock.elapsedRealtime()
}
class AndroidForegroundEventSource(context:Context, private val includeOtherResumes:Boolean=false):ForegroundEventSource {
    private val app=context.applicationContext
    override suspend fun read(fromMillis:Long,untilMillis:Long,packages:Set<String>):ForegroundEventBatch=withContext(Dispatchers.IO) {
        if(!hasUsageAccess(app)) return@withContext ForegroundEventBatch(DetectionStatus.PermissionRequired)
        if(!app.getSystemService(UserManager::class.java).isUserUnlocked) return@withContext ForegroundEventBatch(DetectionStatus.Locked)
        val stream=app.getSystemService(UsageStatsManager::class.java).queryEvents(fromMillis,untilMillis)
            ?: return@withContext ForegroundEventBatch(DetectionStatus.Locked)
        val event=UsageEvents.Event()
        val events=mutableListOf<ForegroundEvent>()
        while(stream.hasNextEvent()) {
            stream.getNextEvent(event)
            if(event.packageName in packages || event.eventType in setOf(16,17,26,27) ||
                (includeOtherResumes && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED))
                events+=ForegroundEvent(event.timeStamp,event.packageName.orEmpty(),event.className.orEmpty(),event.eventType)
        }
        if(!hasUsageAccess(app)) ForegroundEventBatch(DetectionStatus.PermissionRequired)
        else ForegroundEventBatch(DetectionStatus.Observing,events)
    }
}
fun enabledForegroundTargets(repository:AppRepository):List<ForegroundTarget> {
    val enabled=repository.apps.value.filter { it.isManaged }.associateBy { it.appId }
    return repository.catalog.mapNotNull { app -> enabled[app.id]?.takeIf { app.isAvailable }?.let {
        ForegroundTarget(app.id,app.packageName,it.intentCheckEnabled)
    } }
}
