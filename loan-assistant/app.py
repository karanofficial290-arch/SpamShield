"""
Loan Call Legal Assistant
-------------------------
Forward hui calls uthata hai aur:
  * Loan OFFER / sales call  -> politely mana, number hatane ko bolta hai, call khatam.
  * RECOVERY agent call      -> legal assistant ki tarah, RBI ke niyamon ke saath, sakhti
                                 aur izzat se baat; details leta hai, likhit communication maangta hai.
  * Dhamki / gaali / 8am-7pm ke bahar call -> turant Telegram alert.
Har call ka transcript + summary save hota hai (complaint mein saboot ke liye).

Stack: FastAPI + Twilio (voice) + Claude API
"""
import json
import os
import threading
from datetime import datetime
from zoneinfo import ZoneInfo
from xml.sax.saxutils import escape

import anthropic
import requests
from fastapi import FastAPI, Form, Request
from fastapi.responses import HTMLResponse, Response

# ---------- Settings (environment variables) ----------
OWNER_NAME = os.getenv("OWNER_NAME", "Sir")
ADVOCATE_NAME = os.getenv("ADVOCATE_NAME", "").strip()      # sirf tab bharo jab sach mein tumhara vakeel ho
ADVOCATE_CONTACT = os.getenv("ADVOCATE_CONTACT", "").strip()  # vakeel ka email/number (optional)
OWNER_EMAIL = os.getenv("OWNER_EMAIL", "").strip()            # likhit notice kahan bhejein (optional)
CLAUDE_MODEL = os.getenv("CLAUDE_MODEL", "claude-sonnet-5-5")
VOICE = os.getenv("TTS_VOICE", "Polly.Aditi")
LANG = os.getenv("SPEECH_LANG", "hi-IN")
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN")
TELEGRAM_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID")
TWILIO_SID = os.getenv("TWILIO_ACCOUNT_SID")
TWILIO_TOKEN = os.getenv("TWILIO_AUTH_TOKEN")
RECORD_CALLS = os.getenv("RECORD_CALLS", "1") == "1"
DASHBOARD_KEY = os.getenv("DASHBOARD_KEY", "change-me")
LOG_FILE = os.getenv("LOG_FILE", "calls.json")
MAX_TURNS = int(os.getenv("MAX_TURNS", "14"))
IST = ZoneInfo("Asia/Kolkata")

client = anthropic.Anthropic()
app = FastAPI()
calls: dict[str, dict] = {}
lock = threading.Lock()

advocate_line = (
    f"{OWNER_NAME} ke advocate {ADVOCATE_NAME} hain"
    + (f" (contact: {ADVOCATE_CONTACT})" if ADVOCATE_CONTACT else "")
    + ". Legal matter ho toh caller ko unse baat karne ko kaho."
    if ADVOCATE_NAME else
    f"{OWNER_NAME} ka koi advocate tumhe nahi bataya gaya hai, isliye kisi vakeel ka naam mat lo aur aisa mat kaho ki vakeel hai."
)
email_line = (f"Likhit notice/communication ke liye email: {OWNER_EMAIL}." if OWNER_EMAIL
              else "Likhit communication ke liye kaho ki registered address ya registered email pe official notice bhejein.")

