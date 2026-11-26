# Owner Guide: what you do and how to do it

You only do the **physical** things: phone, Telegram, voice recordings, photos of the site. Everything technical (code, tests, measurements, uploads) is done by the coding assistant.

**How this works**

1. The assistant tells you which task to do, for example "please do Task A".
2. Follow the numbered steps. Each step says exactly what to tap.
3. Reply with "done", or send the screenshot the task asks for. If anything looks different from these steps, send a screenshot of what you see. That's always fine.

You will **never** need to type commands, read code, or review technical changes.

---

## Task A: Let the computer talk to the Moto G54 (about 10 minutes) ✅ done 2026-11-21

_Why: so the assistant can install test versions of the app and check what the phone and its camera can do. Nothing on your phone is deleted or changed, and you can switch this off again at any time._

1. Charge the phone above 50% and connect it to Wi-Fi.
2. Open **Settings** → scroll to the bottom → **About phone**.
3. **Take a screenshot** of this screen: press the **Power** and **Volume Down** buttons together. Send the screenshot to the assistant.
4. On the same screen, find **Build number**. On the Moto G54 it is inside **About phone → Device identifiers** (on other Motorola phones it may be under **Software information**, or at the very bottom).
5. **Tap "Build number" 7 times quickly.** The phone may ask for your screen-lock PIN; enter it. You'll see the message **"You are now a developer!"**
6. Go back to **Settings → System → Developer options**.
7. Find **USB debugging** and switch it **on**. Tap **OK** on the warning.
8. Connect the phone to **this computer** with a USB cable. Use the cable that came with the phone; some cheap cables only charge.
9. A box appears on the phone: **"Allow USB debugging?"** Tick **"Always allow from this computer"** and tap **Allow**.
    - If the phone asks what the USB connection is for, choose **"File transfer"**.
10. Tell the assistant **"phone connected"**. Leave the phone plugged in, with the screen on, while the assistant works (about 30–40 minutes).

**Done looks like:** the assistant confirms it can see the phone.

**If the assistant says it can't see the phone**, try these one at a time, and tell the assistant "tried again" after each:

1. Unplug the cable and plug it into a **different USB port** on the computer (for example, one on the other side).
2. Try **a different cable**, ideally the one that came in the Moto box. Many cables can charge but can't carry data.
3. Check that the cable is pushed **all the way in** at both ends; gently blow out any dust in the phone's charging port.
4. With the phone plugged in, swipe down from the top of the phone screen, tap the notification **"Charging this device via USB"**, and choose **"File transfer"**.
5. Unlock the phone and look for the **"Allow USB debugging?"** box; it only appears while the screen is on.

**To undo later (optional):** Settings → System → Developer options → switch **USB debugging** off.

---

## Task B: Create the Telegram alert bot (about 10 minutes) ✅ done 2026-11-22

_Why: this is the "robot" account that will post dumping photos into your group._

1. Open **Telegram** and search for **@BotFather**. Pick the one with the blue tick.
2. Tap **Start**, then send the message: `/newbot`
3. When it asks for a name, send: `LitterGuard Alerts`
4. When it asks for a username, send something that ends in **bot**, for example `litterguard_yourarea_bot`. If that name is taken, try another.
5. BotFather replies with a long code that looks like `1234567890:AAH...`. This is the **bot token**, a password for the bot.
    - **Copy it into a private note** on your phone.
    - **Do not send it to the assistant or anyone else.** You'll paste it into the LitterGuard app yourself later.
