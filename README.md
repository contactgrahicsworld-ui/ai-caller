# AI Telecaller - Android app (free, phone-only, EXPERIMENTAL)

Sirf phone + SIM. Koi external device nahi, koi paid service nahi (Gemini free key + phone ka apna Hindi TTS/STT).

## Build (Android Studio, ek baar)
1. Android Studio install karo -> **Open** -> is folder (`ai-telecaller-android`) ko chuno -> Gradle sync hone do.
2. Phone me USB debugging ON karke jodo -> **Run ▶**. (Ya Build > Build APK, phir APK phone me install.)
3. Phone me **Google app** + **Hindi voice (Settings > Language > Text-to-speech / Google Speech Services me Hindi)** installed hona chahiye.

## Use
1. App kholo, Gemini key (aistudio.google.com/apikey) daalo.
2. Numbers likho, ek line = ek call:  `+919999999999,Rahul,follow_up,business card quote`
   purpose: `follow_up` / `payment` / `ready`
3. Saare permissions Allow karo, phir **START CALLS**. App ek-ek karke call lagayegi, speaker on karegi, customer ke "hello" ke baad AI Hindi me baat karega. Transcript app me dikhta hai (aur `Android/data/com.printbiz.telecaller/files/calls.log`).

## Test kaise karo
Phone khud ko call nahi kar sakta. **Dusre number (family/dusra phone)** ko list me daalo.

## Experimental kyun? (seedhi baat)
- Android SIM-call ki audio app ko nahi deta. Isliye app speaker on karke apne hi mic/speaker se kaam chalati hai.
- **Kai phones call ke dauran app ko mic dete hi nahi (silence)** -> AI sunega nahi. Ye phone/ROM pe depend karta hai (Samsung/Xiaomi/Realme alag behave karte hain). Pehle ek test call se dekho.
- Telephony ka echo-cancel kabhi AI ki awaaz customer tak dheemi/kati hui pahunchata hai. Volume full rakho, phone ko haath me ya table pe rakho, mic dhaka na ho.
- Ringing ke time tone ko app "awaaz" samajh sakti hai; code customer ka pehla non-empty "hello" aane tak sunta rehta hai.
- Din me 30-50 calls se zyada nahi; sirf apne existing customers. Bulk calls se number spam flag ho sakta hai, aur promotional calls pe TRAI DLT/DND rules lagte hain.