SYSTEM_PROMPT = f"""Tum {OWNER_NAME} ke legal assistant ho, phone pe unki taraf se loan se judi calls sambhalte ho.

PEHCHAAN (bahut zaroori):
- Tum ek AI legal assistant ho. Khud ko kabhi advocate, vakeel, police, ya court official mat batao.
- Koi pooche "kya aap AI ho?" ya "kya aap vakeel ho?" toh sach batao: "Main {OWNER_NAME} ka AI legal assistant hoon."
- {advocate_line}

BAAT KARNE KA TAREEKA:
- Phone call hai: har jawab 1-3 chhote sentence, saaf Hindi/Hinglish. Koi markdown, list, emoji nahi.
- Tone: shant, izzatdaar, lekin firm aur confident — jaise ek samajhdar legal professional baat karta hai.
- Kabhi gaali, dhamki ya jhooth mat bolo. Caller bhadke toh bhi shant raho.

CALL KA TYPE PEHCHAANO:
A) LOAN OFFER / SALES ("loan approve hai", "credit card", "pre-approved"):
   Kaho {OWNER_NAME} interested nahi hain, unka number apni calling list se turant hata dein. Phir call khatam karo.

B) RECOVERY / COLLECTION CALL:
   1. Pehle details lo: caller ka poora naam, agency/company ka naam, kis bank/NBFC/app ki taraf se, loan account number, aur unka employee/agent ID.
   2. Batao ki yeh call record ho rahi hai.
   3. Zaroorat pade toh RBI ke recovery niyam yaad dilao (bina bhashan diye, ek-ek point):
      - Recovery agent sirf subah 8 baje se shaam 7 baje ke beech call kar sakte hain.
      - Dhamki, gaali, badtameezi, ya baar-baar pareshaan karna mana hai.
      - Rishtedaron, doston ya office walon ko call karke sharminda karna ya batana mana hai.
      - Agent ko apni pehchaan aur authorization dikhani hoti hai.
   4. Kaho ki koi bhi demand, outstanding amount ya settlement offer likhit mein bhejein. {email_line}
   5. Kaho ki {OWNER_NAME} ko yeh message pahuncha diya jayega aur woh likhit mein jawab denge.

   KABHI MAT KARO:
   - Loan ko jhutlana ya maanna — tumhe {OWNER_NAME} ke loan ki details nahi pata. Kaho "details likhit mein bhejiye, verify karke jawab diya jayega."
   - Payment ki koi date, amount ya waada.
   - {OWNER_NAME} ki koi bhi personal info dena: address, office, family, bank, OTP, Aadhaar, schedule.
   - Kanooni dhamki dena ya jhootha case batana.

C) DHAMKI / GAALI / SHARMINDA KARNE KI BAAT:
   Shant reh kar kaho: "Aapki yeh baat record ho chuki hai. Yeh RBI ke niyamon ke khilaaf hai aur iski shikayat aapke lender aur RBI mein ki ja sakti hai." Agar phir bhi na ruke toh call khatam karo.

D) KOI AUR (dost, family, zaroori kaam): naam, kaam aur callback number lo, kaho message pahuncha diya jayega.

Jab baat poori ho jaye, apne jawab ke bilkul end mein [END] likho."""


def now_ist() -> datetime:
    return datetime.now(IST)


def outside_allowed_hours(t: datetime) -> bool:
    return t.hour < 8 or t.hour >= 19


def twiml(body: str) -> Response:
    return Response(f'<?xml version="1.0" encoding="UTF-8"?><Response>{body}</Response>',
                    media_type="application/xml")


def say(text: str) -> str:
    return f'<Say voice="{VOICE}" language="{LANG}">{escape(text)}</Say>'


def gather(prompt: str) -> str:
    return (f'<Gather input="speech" language="{LANG}" speechTimeout="auto" '
            f'action="/respond" method="POST">{say(prompt)}</Gather>'
            f'{say("Hello? Kya aap line pe hain?")}<Redirect method="POST">/silence</Redirect>')


def telegram(text: str):
    if not (TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID):
        return
    try:
        requests.post(f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage",
                      json={"chat_id": TELEGRAM_CHAT_ID, "text": text}, timeout=10)
    except Exception as e:
        print("Telegram error:", e)


def start_recording(call_sid: str):
    """Twilio REST se poori call record karo (saboot ke liye)."""
    if not (RECORD_CALLS and TWILIO_SID and TWILIO_TOKEN):
        return
    try:
        requests.post(
            f"https://api.twilio.com/2010-04-01/Accounts/{TWILIO_SID}/Calls/{call_sid}/Recordings.json",
            auth=(TWILIO_SID, TWILIO_TOKEN), data={"RecordingChannels": "dual"}, timeout=10)
    except Exception as e:
        print("Recording error:", e)


