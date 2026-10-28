# Ask: a voice assistant for your TV, on your own AI server

Press the mic button on the remote, say what you want, and the TV does it. Ask sends your words to your own
[LiteLLM](https://github.com/BerriAI/litellm) or any OpenAI-compatible server, with tools that act on the TV: it opens
apps, starts films, plays YouTube, switches inputs, searches the web and sets timers. The answer comes up as a
panel over whatever you're watching and is read aloud.

It replaces the TV's search on the mic button with an assistant that runs on a model you chose, on a server you own.

![Ask over a playing video: "Set the volume to 15 percent"](screenshot.jpg)

<sub>Over [Big Buck Bunny](https://peach.blender.org/), (c) Blender Foundation, CC BY 3.0.</sub>

## What you can say

| You say | What happens |
|---|---|
| "Put on The Office, season 3 episode 2" | Stremio opens at that episode's streams, and Ask gets out of the way: you pick with the remote |
| "Put on the movie in caption" | Inception. Misheard titles are corrected (see [Titles it mishears](#titles-it-mishears)) |
| "What was I watching?" / "Resume the last one" | Reads Stremio's Continue Watching, then opens where you stopped |
| "Play some lofi on YouTube" | Finds the video and plays it in SmartTube or YouTube |
| "Pause" · "Next" · "Skip back" | Controls whatever is playing, in any app |
| "Volume 20" · "Mute" | Sets the TV's volume |
| "Switch to HDMI 1" | Changes input (XGIMI's HDMI player included) |
| "Open Kodi" · "Go home" · "Open the Wi-Fi settings" | Opens the app or screen |
| "Who won the match last night?" | Searches the web through your SearXNG and answers in a sentence |
| "Read me the top story on that page" | Reads a page from the results, with a guard against private addresses |
| "Will it rain tomorrow in Lisbon?" | Open-Meteo, no key needed |
| "Remind me in 20 minutes to take the pizza out" | At that time a card comes up over the film and the TV says it out loud |
| "Turn it off in 45 minutes" | A sleep timer: the projector goes to standby then |
| "Send that recipe link to my phone" | An ntfy notification on your phone |
| "Remember we watch with subtitles on" | Kept on the TV and given to every later chat |
| "Bed time" · "It's movie night" | Runs a [routine](#routines) you made |
| "Start movie night" | Runs an n8n (or any) webhook you listed in the setup |
| "Use GLM" | Switches the model from the next question on |
| "What are the stream sizes?" · "What does that say?" | Reads the app behind the panel |
| "Click Settings" · "Press Episode 3" · "Play the second one" | Presses that button, tab or stream in the app behind the panel |
| "Go back" · "Turn it off" | Presses Back; puts the TV to sleep |

Everything else is ordinary chat with the model you picked: questions, a quick translation, a recipe, a bedtime
story. It answers in the language you speak to it in.

## How it feels

- **The panel sits over the film.** It covers the bottom of the screen and the picture stays visible above it.
  While you speak, the film pauses so the microphone hears you. While Ask answers, the sound is turned down
  instead.
- **It listens as soon as it opens.** A short tone means it's listening, and your words appear as you say them.
  The orb follows your voice.
- **Answers stream in.** Each sentence is read aloud as soon as it's complete, so the voice starts before the
  answer is finished. Tool steps show as small chips ("Searching · …", "Stremio · …") above the answer.
- **It gets out of the way.** When a tool opens something (a film, an app, an input), the panel closes without a
  word: the screen already shows what happened. Opened with the mic button over an app, it closes by itself a few
  seconds after the answer. If the answer ends with a question, it listens again for your reply.

| Key | In Ask |
|---|---|
| Mic button / OK | Talk. While it's listening: stop and send. While it's talking: cut in. |
| Back | Stop talking or listening, then close |
| ▲ | New chat · Model · Voice on/off · Setup |
| Menu | New chat |
| Channel/Page ▲▼ | Scroll the conversation |

## Routines

A routine is a name and the request it stands for, in plain words. Saying the name does all of it:

> "Make a routine called bed time: play my sleep playlist https://youtube.com/playlist?list=… at volume 12 and turn
> the TV off in 90 minutes."

Then "bed time", "it's bed time" or "run bed time" plays the playlist, sets the volume and starts the sleep timer.
The name is matched before the model sees anything, so a routine runs the same way every time it's said. A
sentence that only mentions it ("what time is bed time usually?") is an ordinary question. The steps run through
the same tools as anything you'd say, so anything Ask can do can be in a routine: an app, a Stremio title, an
input, a webhook, a reminder, a message to your phone.

| Make and manage | How |
|---|---|
| By voice | "Make a routine called …: …", "what routines do I have?", "delete the bed time routine" |
| On the phone setup page | One per line: `bed time: play … and turn the TV off in 90 minutes` |
| A playlist of your own | Put its YouTube link in the steps; links are opened as they are, not searched |

## Titles it mishears

The TV's recognizer turns film names into words: "in caption", "breaking bat". Asking for something to watch goes
through three checks before anything opens:

1. The model guesses the real title and passes it with the words exactly as heard.
2. Both are matched loosely (edit distance, letters only, and a rough sound key) against Cinemeta's catalogue,
   against an IMDb search through your SearXNG (whose engines correct spelling), and against your Continue
   Watching. Candidates are scored by how well they explain what was heard; the model's guess only adds a smaller
   weight, so it can't talk its way past your words. For "season 2 episode 10", shows without that episode drop
   out.
3. A clear winner opens. Otherwise Ask names two or three and asks.

| Heard | Opened |
|---|---|
| "put on the movie in caption" | Inception (2010) |

## Setup

### On the TV (no computer needed after install)

1. Install it (below) and open **Ask** from Home. The first launch shows a QR code and a PIN.
2. Scan the code with your phone (same Wi-Fi). Paste your LiteLLM address and key, and optionally your SearXNG,
   ntfy and webhooks. The TV checks the key right away and the phone shows the result.
3. Pick the default model with the remote. It's whatever your server has; nothing is hardcoded.
4. Allow the microphone when the TV asks.
5. Make Ask the TV's assistant, so the mic button opens it from any app (`VOICE_KEY=1` below, or the command the
   TV shows). The first press may ask which app to use: pick **Ask**, then **Always**.

The phone page is served by the TV only while the setup screen is open, on your home network only, and it needs
the PIN. Five wrong PINs make a new one.

### From a computer

```bash
TV_HOST=<tv ip> addons/ask-assistant/install.sh                 # build, install, grant; set up on the TV

TV_HOST=<tv ip> LITELLM_URL=https://litellm.example.com LITELLM_KEY=sk-... \
  SEARXNG_URL=https://search.example.com NTFY_URL=https://ntfy.example.com/tv \
  VOICE_KEY=1 addons/ask-assistant/install.sh                   # all of it in one go
```

| Variable | What it sets |
|---|---|
| `LITELLM_URL`, `LITELLM_KEY` | Your server. `/v1` is added when missing. |
| `LITELLM_MODEL` | The default model. Without it: the one picked in setup, else the server's first. |
| `SEARXNG_URL` | Web search and YouTube search. Needs `json` in SearXNG's `search.formats`. |
| `NTFY_URL`, `NTFY_TOKEN` | "Send it to my phone". |
| `SPEECH_LANGUAGE` | `de-DE`, `en-GB` … for the recognizer and the voice. Empty: the TV's language. |
| `HOOKS` | `[{"name":"movie night","description":"Dim the lights for a film","url":"https://n8n…/webhook/…"}]` |
| `VOICE_KEY` | `1` makes the mic button open Ask and switches on its accessibility service; `0` gives the button back to the TV's search and switches the service off. See [The mic button](#the-mic-button). |

Without the build tools, build in Docker the way the launcher is built, then run with `SKIP_BUILD=1`.

## The mic button

Android TV handles the assistant key before apps and accessibility services see it, and starts the assistant.
On the XGIMI it sends `android.speech.action.WEB_SEARCH` to whatever holds the Assistant role. So Ask answers
`ASSIST` and `WEB_SEARCH`, and `VOICE_KEY=1` makes it the role holder:

```bash
adb shell cmd role add-role-holder android.app.role.ASSISTANT io.github.nitzzzu.voicechat 0
```

The TV's own recognizer stays the speech service, which Ask listens with, so nothing else changes. If the first
press shows "Open with", choose Ask and Always. To give the button back to Google:
`cmd role add-role-holder android.app.role.ASSISTANT com.google.android.katniss 0`.

## Your server

Any OpenAI-compatible endpoint works: LiteLLM, llama.cpp's `llama-server`, Ollama, vLLM, or a hosted API. Ask
reads the model list from `/v1/models` and streams `/v1/chat/completions` with tools.

- **OpenRouter.** See [OpenRouter](#openrouter) below.
- **Tool calling.** Native `tool_calls` are used when the server returns them (llama.cpp with `--jinja`, LiteLLM,
  hosted APIs). Small local models often write the call into the text instead. Ask reads those dialects too:
  Qwen/Hermes `<tool_call>{…}</tool_call>`, MiniCPM `<function name=…>`, Nanbeige `<function=…>` and Gemma 4
  `<|tool_call>call:…`. The parser follows caret's tool-call rescue, and the markup is never shown or
  spoken.
- **Thinking models.** A `<think>` block is hidden, and `reasoning_content` is ignored. Only the answer is shown
  and read.
- **Cold starts.** A model that llama-swap has to load first can take minutes. Ask waits up to four.
- **A server on Tailscale.** Install Tailscale on the TV (Play Store) and sign in. Without it the TV can't reach
  `*.ts.net` or other tailnet-only names, and Ask says so.
- **The key.** Give the TV a key of its own, limited to the models you want there, rather than the master key:

  ```bash
  curl -X POST https://litellm.example.com/key/generate -H "Authorization: Bearer $MASTER" \
    -H 'Content-Type: application/json' \
    -d '{"key_alias":"tv","models":["gemma-4-26b-a4b","glm-5.3"],"max_budget":5}'
  ```

## OpenRouter

No server of your own? [OpenRouter](https://openrouter.ai) gives one key for hundreds of hosted models, some of them
free.

1. Create a key at <https://openrouter.ai/settings/keys>. Free models need an account but no credit; they are
   rate-limited per day, and each question takes two or more requests (a tool call, then the answer).
2. Set Ask up with it, on the phone page (address `https://openrouter.ai/api` and the key) or from a computer:

   ```bash
   TV_HOST=<tv ip> LITELLM_URL=https://openrouter.ai/api LITELLM_KEY=sk-or-v1-... \
     LITELLM_MODEL=nvidia/nemotron-3.5-lightning:free addons/ask-assistant/install.sh
   ```

3. Pick a model. The list only offers models OpenRouter marks as supporting tools (Ask needs them), without the
   `:batch` variants; in the list, ◀ ▶ jump ten rows. Say "use nemotron" to switch by voice.

| Free model | How it did here |
|---|---|
| `nvidia/nemotron-3.5-lightning:free` | Weather, web search, a misheard title (Inception) and YouTube all right; quick |
| `nvidia/nemotron-3-ultra-550b-a55b:free` | Tool calls right; slower |
| `google/gemma-4-26b-a4b-it:free`, `google/gemma-4-31b-it:free` | Often busy upstream ("rate-limited"): Ask says the model is busy, try another |

Avoid `openrouter/free`: it routes each request to a different free model, so tool calls come and go. A model that
refuses tools anyway gets a plain chat answer. OpenRouter lists models without checking the key, so a wrong key
only shows on the first question ("the key was refused").

To try OpenRouter and go back to your own server without typing your key again, back the server settings up first:

```bash
adb shell content call --uri content://io.github.nitzzzu.voicechat.setup --method backup    # keeps address, key, model
#   ... set OpenRouter as above, try it ...
adb shell content call --uri content://io.github.nitzzzu.voicechat.setup --method restore   # puts them back
```

With OpenRouter your questions go to OpenRouter and the model's provider, not to a server of your own.

## Privacy and safety

| What | How |
|---|---|
| Your voice | Speech-to-text is the TV's own recognizer (Google on most Android TVs), so the audio goes to it. Only the text goes to your server. The mic is open only while the orb is red. |
| The conversation | Sent to the server you set, kept in memory while Ask is open. With OpenRouter that is OpenRouter and the model's provider. "Remember" facts stay on the TV; "forget …" removes them, `content call … --method forget` clears all. |
| Where else words go | Only when a request needs it, straight from the TV: a heard film or series title (and its IMDb id) to Cinemeta (`v3-cinemeta.strem.io`); a place name, then its coordinates, to Open-Meteo for the weather; search words to your SearXNG, and through it to the search engines it uses; a page's address to that page's site when Ask reads it; the text you send to your phone to your ntfy; your webhooks' calls to their URLs. Nothing else is sent anywhere. |
| Keys | Stored in the app's private storage. The setup page never shows a saved key back; the ADB `get` call only says whether one is set. |
| The setup page | Plain HTTP on your home network, so the key you paste goes from the phone to the TV unencrypted across your Wi-Fi. Use it on a network you trust, or set the key from a computer with `install.sh` (over ADB). |
| Web pages | Marked as untrusted data, so instructions inside a page are not followed. Pages on your LAN, the tailnet or the TV itself are refused, on every redirect. |
| The screen | Read only when you ask what's on it, and only the app behind the panel. |
| The mic button | Ask holds the Assistant role; nothing filters your keys. `VOICE_KEY=1` also switches on Ask's accessibility service (Back, sleep, reading and pressing on screen, only when you ask). `VOICE_KEY=0` gives the button back and switches the service off. |
| Background | Nothing runs while Ask is closed except a timer's alarm. No polling, no analytics. |

## Permissions

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | The remote's microphone, through the speech recognizer |
| `INTERNET`, `ACCESS_WIFI_STATE` | Your server and the tools; the setup page on the LAN |
| `READ_TV_LISTINGS` | Stremio's Continue Watching row |
| `SYSTEM_ALERT_WINDOW` | A timer's reminder over the app you're in |
| Assistant role | The mic button opens Ask |
| Accessibility (optional) | Back, sleep and reading the screen on request |

## Add-on card

On the Clean Your TV launcher, Ask declares a [module card](../../docs/modules.md) "Ask · Voice chat" with a teal
glow. Answer **Show card** and put `io.github.nitzzzu.voicechat` in `dock_apps` to keep it in the dock. On any
other launcher it's an ordinary app.

## Build

JDK 17 and Android SDK 34, as for the launcher: `./gradlew assembleRelease lintRelease`. The one dependency is
ZXing's core, for the setup QR code. The APK is about 80 KB.

## Tested on

XGIMI Halo+ (XK03H), Android TV 11, RT.3.24.327, with the XGIMI RC voice remote. Its mic button arrives as
`KEYCODE_ASSIST` and its audio reaches the recognizer as the built-in mic. Server: LiteLLM v1.105 in front of
llama.cpp (`gemma-4-26b-a4b` as the default, `minicpm5-2b`) and GLM 5.3.

Checked on that projector, with questions typed over ADB (`content call … --method ask`): chat, weather, web
search, Continue Watching, YouTube in SmartTube, volume, pausing SmartTube, a timer's reminder over Home, resuming and
opening Stremio titles (misheard ones included), reading Stremio's screen, pressing a tab in it, switching
the model and remembering a fact by voice, and a routine made by voice and run by its name (volume, reminder and
YouTube in one go). The mic button opening Ask was checked with the real remote. Not run:
`turn_off` and sleep timers (they put the projector to standby), HDMI switching, ntfy and webhooks.

## Scripting

Over ADB, a typed question is handled as if it were spoken, which also makes Ask scriptable (from Home Assistant,
n8n over SSH, a test):

```bash
adb shell content call --uri content://io.github.nitzzzu.voicechat.setup --method ask --arg "'pause and set the volume to 10'"
```

Only the ADB shell may call it. Values that contain a colon (`https://…`, `model:free`) need it escaped as `\:` in
`content call --extra`; `install.sh` does that for you.
