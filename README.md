# 🛡️ Spam Shield

Loan app, telemarketing aur spam calls ko **ring hone se pehle hi kaat do**.
Block hui call na screen pe aati hai, na ring hoti hai, na missed-call notification aata hai.

Android 10 ya usse naye phone pe chalta hai. Koi internet nahi, koi ads nahi, aapka data phone se bahar nahi jata.

## Kya kya block kar sakta hai
- **140xxxxxxx** wale telemarketing number (loan / credit card / insurance promotion)
- Hidden / private number
- International number (+91 ke alawa)
- **Saare anjaan number** – sirf contacts wale call kar payenge (loan recovery wale roz naya number badalte hain, uske liye yahi sabse kaam ka hai)
- Apni blocklist – pura number ya prefix, jaise `98765*`
- Contacts wale aur "Hamesha allow" list wale number **kabhi block nahi hote**
- Emergency option: koi anjaan number 5 minute me 2 baar call kare to doosri call aa jati hai

## APK kaise milega (GitHub pe)
1. GitHub pe naya repository banao (jaise `SpamShield`).
2. Is folder ki saari files upload karo (`.github` folder bhi – woh zaroori hai).
   - Website se: repo me **Add file → Upload files**, saari files/folders drag karo, **Commit** dabao.
3. **Actions** tab kholo – "Build APK" chalega (3–5 minute).
4. Ban jaane ke baad repo ke **Releases** section me `SpamShield.apk` milega. Phone pe download karo.

## Phone me setup
1. APK install karo (Chrome "unknown apps" permission maange to allow karo).
2. App kholo → **Shield ON karo** → *Spam Shield* ko "Caller ID & spam app" chuno.
3. **Contacts permission** do.
4. Jo block karna hai uske switch ON karo.

> Note: Agar Truecaller jaisa koi aur app "Caller ID & spam app" bana hua hai, to ek time pe ek hi chalega. Settings → Apps → Default apps → Caller ID & spam app me Spam Shield chuno.
