package com.printbiz.telecaller

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

data class Lead(val phone: String, val name: String, val purpose: String, val note: String)

@SuppressLint("MissingPermission")
class CallService : Service() {
    companion object { @Volatile var sink: ((String) -> Unit)? = null }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var tts: TextToSpeech? = null
    private val model = "gemini-2.0-flash"
    private lateinit var am: AudioManager
    private lateinit var tm: TelephonyManager
    private lateinit var tc: TelecomManager

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int {
        if (i?.action == "STOP") { job?.cancel(); log("⏹ Stop"); stopSelf(); return START_NOT_STICKY }
        val ch = "tc"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(ch, "Telecaller", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch).setContentTitle("AI Telecaller chal raha hai")
            .setSmallIcon(android.R.drawable.sym_action_call).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)

        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        tc = getSystemService(Context.TELECOM_SERVICE) as TelecomManager

        val key = i?.getStringExtra("key") ?: ""
        val biz = i?.getStringExtra("biz") ?: "Apna Printing Press"
        val leads = (i?.getStringExtra("leads") ?: "").lines().mapNotNull { l ->
            val p = l.split(",").map { it.trim() }
            if (p[0].isBlank()) null else Lead(p[0], p.getOrElse(1) { "" }, p.getOrElse(2) { "follow_up" }, p.getOrElse(3) { "" })
        }
        job?.cancel()
        job = scope.launch {
            try {
                initTts()
                for (l in leads) { runCall(l, key, biz); delay(15_000) }
                log("✅ Sab calls khatam")
            } catch (e: CancellationException) { endCall() } catch (e: Exception) { log("Error: $e") }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() { job?.cancel(); tts?.shutdown(); super.onDestroy() }

    // ---------- helpers ----------
    private fun log(s: String) { sink?.invoke(s); try { File(getExternalFilesDir(null), "calls.log").appendText(s + "\n") } catch (_: Exception) {} }
    private fun state() = tm.callState                      // 0 idle, 1 ringing, 2 offhook
    private fun endCall() { @Suppress("DEPRECATION") try { tc.endCall() } catch (_: Exception) {} }

    private suspend fun initTts() = suspendCancellableCoroutine<Unit> { c ->
        tts = TextToSpeech(this) { st ->
            tts?.language = Locale("hi", "IN")
            tts?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            if (c.isActive) c.resume(Unit)
        }
    }

    private suspend fun speak(text: String) {
        val t = text.replace("[END]", "").trim(); log("AI  : $t")
        am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
        suspendCancellableCoroutine<Unit> { c ->
            val id = System.nanoTime().toString()
            tts!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(u: String?) {}
                override fun onDone(u: String?) { if (c.isActive) c.resume(Unit) }
                @Deprecated("x") override fun onError(u: String?) { if (c.isActive) c.resume(Unit) }
            })
            tts!!.speak(t, TextToSpeech.QUEUE_FLUSH, null, id)
            c.invokeOnCancellation { tts?.stop() }
        }
        delay(400)
    }

    private suspend fun listen(): String? = suspendCancellableCoroutine { c ->
        val rec = SpeechRecognizer.createSpeechRecognizer(this)
        fun done(r: String?) { if (c.isActive) c.resume(r); rec.destroy() }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) { done(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()) }
            override fun onError(e: Int) { done(null) }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        rec.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
        })
        c.invokeOnCancellation { rec.destroy() }
    }

    // ---------- one call ----------
    private suspend fun runCall(l: Lead, key: String, biz: String) {
        log("\n📞 ${l.name} ${l.phone}")
        val ex = Bundle().apply { putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true) }
        tc.placeCall(Uri.fromParts("tel", l.phone, null), ex)

        var waited = 0
        while (state() != 2 && waited < 20_000) { delay(500); waited += 500 }
        if (state() != 2) { log("call nahi lagi"); return }
        delay(1500); am.isSpeakerphoneOn = true

        // customer ke pehle "hello" ka wait
        var heard: String? = null
        val t0 = System.currentTimeMillis()
        while (heard == null && System.currentTimeMillis() - t0 < 45_000) {
            if (state() == 0) { log("call cut"); return }
            heard = listen()?.takeIf { it.isNotBlank() }
        }
        if (heard == null) { log("no answer"); endCall(); return }
        log("USER: $heard")

        val hello = "नमस्ते ${l.name} जी, मैं $biz से बोल रही हूँ। क्या अभी दो मिनट बात हो सकती है?"
        speak(hello)
        val hist = mutableListOf("user" to "(call uthi)", "model" to hello)
        repeat(8) {
            if (state() == 0) return
            val u = listen()?.takeIf { it.isNotBlank() } ?: return@repeat
            log("USER: $u"); hist += "user" to u
            val ai = gemini(key, sysPrompt(l, biz), hist)
            hist += "model" to ai
            speak(ai)
            if ("[END]" in ai) { endCall(); return }
        }
        endCall()
    }

    private fun sysPrompt(l: Lead, biz: String): String {
        val purpose = when (l.purpose) {
            "payment" -> "Payment baaki hai. Politely yaad dilao, poochho kab tak pay karenge. Dabav mat banao."
            "ready" -> "Order ready hai. Batao ki pickup/delivery kab convenient hai."
            else -> "Customer ne pehle quote/enquiry li thi. Poochho ki kya order final karna hai, aur kab tak."
        }
        return """Tum $biz (printing: books, pamphlets, business cards, logo design) ki taraf se phone pe baat kar rahe ho.
Customer: ${l.name}. Maksad: $purpose
Extra info: ${l.note}
Rules:
- Jawab Devanagari Hindi me do (English words jaise order, payment, quote waise hi). 1-2 chhote vakya, max 25 shabd.
- Ek baar me ek hi sawal poocho.
- Rate/discount khud promise mat karo; bolo "owner se confirm karwa ke batata hoon".
- Customer interested nahi / DND maange / gussa ho: maafi maango, baat khatam karo.
- Baat khatam karni ho to aakhir me [END] likho. Callback ya owner se baat maange to bolo owner call karenge aur [END] likho."""
    }

    private suspend fun gemini(key: String, sys: String, hist: List<Pair<String, String>>): String = withContext(Dispatchers.IO) {
        try {
            val contents = JSONArray()
            hist.forEach { (r, t) -> contents.put(JSONObject().put("role", r).put("parts", JSONArray().put(JSONObject().put("text", t)))) }
            val body = JSONObject()
                .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
                .put("contents", contents)
                .put("generationConfig", JSONObject().put("temperature", 0.6).put("maxOutputTokens", 120))
            val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
                .openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 8000; c.readTimeout = 10000
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
        } catch (e: Exception) {
            "माफ़ कीजिए, line me problem hai. Owner aapko wapas call karenge। धन्यवाद! [END]"
        }
    }
}
