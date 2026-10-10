package com.hirolifecreative.floatingmap

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Reads visible addresses and optionally accepts Dasher offers; never logs screen contents. */
class DashAddressService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var scanQueued = false
    private var visited = 0
    private var lastStatus = 0L
    private val offerLatch=DasherOfferLatch()
    private var awaitingAcceptedAddress=false
    private val prefs by lazy { getSharedPreferences("mapa_flotante",MODE_PRIVATE) }
    private val preferenceListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
        if(key=="dasher_accept_all"||key=="dasher_auto"||key?.startsWith("offer_")==true) {
            offerLatch.reset();if(!prefs.getBoolean("dasher_accept_all",false)) awaitingAcceptedAddress=false;handler.removeCallbacks(scan);scanQueued=true;handler.post(scan)
        }
    }
    override fun onCreate() { super.onCreate();prefs.registerOnSharedPreferenceChangeListener(preferenceListener) }
    private fun status(message: String) {
        prefs.edit().putString("dasher_status",message).apply()
        sendBroadcast(Intent("com.hirolifecreative.floatingmap.DASHER_SCAN_STATUS").setPackage(packageName).putExtra("status",message))
    }
    private fun acceptButtons(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val found=mutableListOf<AccessibilityNodeInfo>();var count=0
        fun walk(node: AccessibilityNodeInfo?,depth: Int) {
            if(node==null||depth>40||count++>=1800) return
            if(node.isVisibleToUser&&node.isEnabled&&listOf(node.text?.toString().orEmpty(),node.contentDescription?.toString().orEmpty()).flatMap { it.lines() }.any(DasherOfferPolicy::isAccept)) {
                var target: AccessibilityNodeInfo?=node
                repeat(5) {
                    val current=target ?: return@repeat
                    if(current.isClickable&&current.isEnabled&&current.isVisibleToUser && (current.packageName==null||current.packageName.toString() in DasherAutomation.packages)) {
                        found.add(current);target=null
                    } else target=current.parent
                }
            }
            for(i in 0 until minOf(node.childCount,150)) walk(node.getChild(i),depth+1)
        }
        walk(root,0)
        return found.distinctBy { node -> android.graphics.Rect().also { node.getBoundsInScreen(it) }.toShortString() }
    }
    private val scan = object : Runnable {
        override fun run() {
            scanQueued = false
            val voice=VoicePreferences.read(this@DashAddressService)
            if (!prefs.getBoolean("dasher_auto",true)&&!prefs.getBoolean("dasher_accept_all",false)&&(!voice.orderAnnouncements||voice.mode=="off")) return
            var visibleDasher = false
            try {
                val active = rootInActiveWindow
                val roots = buildList {
                    if (active?.packageName?.toString() in DasherAutomation.packages) add(active!!)
                    windows.mapNotNull { it.root }.filter { it.packageName?.toString() in DasherAutomation.packages }.forEach { add(it) }
                }
                visibleDasher = roots.isNotEmpty()
                val snapshots=roots.map { root ->
                    val texts=mutableListOf<String>();visited=0;collect(root,texts,0);root to texts
                }
                val offers=snapshots.filter { (_,texts)->DasherOfferPolicy.isOffer(texts) }
                val anyAccept=snapshots.any { (_,texts)->texts.flatMap { it.lines() }.any(DasherOfferPolicy::isAccept) }
                val now=android.os.SystemClock.elapsedRealtime()
                if(offerLatch.mayClick(now,prefs.getBoolean("dasher_accept_all",false),anyAccept)&&offers.isNotEmpty()) {
                    val filters=OfferFilterPreferences.read(this@DashAddressService)
                    val eligible=offers.filter { (_,texts)->DasherOfferFilters.reason(filters,DasherOfferFilters.metrics(texts))==null }
                    if(eligible.isEmpty()&&System.currentTimeMillis()-lastStatus>10000) {
                        lastStatus=System.currentTimeMillis()
                        status("Dasher · revisión manual: "+DasherOfferFilters.reason(filters,DasherOfferFilters.metrics(offers.first().second)))
                    }
                    val buttons=eligible.flatMap { (root,_)->acceptButtons(root) }.distinctBy { node -> android.graphics.Rect().also { node.getBoundsInScreen(it) }.toShortString() }
                    if(buttons.size==1 && prefs.getBoolean("dasher_accept_all",false)) {
                        val success=buttons.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        offerLatch.recordClick(now,success)
                        if(success) awaitingAcceptedAddress=true
                        status(if(success) "Dasher · aceptación enviada; esperando la dirección del local" else "Dasher · no se pudo pulsar Aceptar; reintentando")
                    }
                }
                val parsedDestinations=snapshots.mapNotNull { (_,texts)->DasherAddressParser.select(texts) }.filter { !awaitingAcceptedAddress || it.stage in listOf("Recogida","Entrega") }.sortedByDescending { it.unit.length+it.merchant.length }.distinctBy { it.address.lowercase(java.util.Locale.ROOT)+"|"+it.stage }
                val destinations=if(anyAccept||!prefs.getBoolean("dasher_auto",true)) emptyList() else parsedDestinations
                val voiceTexts=if(offers.isNotEmpty()) offers.flatMap { it.second }.distinct() else snapshots.firstOrNull()?.second.orEmpty()
                if(visibleDasher) DasherAutomation.announce(this@DashAddressService,voiceTexts,offers.isNotEmpty(),!anyAccept&&parsedDestinations.singleOrNull()?.stage=="Recogida")
                if (destinations.size == 1) { awaitingAcceptedAddress=false;DasherAutomation.submit(this@DashAddressService,destinations.single()) }
                else if (visibleDasher && !anyAccept && System.currentTimeMillis()-lastStatus > 15000) {
                    lastStatus = System.currentTimeMillis()
                    val message = if (destinations.size > 1) "Dasher · varias direcciones visibles; abre la parada actual"
                        else "Dasher · esperando una dirección legible del pedido"
                    getSharedPreferences("mapa_flotante",MODE_PRIVATE).edit().putString("dasher_status",message).apply()
                    sendBroadcast(Intent("com.hirolifecreative.floatingmap.DASHER_SCAN_STATUS").setPackage(packageName).putExtra("status",message))
                }
            } catch (_: Exception) { }
            if (visibleDasher && !scanQueued) { scanQueued = true;handler.postDelayed(this,2000) }
        }
    }
    override fun onServiceConnected() {
        if (!scanQueued) { scanQueued = true;handler.post(scan) }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() !in DasherAutomation.packages) return
        if (!scanQueued) { scanQueued = true;handler.postDelayed(scan,700) }
    }
    private fun collect(node: AccessibilityNodeInfo?,out: MutableList<String>,depth: Int) {
        if (node == null || depth > 40 || visited++ >= 1800 || out.size >= 500) return
        // A container may report itself invisible while its descendants remain visible.
        if (node.isVisibleToUser) {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        }
        for (i in 0 until minOf(node.childCount,150)) collect(node.getChild(i),out,depth+1)
    }
    override fun onInterrupt() { handler.removeCallbacks(scan);scanQueued = false }
    override fun onDestroy() { prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener);handler.removeCallbacks(scan);super.onDestroy() }
}
