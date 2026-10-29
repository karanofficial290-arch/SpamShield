# ⚖️ Loan Call Legal Assistant — Setup

Loan wali calls tera AI legal assistant uthayega:

| Call | Assistant kya karega |
|---|---|
| **Loan offer / sales** | Mana karega, number list se hatane ko bolega, call khatam |
| **Recovery agent** | Naam, company, lender, account no., agent ID lega · bolega call record ho rahi hai · RBI ke niyam yaad dilayega · likhit notice maangega |
| **Dhamki / gaali** | Shant reh ke bolega ki yeh record ho gaya aur niyamon ke khilaaf hai · **tujhe turant Telegram alert** |
| **Subah 8 se pehle / shaam 7 ke baad** | Bolega ki is samay recovery call karna niyam ke khilaaf hai · log mein laal mark |
| **Koi aur** | Naam, kaam, callback number note karega |

Har call ke baad Telegram pe summary aayegi: TYPE, CALLER, LOAN, KYA KAHA, NIYAM TODE, AGLA KADAM.
Saari calls ka record `/calls` page pe, aur audio recordings Twilio mein — **RBI / lender ko complaint mein saboot**.

### Assistant kya NAHI karega
- Khud ko vakeel nahi batayega (poochne pe bolega "main AI legal assistant hoon"). Jhooth mein vakeel banna tere liye kanooni pareshani ban sakta hai.
- Loan ko na jhutlayega, na maanega. Na koi payment date ya waada karega.
- Tera address, office, family, bank, OTP kuch nahi batayega.
- Asli vakeel ho toh `.env` mein `ADVOCATE_NAME` daal de, assistant caller ko unse baat karne ko bolega.

---

## Step 1: Accounts
1. **Claude API key**: console.anthropic.com
2. **Twilio**: twilio.com → ek voice number lo. Indian number lena (KYC lagega), warna forwarding international charge hogi.
   Console home pe **Account SID** aur **Auth Token** milenge (recording ke liye).
3. **Telegram bot** (recommended): @BotFather se bot banao → token; @userinfobot se chat ID. Bot ko ek baar "hi" bhej dena.

## Step 2: Server deploy (Render.com)
1. Yeh folder GitHub repo mein daalo.
2. Render → New Web Service → repo.
3. Start command: `uvicorn app:app --host 0.0.0.0 --port $PORT`
4. `.env.example` ki values Environment Variables mein daalo.
5. URL milega, jaise `https://loan-assistant.onrender.com`

> Note: Render ka free plan thodi der baad so jaata hai aur pehli call pe 30-50 sec late uthta hai.
> Calls theek se uthani hain toh sabse sasta paid plan lena behtar hai.

## Step 3: Twilio jodo
Twilio Console → Phone Numbers → tera number → Voice Configuration:
- **A call comes in** → Webhook → `https://TERA-URL/voice` (POST)
- **Call status changes** → `https://TERA-URL/status` (POST)

## Step 4: Phone pe forwarding
| Kab | Dial karo |
|---|---|
| Call **reject** karo / busy | `**67*+91TWILIO_NUMBER#` |
| **Na uthao** (20 sec) | `**61*+91TWILIO_NUMBER**20#` |
| Phone band / network nahi | `**62*+91TWILIO_NUMBER#` |

Band karna: `##67#`, `##61#`, `##62#`

Ab loan wali call aaye toh **reject** daba de — assistant sambhal lega.

## Step 5: Record dekho
- Telegram pe har call ki summary + dhamki alert
- Browser: `https://TERA-URL/calls?key=TERA_DASHBOARD_KEY`
- Audio: Twilio Console → Monitor → Recordings

## Harassment ho toh complaint (free)
1. Pehle lender (bank/NBFC/app) ke **Grievance Officer** ko likhit complaint, saath mein call summary/recording.
2. 30 din mein hal na ho → **RBI**: cms.rbi.org.in
3. Gair-kanooni loan app / photo-contacts leak ki dhamki → **1930** ya **cybercrime.gov.in**
4. Seedhi jaan-maal ki dhamki → **112** / nazdeeki police station.

*Yeh legal salah nahi hai — bada mamla ho toh asli advocate se zaroor milna.*

## Kharcha
Twilio number + per-minute, aur Claude API per call. `CLAUDE_MODEL=claude-haiku-5-5` set karo toh sasta padega
(par Sonnet zyada samajhdari se baat karta hai, isliye default wahi hai).
