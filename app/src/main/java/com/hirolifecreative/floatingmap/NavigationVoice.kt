package com.hirolifecreative.floatingmap

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Talks to the separate official sherpa engine through Android IPC; loads no JNI in :map. */
object NavigationVoice {
    const val CLAUDE_ENGINE="com.k2fsa.sherpa.onnx.tts.engine"
    const val CLAUDE_APK="https://huggingface.co/csukuangfj2/sherpa-onnx-apk/resolve/main/tts-engine-new/1.13.8/sherpa-onnx-1.13.8-arm64-v8a-spa-tts-engine-vits-piper-es_MX-claude-high.apk"
    private val main=Handler(Looper.getMainLooper())
    private val listeners=linkedSetOf<(String)->Unit>()
    private var context: Context?=null
    private var engine: TextToSpeech?=null
    private var ready=false
    private var engineName: String?=null
    private var initializing=false
    private var initialization=0
    private var pending: Request?=null
    private var active: Request?=null
    private var queued: Request?=null
    private var sequence=0L
    private var focus: AudioFocusRequest?=null
    private var status="Selecciona una voz y pulsa Probar voz."
    private data class Request(val id: Long,val text: String,val finish: Boolean,val config: VoiceConfig,val announcement: Boolean=false,val created: Long=android.os.SystemClock.elapsedRealtime())
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val idle=Runnable { release() }
    fun claudeInstalled(ctx: Context): Boolean = try { ctx.packageManager.getPackageInfo(CLAUDE_ENGINE,0);true } catch (_: Exception) { false }
    fun observe(listener: (String)->Unit) { listeners+=listener;listener(status) }
    fun remove(listener: (String)->Unit) { listeners-=listener }
    private fun status(text: String) { status=text;listeners.toList().forEach { it(text) } }
    private fun initialize(ctx: Context) {
        if (context!=null) return
        context=ctx.applicationContext
        androidx.core.content.ContextCompat.registerReceiver(ctx.applicationContext,object: android.content.BroadcastReceiver() {
            override fun onReceive(context: Context,intent: android.content.Intent) { cancel();release() }
        },android.content.IntentFilter(VoicePreferences.ACTION),androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }
    fun speak(ctx: Context,text: String,finish: Boolean=false,enqueue: Boolean=false) {
        if (Looper.myLooper()!=Looper.getMainLooper()) { main.post { speak(ctx,text,finish,enqueue) };return }
        initialize(ctx)
        val config=VoicePreferences.read(ctx)
        if (config.mode=="off" || text.isBlank()) { cancel();return }
        main.removeCallbacks(idle)
        val name=if (config.mode=="claude" && claudeInstalled(ctx)) CLAUDE_ENGINE else "android"
        if (engine!=null && name!=engineName) release()
        val request=Request(++sequence,text.take(500),finish,config,enqueue)
        if(enqueue && (active!=null || pending!=null)) { queued=request;return }
        if(!enqueue && active?.announcement==true) queued=active?.copy(id=++sequence)
        pending=request
        if (ready) { play();return }
        if (initializing) return
        initializing=true;engineName=name
        val generation=++initialization
        status(if(name==CLAUDE_ENGINE) "Cargando Claude High…" else if(config.mode=="claude") "Claude no está instalada; usando la voz Android." else "Cargando voz Android…")
        try {
            val listener=TextToSpeech.OnInitListener { result -> main.post {
                if (generation!=initialization) return@post
                initializing=false
                if (result!=TextToSpeech.SUCCESS) { failed("No se pudo iniciar la voz. Revisa el motor instalado.");return@post }
                val language=engine?.setLanguage(Locale("es","MX")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (language<0) { failed("El motor no tiene una voz en español disponible.");return@post }
                engine?.setAudioAttributes(attributes)
                engine?.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
                    override fun onStart(id: String?) { main.post { if(active?.id.toString()==id) status(if(engineName==CLAUDE_ENGINE) "Hablando con Claude High · sin conexión" else "Hablando con voz Android") } }
                    override fun onDone(id: String?) { main.post { completed(id,false) } }
                    @Deprecated("Android callback") override fun onError(id: String?) { main.post { completed(id,true) } }
                    override fun onError(id: String?,code: Int) { main.post { completed(id,true) } }
                    override fun onStop(id: String?,interrupted: Boolean) { main.post { if(active?.id.toString()==id) { active=null;abandonFocus() } } }
                })
                ready=true;play()
            } }
            engine=if(name==CLAUDE_ENGINE) TextToSpeech(ctx.applicationContext,listener,CLAUDE_ENGINE) else TextToSpeech(ctx.applicationContext,listener)
        } catch (_: Exception) { failed("No se pudo conectar con el motor de voz.") }
    }
    private fun play() {
        val request=pending ?: return
        pending=null
        val ctx=context ?: return
        engine?.stop();abandonFocus()
        val manager=ctx.getSystemService(AudioManager::class.java)
        val requestFocus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change -> if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) cancel() },main).build()
        if (manager.requestAudioFocus(requestFocus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { status("Voz pausada: otra aplicación está usando el audio.");return }
        focus=requestFocus;active=request
        engine?.setSpeechRate(request.config.speedPercent/100f)
        val result=engine?.speak(request.text,TextToSpeech.QUEUE_FLUSH,Bundle(),request.id.toString())
        if(result!=TextToSpeech.SUCCESS) completed(request.id.toString(),true)
    }
    private fun completed(id: String?,error: Boolean) {
        val request=active ?: return
        if(id!=request.id.toString()) return
        active=null;abandonFocus()
        status(if(error) "No se pudo reproducir la voz. Pulsa Probar voz para reintentar." else if(engineName==CLAUDE_ENGINE) "Claude High lista · sin conexión" else "Voz Android lista")
        val next=queued?.takeIf { !error && android.os.SystemClock.elapsedRealtime()-it.created<=30000 }
        queued=null
        if(next!=null) { pending=next;play() }
        else if(request.finish || error) release() else main.postDelayed(idle,300000)
    }
    private fun failed(message: String) { pending=null;release();status(message) }
    private fun abandonFocus() {
        focus?.let { context?.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) };focus=null
    }
    fun cancel(keepAnnouncements: Boolean=false) {
        if(Looper.myLooper()!=Looper.getMainLooper()) { main.post { cancel(keepAnnouncements) };return }
        if(keepAnnouncements && active?.announcement==true) return
        val preserved=if(keepAnnouncements) queued?.takeIf { it.announcement } ?: pending?.takeIf { it.announcement } else null
        sequence++;pending=preserved;queued=null;active=null
        try { engine?.stop() } catch (_: Exception) { }
        abandonFocus();main.removeCallbacks(idle)
        if(preserved!=null) { if(ready) play() } else main.postDelayed(idle,300000)
    }
    private fun release() {
        main.removeCallbacks(idle);initialization++;initializing=false;ready=false;pending=null;active=null;queued=null
        try { engine?.stop();engine?.shutdown() } catch (_: Exception) { }
        engine=null;engineName=null;abandonFocus()
    }
}
