# Owner Guide: what you do and how to do it

You only do the **physical** things: phone, Telegram, voice recordings, photos of the site. Everything technical (code, tests, measurements, uploads) is done by the coding assistant.

**How this works**
1. The assistant tells you which task to do, for example "please do Task A".
2. Follow the numbered steps. Each step says exactly what to tap.
3. Reply with "done", or send the screenshot the task asks for. If anything looks different from these steps, send a screenshot of what you see. That's always fine.

You will **never** need to type commands, read code, or review technical changes.

---

## Task A: Let the computer talk to the Moto G54 (about 10 minutes)

*Why: so the assistant can install test versions of the app and check what the phone and its camera can do. Nothing on your phone is deleted or changed, and you can switch this off again at any time.*

1. Charge the phone above 50% and connect it to Wi-Fi.
2. Open **Settings** → scroll to the bottom → **About phone**.
3. **Take a screenshot** of this screen: press the **Power** and **Volume Down** buttons together. Send the screenshot to the assistant.
4. On the same screen, find **Build number**. On Motorola phones it may be under **About phone → Software information**, or at the very bottom.
5. **Tap "Build number" 7 times quickly.** The phone may ask for your screen-lock PIN; enter it. You'll see the message **"You are now a developer!"**
6. Go back to **Settings → System → Developer options**.
7. Find **USB debugging** and switch it **on**. Tap **OK** on the warning.
8. Connect the phone to **this computer** with a USB cable. Use the cable that came with the phone; some cheap cables only charge.
9. A box appears on the phone: **"Allow USB debugging?"** Tick **"Always allow from this computer"** and tap **Allow**.
   - If the phone asks what the USB connection is for, choose **"File transfer"**.
10. Tell the assistant **"phone connected"**. Leave the phone plugged in, with the screen on, while the assistant works (about 30–40 minutes).

**Done looks like:** the assistant confirms it can see the phone.

**To undo later (optional):** Settings → System → Developer options → switch **USB debugging** off.

---

## Task B: Create the Telegram alert bot (about 10 minutes)

*Why: this is the "robot" account that will post dumping photos into your group.*

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

## Task C: Record the warning voice (about 20 minutes)

*Why: the phone will play this loudly through the speaker when someone dumps waste.*

**Who speaks:** you, or a volunteer **who agrees** that their voice will be played in public. A clear, firm voice works best.

**Where:** a quiet room with the fan and TV off.

**How:**
1. On the Moto G54, open a voice recorder app. Moto's **Recorder** app works; any recorder is fine.
2. Hold the phone about a hand's length (20 cm) from your mouth. Speak **slowly, loudly and clearly**.
3. Record each line below as a **separate recording**. Do 2–3 attempts of each and keep the best one.
4. Rename each recording exactly as shown in the first column. If renaming is hard, just tell the assistant the order you recorded them in.

| Name the recording | Say this |
| --- | --- |
| `warning-english` | "Attention. Illegal dumping detected. This area is under camera surveillance. Your vehicle is being recorded. Please take your waste back." |
| `warning-kannada` | "ಗಮನಿಸಿ. ಅಕ್ರಮವಾಗಿ ಕಸ ಎಸೆಯುವುದು ಪತ್ತೆಯಾಗಿದೆ. ಈ ಪ್ರದೇಶವು ಕ್ಯಾಮೆರಾ ಕಣ್ಗಾವಲಿನಲ್ಲಿದೆ. ನಿಮ್ಮ ವಾಹನವನ್ನು ದಾಖಲಿಸಲಾಗುತ್ತಿದೆ. ದಯವಿಟ್ಟು ನಿಮ್ಮ ಕಸವನ್ನು ವಾಪಸ್ ತೆಗೆದುಕೊಂಡು ಹೋಗಿ." |
| `notice-english` | "This area is under camera surveillance." |
| `notice-kannada` | "ಈ ಪ್ರದೇಶವು ಕ್ಯಾಮೆರಾ ಕಣ್ಗಾವಲಿನಲ್ಲಿದೆ." |

> The Kannada lines were written by the assistant. **Ask a Kannada speaker to check the wording**, and change it if it sounds unnatural; say whatever sounds natural locally.

**Done looks like:** 4 recordings saved on the phone. You don't need to send them anywhere. The assistant copies them off the phone the next time it's connected (Task A).

---

## Task D: Site visit (about 30 minutes, spread over one day)

*Why: so the assistant can plan where the camera looks and whether the light is good enough.*

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
5. Keep the photos on the phone; the assistant copies them when the phone is connected. **The photos stay private** and are never uploaded to GitHub.

**Done looks like:** 3–4 photos on the phone, plus your message with the distance and the four answers.

---

## Later tasks (the assistant will send these when it's time)

- **Task E:** install the first test version of the app and draw the "dump zone" on the screen.
- **Task F:** a test walk. A volunteer drops a bag and leaves, while you check that the siren and Telegram photo arrive.
- **Task G:** leave the phone running for 3 hours while plugged in.
- **Task H:** mount the phone and the speaker at the real site.

Each one comes with the same kind of step-by-step instructions.