def ask_claude(messages: list[dict], extra: str = "") -> str:
    try:
        r = client.messages.create(model=CLAUDE_MODEL, max_tokens=200,
                                   system=SYSTEM_PROMPT + extra, messages=messages)
        return r.content[0].text.strip()
    except Exception as e:
        print("Claude error:", e)
        return (f"Maaf kijiye, abhi {OWNER_NAME} available nahi hain. Kripya apni baat likhit mein "
                f"official channel se bhejiye. Dhanyavaad. [END]")


THREAT_WORDS = ["ghar aa", "ghar pe aa", "uthwa", "jail", "police bhej", "maar", "dekh lunga", "dekh lenge",
                "photo viral", "contacts ko", "sab ko bata", "badnaam", "family ko", "office mein bata"]


def looks_threatening(text: str) -> bool:
    t = text.lower()
    return any(w in t for w in THREAT_WORDS)


@app.post("/voice")
async def voice(CallSid: str = Form(...), From: str = Form("unknown")):
    t = now_ist()
    late = outside_allowed_hours(t)
    greeting = (f"Namaste, main {OWNER_NAME} ka legal assistant bol raha hoon. Yeh call record ki ja rahi hai. "
                "Kripya apna naam, company aur call karne ki wajah batayein.")
    with lock:
        calls[CallSid] = {"from": From, "start": t.isoformat(timespec="seconds"), "late": late,
                          "alerted": False,
                          "messages": [{"role": "user", "content": "(call connected)"},
                                       {"role": "assistant", "content": greeting}]}
    threading.Thread(target=start_recording, args=(CallSid,), daemon=True).start()
    return twiml(gather(greeting))


@app.post("/respond")
async def respond(CallSid: str = Form(...), SpeechResult: str = Form("")):
    with lock:
        call = calls.setdefault(CallSid, {"from": "unknown", "messages": [], "late": False, "alerted": False})
        call["messages"].append({"role": "user", "content": SpeechResult or "(awaaz saaf nahi aayi)"})
        msgs = list(call["messages"])
        turns = sum(1 for m in msgs if m["role"] == "user")
        late = call.get("late")

    extra = ""
    if late:
        extra = (f"\n\nNOTE: Yeh call {call['start'][11:16]} baje aayi hai, jo 8am-7pm ke bahar hai. "
                 "Agar yeh recovery call hai toh shant tareeke se bata do ki is samay recovery call karna "
                 "RBI ke niyamon ke khilaaf hai.")

    if SpeechResult and looks_threatening(SpeechResult):
        with lock:
            first = not call["alerted"]
            call["alerted"] = True
        if first:
            telegram(f"⚠️ DHAMKI ALERT\nNumber: {call['from']}\nCaller ne kaha: \"{SpeechResult}\"\n"
                     "Call record ho rahi hai. Baad mein summary aayegi.")

    reply = ask_claude(msgs, extra)
    done = "[END]" in reply or turns >= MAX_TURNS
    reply = reply.replace("[END]", "").strip() or "Theek hai, dhanyavaad."

    with lock:
        call["messages"].append({"role": "assistant", "content": reply})

    if done:
        return twiml(say(reply) + "<Hangup/>")
    return twiml(gather(reply))


@app.post("/silence")
async def silence():
    return twiml(say("Koi jawab nahi mila. Dhanyavaad.") + "<Hangup/>")


@app.post("/status")
async def status(CallSid: str = Form(...), CallStatus: str = Form(""), CallDuration: str = Form("0")):
    if CallStatus in ("completed", "busy", "failed", "no-answer", "canceled"):
        with lock:
            call = calls.pop(CallSid, None)
        if call:
            call["duration_sec"] = int(CallDuration or 0)
            call["sid"] = CallSid
            threading.Thread(target=finish_call, args=(call,), daemon=True).start()
    return Response(status_code=204)


