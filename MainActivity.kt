package com.printbiz.telecaller

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private lateinit var keyBox: EditText
    private lateinit var bizBox: EditText
    private lateinit var leadsBox: EditText
    private lateinit var logView: TextView
    private val perms = arrayOf(
        Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.READ_PHONE_STATE, Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sp = getSharedPreferences("cfg", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 32, 32, 32) }

        fun label(t: String) = TextView(this).apply { text = t; textSize = 13f; setPadding(0, 16, 0, 0) }
        keyBox = EditText(this).apply { hint = "Gemini API key"; setText(sp.getString("key", "")); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        bizBox = EditText(this).apply { hint = "Business name"; setText(sp.getString("biz", "Apna Printing Press")) }
        leadsBox = EditText(this).apply {
            hint = "+919999999999,Rahul,follow_up,business card quote\n+918888888888,Priya,payment,2000 baaki"
            setText(sp.getString("leads", "")); minLines = 4; gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val start = Button(this).apply { text = "START CALLS" }
        val stop = Button(this).apply { text = "STOP" }
        logView = TextView(this).apply { textSize = 12f }

        root.addView(label("Gemini API key (aistudio.google.com/apikey, free)")); root.addView(keyBox)
        root.addView(label("Business name")); root.addView(bizBox)
        root.addView(label("Numbers: number,naam,purpose(follow_up/payment/ready),note  (ek line = ek call)")); root.addView(leadsBox)
        root.addView(start); root.addView(stop)
        root.addView(logView)
        setContentView(ScrollView(this).apply { addView(root) })

        CallService.sink = { line -> runOnUiThread { logView.append(line + "\n") } }

        start.setOnClickListener {
            if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
                requestPermissions(perms, 1); return@setOnClickListener
            }
            sp.edit().putString("key", keyBox.text.toString()).putString("biz", bizBox.text.toString())
                .putString("leads", leadsBox.text.toString()).apply()
            val i = Intent(this, CallService::class.java)
                .putExtra("key", keyBox.text.toString().trim())
                .putExtra("biz", bizBox.text.toString().trim())
                .putExtra("leads", leadsBox.text.toString())
            startForegroundService(i)
        }
        stop.setOnClickListener { startService(Intent(this, CallService::class.java).setAction("STOP")) }
    }

    override fun onDestroy() { CallService.sink = null; super.onDestroy() }
}