6. In Telegram, create a **new group** named `LitterGuard Test`. Add the bot to it (search for the bot's username when adding members).
7. In that group, send the message: `/start`

**Done looks like:** a group containing you and the bot. Tell the assistant "Telegram done". The app will find the group automatically later.

---

## Task C: Record the warning voice (about 15 minutes) ✅ done 2026-11-22

_Why: the phone will play this loudly through the speaker when someone dumps waste._

You only need this again if you want to re-record. The app uses **English only**.

**Who speaks:** you, or a volunteer **who agrees** that their voice will be played in public and published with the app. A clear, firm voice works best.

**Where:** a quiet room with the fan and TV off.

**How:**

1. On the Moto G54, open the **Recorder** app.
2. Hold the phone about a hand's length (20 cm) from your mouth. Speak **slowly, loudly and clearly**.
3. Record each line below as a **separate recording**. Do 2–3 attempts of each and keep the best one.
4. Rename each recording as shown in the first column.

| Name the recording | Say this |
| --- | --- |
| `Litterwarning` | "Attention. Illegal dumping detected. This area is under camera surveillance. Your vehicle is being recorded. Please take your waste back." |
| `LitterNotice` | "This area is under camera surveillance." |

**Done looks like:** 2 recordings saved on the phone. You don't need to send them anywhere. The assistant copies them off the phone and makes them louder and clearer.

---

## Task D: Site visit (about 30 minutes, spread over one day)

_Why: so the assistant can plan where the camera looks and whether the light is good enough._

1. Stand exactly where the phone will be mounted (balcony, window or security cabin).
2. With the **Moto G54**, take **one photo of the dumping spot at each of these times**:
    - daytime;
    - just after sunset;
    - at night (around 10 pm);
    - early morning (around 6 am), if you can. Dumping often happens then.
3. Count your **footsteps** from the mounting point to the middle of the dumping spot, or guess the distance in metres. Write the number down.
4. Answer these four questions in a message to the assistant:
    - Is there a **power socket** within reach of the mounting point?
    - Does **Wi-Fi** work there? (Open YouTube on the phone at that spot and check it plays.)
    - Is there a **street light** over the dumping spot at night?
    - Roughly **what times** does dumping usually happen?
5. **Check what the app can see from there** (do this once Task E is done; it takes 5 minutes):
    1. Put the phone where it will be mounted, held **sideways** and **completely still** (rest it on a ledge, or lean it against something).
    2. Open **LitterGuard** → **Change dump zone** (or **Draw dump zone**) → tap the corners of the dumping patch → **Save zone**.
    3. Tap **Start watching** and wait until it says **Watching**.
    4. Ask your friend to walk to the dumping spot and stand there. A box with the word **Person** should appear around them, with a **★** in front when their feet are inside the zone.
    5. **Take a screenshot** while the box is showing (**Power** + **Volume Down**). If no box appears, take a screenshot anyway; that is just as useful.
    6. If you can, repeat with a **scooter or car** at the spot.
    - ⚠️ The warning voice is **very loud**. If a bag is left in the zone, it will play. Tell people nearby that you are testing, or tap **Stop watching** when you finish.
    - Only photograph people who agree, such as your friend. Do **not** leave the app watching a public place unattended yet.
6. Keep the photos and screenshots on the phone; the assistant copies them when the phone is connected. **They stay private** and are never uploaded to GitHub.

**Done looks like:** 3–4 photos and 1–2 screenshots on the phone, plus your message with the distance and the four answers.

---

## Task E: Set up the first test version of the app (about 15 minutes)

_Why: to get the app working on your phone and rehearse before the demo._

**Before you start:** the assistant installs the app. For that, plug the G54 into this computer (Task A, steps 8 and 9) and tell the assistant "phone connected". When the assistant says the app is installed, continue. You can unplug the phone afterwards.

1. Open **LitterGuard**. The phone turns sideways for this app; hold it sideways.
2. Under **1. Camera**, tap **Allow camera**, then tap **While using the app**. If it also asks about **notifications**, tap **Allow**.
3. Stand the phone where it **cannot move** (a tripod, or leaning against books on a table or chair back), looking down at a clear patch of floor **3 to 5 steps away**. Use a bright room.
4. Under **2. Dump zone**, tap **Draw dump zone**. Tap **4 corners** of the floor patch, going around it. Tap **Save zone**.
5. Under **3. Warning sound**, tap **Test siren (loud)**. Your recorded warning should play. Tap **Stop siren** to stop it early.
6. Under **4. Telegram alerts**, tap **Set up Telegram**:
    1. Open your private note with the **bot token** (from Task B), copy it, and paste it into **Bot token**. **Only paste it into the app, never into the chat with the assistant.**
    2. Tap **Connect bot**. It should say **Bot connected**.
    3. Tap **Open Telegram**, choose your group **LitterGuard Test**, and confirm.
    4. Come back to LitterGuard and tap **Find my group**. It should show the group's name.
    5. Tap **Send test alert**. A message should appear in the group.
    - If it says **No group found yet**: in the Telegram group, send `/start@LitterGuardBot`, then tap **Find my group** again.
7. Tap **Back**.

**Done looks like:** all four steps on the first screen are ready, and the test message is in your Telegram group. Send the assistant a screenshot of the first screen.

---

## Task F: Rehearse, then run the demo (about 15 minutes)

_Why: so you know what the app does before you show it, and the assistant can fix anything that doesn't work._

**You need:** the phone set up as in Task E, a friend or volunteer (or do it yourself), and a **filled bag** whose colour stands out from the floor (for example a dark bag on a light floor), at least as big as a shopping bag.

**Get ready**

1. Charge the phone, or keep it plugged into its charger.
2. Make sure nothing in the zone will move on its own (curtains, a fan blowing on plants, pets).
3. Tap **Start watching**. Step out of the camera's view and wait until it says **Watching**.

**Scene 1: dumping (the main demo)**

4. Walk into the zone so your **feet** are inside it. Wait 2 seconds. Put the bag down.
5. Walk **out of the zone** without stopping, and don't stand between the camera and the bag.
6. Wait. The screen goes through **Checking what was left…** and **New object: checking it stays**, then **🚨 Dumping detected**. The warning should start about **7 seconds** after you leave, and a photo should arrive in Telegram soon after.

**Scene 2: just walking past (it must stay silent)**

7. **Wait one full minute**, then pick up the bag and walk away with it. Nothing should happen. (After an alert the app stays quiet for one minute on purpose, and taking something away is not dumping.) If it does sound, tell the assistant. Then tap **Stop watching**, **Start watching**, and wait for **Watching**.
8. Walk through the zone **carrying** the bag, without putting it down. Nothing should happen.

**Scene 3: changing your mind (it must stay silent)**

9. Walk in, put the bag down, pick it up again straight away, and walk out with it. Nothing should happen.

**Afterwards**

10. Tap **Stop watching**, then **Past incidents**. Open the incident, look at the pictures, and tap **Yes, this was dumping** or **False alarm**.

**Done looks like:** you tell the assistant, for each scene, whether it behaved as described. If it did not, send a screenshot and say roughly what time it happened (the time helps the assistant find it in the phone's records).

**If something goes wrong**

| What you see | Try this |
| --- | --- |
| No **Person** box appears | Add more light, or move the phone closer to the zone. |
| The box has no **★** | Your feet are outside the zone. Redraw the zone a bit bigger. |
| Nothing happens after leaving the bag | Use a bigger bag, or one whose colour stands out more. Leave the view completely. |
| **Unclear: the view changed too much** in Past incidents | The phone moved, or a light was switched on or off. Fix the phone firmly. |
| Anything else odd | Tap **Stop watching**, then **Start watching** again. |

**What to tell your friend this version can't do yet:** it saves photos but does not read number plates; it has not been tested at night, at the real distance, or for hours at a time; and it does not restart by itself if the phone reboots.

---

## Later tasks (the assistant will send these when it's time)

- **Task G:** leave the phone running for 3 hours while plugged in.
- **Task H:** mount the phone and the speaker at the real site.

Each one comes with the same kind of step-by-step instructions.