SUMMARY_PROMPT = """Neeche ek phone call ka transcript hai jo ek legal assistant ne uthaya. Is format mein jawab do, kuch aur nahi:
TYPE: OFFER / RECOVERY / DHAMKI / ZAROORI / OTHER (ek chuno)
CALLER: naam, company, lender, agent ID (jo pata chala, warna "nahi bataya")
LOAN: account number / amount jo bataya (warna "nahi bataya")
KYA KAHA: 1-2 line
NIYAM TODE: RBI recovery niyamon ka koi ulanghan (dhamki, gaali, rishtedaron ko batane ki baat, samay) — warna "koi nahi"
AGLA KADAM: owner ko kya karna chahiye, 1 line"""


def finish_call(call: dict):
    transcript = "\n".join(f"{'Caller' if m['role'] == 'user' else 'Assistant'}: {m['content']}"
                           for m in call["messages"][1:])
    late_note = " (8am-7pm ke BAHAR)" if call.get("late") else ""
    try:
        r = client.messages.create(model=CLAUDE_MODEL, max_tokens=300, messages=[{"role": "user", "content":
                                   f"{SUMMARY_PROMPT}\n\nCall ka samay: {call.get('start')}{late_note}\n\n{transcript}"}])
        summary = r.content[0].text.strip()
    except Exception as e:
        summary = f"(summary nahi ban paya: {e})"

    record = {"sid": call.get("sid"), "from": call["from"], "start": call.get("start"), "late": call.get("late"),
              "duration_sec": call["duration_sec"], "summary": summary, "transcript": transcript}
    with lock:
        data = []
        if os.path.exists(LOG_FILE):
            with open(LOG_FILE, encoding="utf-8") as f:
                data = json.load(f)
        data.insert(0, record)
        with open(LOG_FILE, "w", encoding="utf-8") as f:
            json.dump(data[:1000], f, ensure_ascii=False, indent=2)

    telegram(f"📞 {call['from']} · {call.get('start', '')[:16].replace('T', ' ')}{late_note} · "
             f"{call['duration_sec']}s\n{summary}")


@app.get("/calls", response_class=HTMLResponse)
async def dashboard(request: Request, key: str = ""):
    if key != DASHBOARD_KEY:
        return HTMLResponse("Not allowed", status_code=403)
    data = []
    if os.path.exists(LOG_FILE):
        with open(LOG_FILE, encoding="utf-8") as f:
            data = json.load(f)
    rows = "".join(
        f"<details{' class=late' if c.get('late') else ''}><summary><b>{escape(c['from'])}</b> · "
        f"{escape((c.get('start') or '')[:16].replace('T', ' '))}{' · ⏰ 8-7 ke bahar' if c.get('late') else ''}"
        f" · {c['duration_sec']}s<pre class=s>{escape(c['summary'])}</pre></summary>"
        f"<pre>{escape(c['transcript'])}</pre></details>" for c in data) or "<p>Abhi koi call nahi.</p>"
    return f"""<!doctype html><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>Loan Call Log</title><style>body{{font-family:sans-serif;margin:16px;max-width:760px}}
details{{border:1px solid #ccc;border-radius:10px;padding:10px;margin:10px 0}} details.late{{border-color:#d93025}}
pre{{white-space:pre-wrap;font-size:13px;margin:6px 0}} pre.s{{background:#f5f5f5;padding:8px;border-radius:8px}}</style>
<h2>⚖️ Loan Call Log</h2><p>Tap karke poori baatcheet dekho. Recordings Twilio Console → Monitor → Recordings mein hain.</p>{rows}"""


@app.get("/")
async def health():
    return {"ok": True}
