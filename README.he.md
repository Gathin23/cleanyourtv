<div dir="rtl" lang="he" align="right">

<p align="center"><a href="https://cleanyour.tv"><img src="https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/banner.png" alt="CleanYourTV — מסך בית נקי מפרסומות לטלוויזיה" width="800"></a></p>

<p align="center" dir="ltr"><a href="README.md">English</a> · <strong lang="he" dir="rtl">עברית</strong> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">正體中文</a> · <a href="README.vi.md">Tiếng Việt</a> · <a href="README.ja.md">日本語</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português</a> · <a href="README.nl.md">Nederlands</a> · <a href="README.de.md">Deutsch</a> · <a href="README.uk.md">Українська</a> · <a href="README.tr.md">Türkçe</a> · <a href="README.uz.md">Oʻzbekcha</a></p>

<p align="center">
  <a href="https://github.com/Gathin23/cleanyourtv/stargazers"><img src="https://img.shields.io/github/stars/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="כוכבים ב־GitHub"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/pulls?q=is%3Amerged"><img src="https://img.shields.io/github/issues-search?query=repo%3AGathin23%2Fcleanyourtv%20is%3Apr%20is%3Amerged&style=flat&cacheSeconds=1800&label=merged%20PRs&color=cfffcf&labelColor=15181e" alt="בקשות מיזוג שהתקבלו"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/network/members"><img src="https://img.shields.io/github/forks/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="פיצולים"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://img.shields.io/github/contributors/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="תורמים"></a>
  <a href="https://buymeacoffee.com/gathint"><img src="https://img.shields.io/badge/support-%E2%9D%A4-ff5e8a?style=flat&labelColor=15181e" alt="תמיכה בפרויקט"></a>
  <a href="https://www.producthunt.com/products/cleanyourtv"><img src="https://img.shields.io/badge/Product%20Hunt-upvote%20%E2%96%B2-da552f?style=flat&logo=producthunt&logoColor=white&labelColor=15181e" alt="CleanYourTV ב־Product Hunt"></a>
</p>

<p align="center">
  <a href="https://trendshift.io/repositories/310225"><img src="docs/trending-badge.png" alt="מקום ראשון ב־Trendshift בשבוע 19–25 באוקטובר 2026" width="360"></a>
</p>

## מתחילים כאן

| מה יש לכם | לאן ממשיכים |
|---:|---:|
| **Android TV או Google TV** | [התחלה מהירה](#התחלה-מהירה) — עוזר קוד יכול לבצע את התהליך בכ־15 דקות. |
| **Samsung, LG, Roku, Vizio או Hisense VIDAA** | הפרויקט אינו פועל במערכות האלה. אפשר לכבות את המעקב באמצעות השלט. [מדריך לפי מערכת](https://cleanyour.tv/not-android) |
| **טלפון או מחשב בלי עוזר קוד** | [אפליקציות הקהילה](https://cleanyour.tv/app) לטלפוני Android, ל־Mac ול־Windows. |
| **רוצים לראות את התוצאה קודם** | [מה מקבלים](#מה-מקבלים) — צילומי מסך של מסך הבית החדש. |

האתר הרשמי הוא [cleanyour.tv](https://cleanyour.tv), לצד המאגר הזה. האתרים cleanyourtv.com ו־clean-your-tv.net אינם קשורים לפרויקט, והוא אינו מפרסם אפליקציה בחנויות האפליקציות. [פרטים](https://github.com/Gathin23/cleanyourtv/issues/1004)

[איך זה עובד](#איך-זה-עובד) · [התחלה מהירה](#התחלה-מהירה) · [מה מקבלים](#מה-מקבלים) · [טלוויזיות נתמכות](#טלוויזיות-נתמכות) · [התקנה ידנית](#התקנה-ידנית) · [מסך הבית](#מסך-הבית) · [שחזור](#שחזור-הטלוויזיה) · [עדכוני קושחה](#אחרי-עדכון-קושחה) · [שאלות נפוצות](#שאלות-נפוצות) · [תרומה לפרויקט](#תרומה-לפרויקט)

## איך זה עובד

בטלוויזיות Android אפשר להתחבר דרך ADB, כמו בטלפוני Android. מפעילים ניפוי שגיאות ב־USB באפשרויות למפתחים ומאשרים את המחשב בטלוויזיה פעם אחת.

| שלב | מה קורה |
|---:|---:|
| **גיבוי** | שומרים את רשימת החבילות המותקנות ואת ההגדרות. |
| **השבתה** | משביתים שירותי טלמטריה ופרסום, שורות תוכן ואפליקציות מיותרות שהיצרן התקין מראש, באמצעות <code dir="ltr">pm disable-user</code>. אפשר לבטל את השינויים בעזרת סקריפט השחזור. |
| **התקנה** | מתקינים אפליקציית מסך בית קלה ומגדירים אותה כברירת המחדל. |

**בלי root, בלי כבל, בלי להסיר אפליקציות, ועם פקודת שחזור אחת.** רשימת החבילות משתנה לפי הדגם. ההנחיות לעוזר הקוד מבקשות ממנו לבדוק את הטלוויזיה ולברר אילו תכונות נמצאות בשימוש לפני ההשבתה.

## התחלה מהירה

יש לחבר את המחשב ואת הטלוויזיה לאותה רשת. לבנייה במחשב דרושים מעטפת פקודות, Python 3, גרסת JDK בטווח <span dir="ltr">17–22</span> ו־Android SDK עם פלטפורמה 34 וכלי בנייה מסדרה 34. אפשר להשתמש ב־Claude Code, ב־[Codex](https://github.com/openai/codex), ב־[Gemini CLI](https://github.com/google-gemini/gemini-cli) או ב־[Antigravity CLI](https://antigravity.google/product/antigravity-cli). בלי עוזר קוד, אפשר לבצע את אותם השלבים לפי [הוראות ההתקנה הידנית](#התקנה-ידנית).

| איפה | מה עושים |
|---:|---:|
| **1. בטלוויזיה** | נכנסים להגדרות, להעדפות המכשיר ואז ל״מידע״. לוחצים 7 פעמים על OK בשורה ״מספר Build״, חוזרים לאפשרויות למפתחים ומפעילים ניפוי שגיאות ב־USB. אם יש מתג לניפוי שגיאות דרך הרשת או ADB, מפעילים גם אותו. משאירים את הטלוויזיה דולקת. |
| **2. במחשב** | משכפלים את המאגר באמצעות <code dir="ltr">git clone https://github.com/Gathin23/cleanyourtv</code>, פותחים את התיקייה בעוזר הקוד, מדביקים את תוכן [PROMPT.md](PROMPT.md) ומציינים את דגם הטלוויזיה. |
| **3. במהלך ההתקנה** | מאשרים את בקשת ניפוי השגיאות בטלוויזיה ובוחרים אילו אפליקציות להשאיר, מה יוצג בשורת המועדפים ובאיזה סדר, ואם להשאיר את העוזר הקולי, Chromecast ועדכוני הקושחה. |

חשוב לשכפל את המאגר כדי שעוזר הקוד ישתמש בסקריפטים הקיימים, <code dir="ltr">tools/disable.sh</code> ו־<code dir="ltr">launcher/build-and-install.sh</code>. הם כוללים גיבוי, בדיקה שמסך הבית נשאר זמין ותיעוד לשחזור. העוזר מגבה, משבית, בונה ומתקין, ובודק את השלבים בעזרת צילומי מסך מהטלוויזיה.

## מה מקבלים

מסך בית בסגנון Apple TV, בלי שורות ממומנות, הצעות חוזרות מהיצרן או דיווחי שימוש ממסך הבית.

| רכיב | מה רואים |
|---:|---:|
| **מסך בית כהה** | ארבע אפליקציות מועדפות בשורה עם פינות מעוגלות. |
| **אזור עליון** | שם האפליקציה המסומנת באותיות גדולות, עם הילה בצבע שנלקח מהסמל שלה. |
| **רשת האפליקציות** | שאר האפליקציות מופיעות כשלוחצים למטה. |
| **מסגרת זכוכית** | המסגרת עוברת בין האריחים. האריח המסומן מתרומם ומציג את שמו. |
| **שלושה כפתורים במראה זכוכית חלבית** | מקורות קלט (HDMI לפי השמות שהוגדרו בטלוויזיה, AV ושידור חי), רשת והגדרות. |
| **ברכה, שעון ותאריך** | שעון דק בגופן [Outfit](https://fonts.google.com/specimen/Outfit) ושומר מסך תואם. ההגדרה <code dir="ltr">show_stats</code> מציגה נתוני מעבד, זיכרון ואחסון כשהם זמינים. הנתונים מתעדכנים רק כשמסך הבית מוצג. |
| **ערכות עיצוב וכרטיסי תוספים** | אפשר לשנות צבעים, רקע, מועדפים ומספר עמודות בלי לבנות מחדש. אפליקציות יכולות להציע כרטיסים שיופיעו לאחר אישור. |

| | |
|---:|---:|
| ![שם האפליקציה המסומנת והילה בצבע הסמל](docs/focus-2-up-netflix.png) | ![תפריט מקורות קלט עם HDMI, AV ושידור חי](docs/inputs-menu.jpg) |
| האזור העליון וההילה | מקורות קלט ומצב חיבור |
| ![רשת האפליקציות עם מסגרת הזכוכית](docs/focus-1-fancode.png) | ![שעון דק נע מעל הילה](docs/clock-screensaver.png) |
| מסגרת הזכוכית ושם האריח | שומר מסך עם שעון בתנועה |
| ![ערכת Aurora עם תמונת רקע וכפתורים בגוון תואם](themes/aurora/screenshot.jpg) | ![שש עמודות ושישה מועדפים](themes/six-columns/screenshot.jpg) |
| ערכת Aurora | שש עמודות באותו מסך בית |
| ![הזזת אריח לאחר לחיצה ארוכה על OK](docs/tile-move.jpg) | ![ספריית האפליקציות המותקנות](docs/app-library.jpg) |
| הזזת אריח בעזרת החצים | החזרת קיצור דרך למסך הבית |

## טלוויזיות נתמכות

| מערכת | מה עובד |
|---:|---:|
| **Android TV, Google TV** | השבתת חבילות והתקנת מסך בית. |
| **Fire TV** | השבתת חבילות בלבד, כי Fire OS אינו מאפשר להחליף את מסך הבית. ב־Fire TV Box משנת 2015 עם Fire OS 5 גם פקודות <code dir="ltr">pm</code> נחסמות. |
| **חלק מדגמי TCL לשוק הסיני** | התקנת מסך בית חלופי חסומה. רשימות הדגמים מציינות זאת בראש הקובץ. |
| **Samsung (Tizen), LG (webOS), Roku, Vizio, Hisense VIDAA** | אין תמיכה ב־ADB. אפשר [לכבות מעקב בשלט](https://cleanyour.tv/not-android). יש גם [פרויקטים ל־webOS](SHOWCASE.md#other-platforms-fire-tv-and-lg-webos) ו[תיעוד בדיקה על Samsung עם Tizen](docs/devices/samsung-tizen.md). |

הרשימות נבדקו על ידי הקהילה. לכל דגם יש תיקייה ב־[examples](examples/). הטבלה מציגה את המותגים עם הכי הרבה רשימות.

<div dir="ltr" align="left">

| | | | | | |
|:---|:---|:---|:---|:---|:---|
| [TCL](https://cleanyour.tv/tvs?q=TCL) · 84 | [Sony](https://cleanyour.tv/tvs?q=Sony) · 50 | [Xiaomi](https://cleanyour.tv/tvs?q=Xiaomi) · 43 | [Philips](https://cleanyour.tv/tvs?q=Philips) · 23 | [Skyworth](https://cleanyour.tv/tvs?q=Skyworth) · 17 | [Hisense](https://cleanyour.tv/tvs?q=Hisense) · 16 |
| [SHARP](https://cleanyour.tv/tvs?q=SHARP) · 10 | [Amazon Fire TV](https://cleanyour.tv/tvs?q=Amazon%20Fire%20TV) · 9 | [Haier](https://cleanyour.tv/tvs?q=Haier) · 7 | [Panasonic](https://cleanyour.tv/tvs?q=Panasonic) · 7 | [BenQ](https://cleanyour.tv/tvs?q=BenQ) · 6 | [JVC](https://cleanyour.tv/tvs?q=JVC) · 6 |
| [Toshiba](https://cleanyour.tv/tvs?q=Toshiba) · 6 | [Acer](https://cleanyour.tv/tvs?q=Acer) · 4 | [Formovie](https://cleanyour.tv/tvs?q=Formovie) · 4 | [Nokia](https://cleanyour.tv/tvs?q=Nokia) · 4 | [OnePlus](https://cleanyour.tv/tvs?q=OnePlus) · 4 | [Thomson](https://cleanyour.tv/tvs?q=Thomson) · 4 |
| [Artel](https://cleanyour.tv/tvs?q=Artel) · 3 | [FFalcon](https://cleanyour.tv/tvs?q=FFalcon) · 3 | [Google](https://cleanyour.tv/tvs?q=Google) · 3 | [Grundig](https://cleanyour.tv/tvs?q=Grundig) · 3 | [Motorola](https://cleanyour.tv/tvs?q=Motorola) · 3 | [Noblex](https://cleanyour.tv/tvs?q=Noblex) · 3 |
| [NVIDIA Shield](https://cleanyour.tv/tvs?q=NVIDIA%20Shield) · 3 | [onn](https://cleanyour.tv/tvs?q=onn) · 3 | [RCA](https://cleanyour.tv/tvs?q=RCA) · 3 | [Redmi](https://cleanyour.tv/tvs?q=Redmi) · 3 | [RockTek](https://cleanyour.tv/tvs?q=RockTek) · 3 | [Vu](https://cleanyour.tv/tvs?q=Vu) · 3 |

</div>

<details>
<summary>עוד 80 מותגים ו־14 מותגי מקרנים</summary>

<div dir="ltr" align="left">

**Challenger** 2 · **CHiQ** 2 · **DEXP** 2 · **EKO** 2 · **Hitachi** 2 · **Insignia Fire TV** 2 · **Kalley** 2 ·
**Kogan** 2 · **Lumio** 2 · **Master-G** 2 · **PRISM+** 2 · **Sankey** 2 · **Sber** 2 · **Síragon** 2 · **TIM** 2 ·
**Vidda** 2 ·
AiPro2K · AiPro4K · AIWA · AQUA · ASUS · Beko · Bell Canada · BGH · Caxium · Cecotec · Compac · Dcolor · Dialog ·
Durabrand · DYON · Essentiel b · Freebox · HAAM · Hikeen · Home+ · Impex · INCLO · Indurama · Jio · Karbonn · Kodak ·
Koryo · KTC · KUNFT · Liberton · LYNCAST · Magyar Telekom · MarQ · MECOOL · MTC · Next · OK. · OPPO · Panavox · PEAQ ·
Philco · Polytron · PPTV · Prosonic · Salora · SAMPO · SANYO · Seleco · Smarttech · STRONG · SunWind · Super General ·
TD Systems · Tele System · Tesla · TiVo · Tornado · Turkcell · Tuvio · Verizon · VISIO · Vision · Vityas · X.Vision ·
Alwtniet, Anker Nebula, BYINTEK, Chihi, Epson, Gadnic, HOTACK, HY320, JMGO, Magcubic, Portronics, Stellamore, Wanbo, XGIMI

</div>

</details>

אפשר לחפש דגם ב־[cleanyour.tv/tvs](https://cleanyour.tv/tvs) או ב[רשימה המלאה](examples/README.md). אם הדגם חסר, עוזר הקוד יכול לבדוק את החבילות בטלוויזיה. אחר כך אפשר [לתרום את הרשימה](CONTRIBUTING.md).

## התקנה ידנית

| מה צריך | למה |
|---:|---:|
| **Python 3** | להרצת הכלים. הפקודה הראשונה בהמשך יוצרת סביבת Python מבודדת ומתקינה את התלויות. |
| **<span dir="ltr">JDK 17–22</span> ו־Android SDK** | לבניית מסך הבית. צריך JDK מלא שכולל <code dir="ltr">jlink</code>, פלטפורמה 34 וכלי בנייה בגרסה 34.0.0 או 34.x חדשה יותר. Gradle 8.9 אינו תומך ב־JDK 23 ומעלה. ב־Linux הסקריפט מחפש את ה־SDK ב־<code dir="ltr">~/Android/Sdk</code> ואת Java ב־<code dir="ltr">PATH</code>. |

ב־macOS אפשר להתקין את הכלים כך:

<div dir="ltr" align="left">

```bash
brew install openjdk@17 android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
```

</div>

הכלי <code dir="ltr">tools/tv.py</code> מתחבר ל־ADB ברשת ביציאה 5555. אם היא סגורה והטלוויזיה מציעה ניפוי שגיאות אלחוטי עם קוד צימוד, אפשר לצמד פעם אחת בעזרת <code dir="ltr">adb</code>. לאחר הצימוד אפשר להריץ <code dir="ltr">adb tcpip 5555</code> כדי לפתוח את היציאה, בדרך כלל עד האתחול הבא. אפשר גם להשאיר אותה סגורה ולהגדיר את <code dir="ltr">TV_ADB_BINARY</code> לנתיב של <code dir="ltr">adb</code> ואת <code dir="ltr">TV_HOST</code> לכתובת וליציאה שמופיעות במסך ניפוי השגיאות האלחוטי. [חיבור ADB מוצפן](tools/native-adb.md) · [פתרון בעיות חיבור](docs/TROUBLESHOOTING.md#connecting)

<div dir="ltr" align="left">

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # your TV's IP address

tools/venv/bin/python -I tools/tv.py --probe                 # model, Android version, firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # one shell command on the TV
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # apply a debloat list
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # undo it, stock launcher back
tools/venv/bin/python -I tools/check.py examples/xiaomi-mi-tv-4a/debloat.txt   # what a firmware update undid
TV_HOST=192.168.1.50 launcher/build-and-install.sh           # build the launcher and make it Home
tools/theme.sh themes/aurora                                 # push a theme; --reset removes it
```

</div>

| כלי | מה הוא עושה |
|---:|---:|
| [<code dir="ltr">tools/tv.py</code>](tools/tv.py) | לקוח ADB ב־Python להרצת פקודות, העברת קבצים, אתחול וצילומי מסך. תומך גם ב[חיבור מצומד ומוצפן](tools/native-adb.md). |
| [<code dir="ltr">tools/docker-build.sh</code>](tools/docker-build.sh) | בונה את מסך הבית או תוסף ב־Docker. מתקינים את התוצר עם <code dir="ltr">SKIP_BUILD=1</code>. [מדריך](tools/docker-build.md) |
| [<code dir="ltr">tools/b64push.py</code>](tools/b64push.py) | מעביר קבצים גדולים במקטעי Base64 ומאמת את תקינותם. |
| [<code dir="ltr">tools/relay.py</code>](tools/relay.py) | מעביר יציאה מקומית ליציאת ADB של הטלוויזיה. מאזין במחשב המקומי בלבד. [מדריך](tools/relay.md) |
| [<code dir="ltr">tools/disable.sh</code>](tools/disable.sh), [<code dir="ltr">restore.sh</code>](tools/restore.sh) | משביתים חבילות לפי רשימה ומשחזרים לפי המצב שתועד לפני השינוי. |
| [<code dir="ltr">tools/recovery.py</code>](tools/recovery.py) | כותב את רשומת השחזור ומדפיס את רצף פקודות השחזור. |
| [<code dir="ltr">tools/check.py</code>](tools/check.py) | בודק אילו שינויים עדכון קושחה ביטל. האפשרות <code dir="ltr">--fix</code> מחילה אותם מחדש. |
| [<code dir="ltr">tools/adb-closed.py</code>](tools/adb-closed.py) | בודק אם יציאת ADB סגורה. מחזיר 0 אם החיבור נדחה, 1 אם היציאה פתוחה ו־2 כשאין תשובה או הבדיקה לא רצה. |
| [<code dir="ltr">tools/philips-api.py</code>](tools/philips-api.py) | בודק אם JointSpace בטלוויזיות Philips עדיין זמין ל־Home Assistant. מנסה תחילה HTTPS ביציאה 1926 ואז 1925, וקורא רק מידע שזמין בלי צימוד. |
| [<code dir="ltr">tools/privacy.py</code>](tools/privacy.py) | מציג ראיות לשירותי קול, זיהוי תוכן וטלמטריה. האפשרות <code dir="ltr">--write</code> שומרת רשימה בפורמט של כלי ההשבתה והשחזור. |
| [<code dir="ltr">tools/theme.sh</code>](tools/theme.sh), [<code dir="ltr">theme_restart.py</code>](tools/theme_restart.py) | מעבירים ערכת עיצוב מ־[themes](themes/) ומפעילים מחדש את מסך הבית. |
| [<code dir="ltr">tools/manage.py</code>](tools/manage.py) | מציג תוספים, ערכות ומתגים עם צילומי מסך, בודק מה מותקן ומריץ את סקריפט ההתקנה, העדכון או ההסרה של כל פריט. |
| [<code dir="ltr">tools/bench.py</code>](tools/bench.py) | מודד זמני תגובה, תנועת מיקוד, גמגומים, זמני פתיחה וחזרה לבית, ומעבד וזיכרון במנוחה. שומר מדידות ב־JSON. |
| [<code dir="ltr">tools/trace.py</code>](tools/trace.py), [<code dir="ltr">trace_report.py</code>](tools/trace_report.py) | מקליטים נתוני Perfetto לפתיחה, לחזרה לבית ולניווט, ומשווים את חלוקת הזמן. [מדריך](tools/trace.md) |
| [<code dir="ltr">tools/flamechart.py</code>](tools/flamechart.py) | מצייר השוואת לפני ואחרי מצילומי Perfetto, עם מסלול לכל תהליכון. [מדריך](tools/flamechart.md) |
| [<code dir="ltr">tools/screendiff.py</code>](tools/screendiff.py) | משווה צילומי מסך ברמת הפיקסל, מדלג על אזורים שמוסכו ומטשטש את שם הרשת. [מדריך](tools/screendiff.md) |
| [<code dir="ltr">tools/race_calendar.py</code>](tools/race_calendar.py) | מוסיף לערכת עיצוב את לוח מרוצי F1 לעונה דרך Jolpica API. |
| [<code dir="ltr">launcher/build-and-install.sh</code>](launcher/build-and-install.sh) | בונה גרסה מוקטנת, מתקין אותה, מחליף את מסך הבית ומשחזר את המצב אם ההחלפה לא הצליחה. |

אפשר להחזיק רשימות מחוץ למאגר. נתיבים יחסיים נפתרים ביחס לתיקייה שממנה מריצים את הפקודה, ואפשר להשתמש גם בנתיבים מוחלטים.

<details>
<summary>התקנה ב־Windows באמצעות PowerShell</summary>

צריך Python 3, גרסת JDK בטווח <span dir="ltr">17–22</span> ופלטפורמה 34 עם כלי הבנייה מסדרה 34 דרך SDK Manager. הסקריפט משתמש ב־<code dir="ltr">JAVA_HOME</code> כשהוא מצביע לגרסה מתאימה, אחרת הוא מנסה את ה־JDK שמגיע עם Android Studio.

<div dir="ltr" align="left">

```powershell
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
```

</div>

ב־Google TV יש להעביר גם את שם החבילה של מסך הבית המקורי לפקודת השחזור: <code dir="ltr">-Stock com.google.android.apps.tv.launcherx</code>.

</details>

<details>
<summary>איך הסקריפטים שומרים על מסך בית זמין</summary>

סקריפט ההתקנה בודק מי מטפל בבקשת HOME ומשבית מסך בית מקורי רק אם הוא מוכר. הרשימה <code dir="ltr">KNOWN_STOCK_LAUNCHERS</code> כוללת את <code dir="ltr">com.google.android.tvlauncher</code>, את <code dir="ltr">com.google.android.apps.tv.launcherx</code>, את <code dir="ltr">com.google.android.leanbacklauncher</code> ואת <code dir="ltr">com.mitv.tvhome</code>. אפשר להגדיר חבילה במפורש באמצעות <code dir="ltr">STOCK_LAUNCHER=&lt;package&gt;</code> או <code dir="ltr">-StockLauncher</code>. הערך <code dir="ltr">none</code> משאיר אותה פעילה.

- אם רשימת ההשבתה כוללת את מסך הבית הפעיל ואין חלופה אמיתית, סקריפט ההשבתה מדלג עליו וממשיך עם שאר הרשימה.
- אשף ההגדרה, ההגדרות, <code dir="ltr">FallbackHome</code> ואפליקציות בדיקות יצרן אינם נחשבים למסך בית חלופי.
- ב־Google TV הסקריפט שומר על מסך הבית הפעיל כל עוד setupwraith יכול לתפוס את מקומו, למשל כשאשף ההגדרה עדיין אחראי לחיבור ה־Wi-Fi.

לאחר שסקריפט ההתקנה מגדיר את מסך הבית החדש, אפשר להריץ שוב את אותה רשימה. ההגדרה <code dir="ltr">FORCE_HOME=1</code> (או <code dir="ltr">$env:FORCE_HOME</code> ב־PowerShell) עוקפת את ההגנה.

</details>

## המיקרופון ופרטיות

הרשאת <code dir="ltr">RECORD_AUDIO</code> אינה מעידה שחבילה מקליטה בפועל. שירותי זיהוי תוכן אוטומטי (ACR) יכולים לגשת לתוכן שמוצג גם בלי הרשאת שמע. הכלי [privacy.py](tools/privacy.py) מדווח על המידע שהטלוויזיה מספקת לגבי שירותי קול, זיהוי תוכן וטלמטריה. אפשר להוסיף <code dir="ltr">--write</code> כדי להפיק רשימת השבתה. הבדיקה אינה יכולה לקבוע מה מטרת השירות. [מדריך הפרטיות](docs/privacy.md)

<div dir="ltr" align="left">

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/privacy.py
```

</div>

## מסך הבית

אפליקציית מסך בית קלה ב־Java, בגודל כ־572 KB, שמתאימה לטלוויזיות עם 1 GB של זיכרון. היא פועלת בלי טשטוש, וידאו, הרשאת אינטרנט או שירותי רקע. הטלוויזיה צריכה להריץ Android 8.0 ומעלה (API 26), לפי <code dir="ltr">minSdk</code> ב־[build.gradle](launcher/app/build.gradle). [אפשרויות לדגמים ישנים](docs/TROUBLESHOOTING.md)

| תכונה | איך היא עובדת |
|---:|---:|
| **התאמה אישית** | סדר המועדפים, אפליקציות מוסתרות, אפליקציית שידור חי, 4 עד 7 אריחים בשורה והפחתת תנועה. ההגדרות נמצאות ב־[config.xml](launcher/app/src/main/res/values/config.xml). |
| **הגדרות בטלוויזיה** | לחיצה ארוכה על OK בכפתור ההגדרות פותחת את הגדרות מסך הבית. עם <code dir="ltr">launcher_settings_button</code> מספיקה לחיצה רגילה. הגדרות הטלוויזיה זמינות גם מדף ההגדרות ומאריח האפליקציה. |
| **ערכות עיצוב** | רקע, תמונה ופריסה מקובץ <code dir="ltr">config.json</code> שנשלח דרך הרשת. יש 28 ערכות ב־[themes](themes/), כולל ברירת המחדל. |
| **31 שפות** | השפה נקבעת לפי הטלוויזיה. בעברית הפריסה מוצגת מימין לשמאל. |
| **ספריית אפליקציות** | אפשר לדפדף בכל האפליקציות המותקנות ולהוסיף או להסיר קיצורי דרך ממסך הבית. [מדריך](docs/app-library.md) |
| **תפריט אריח** | <code dir="ltr">tile_menu</code> מופעל כברירת מחדל. לחיצה ארוכה על OK מאפשרת להזיז אריח ולפתוח פרטי אפליקציה. כשספריית האפליקציות מופעלת אפשר גם להסיר את האריח מהבית. כשהתפריט כבוי, לחיצה ארוכה פותחת את פרטי האפליקציה. |
| **כרטיסי תוספים** | אפליקציות יכולות להציע אריח שמוצג לאחר אישור חד־פעמי. [מפרט הכרטיסים](docs/modules.md) |
| **תוספים** | אפליקציות נפרדות שמתחברות דרך הכרטיסים. [רשימת תוספים](addons/) |
| **שומר מסך עם שעון** | השעון נע על המסך כדי לצמצם צריבה. |
| **כפתורי השלט** | הכפתורים למקורות קלט, YouTube והגדרות יכולים להמשיך לפעול אחרי החלפת מסך הבית המקורי. |

[ערכות, מתגים ותוספים עם צילומי מסך](https://cleanyour.tv/addons) · [מדריך הגדרות, שפות, שלט, שומר מסך ובנייה](docs/launcher.md)

## שחזור הטלוויזיה

יש לשמור את הרשימה שהוחלה. פקודה אחת משחזרת את המצב הקודם.

<div dir="ltr" align="left">

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

</div>

לפני ההשבתה הסקריפט מתעד את המצב ב־<code dir="ltr">~/.cleanyourtv/</code>. השחזור משאיר חבילות שכבר היו מושבתות ומחזיר את מסך הבית לרכיב שהיה פעיל קודם. אפליקציית מסך הבית החלופית נשארת מותקנת ואפשר להסיר אותה מהגדרות האפליקציות. אם אין חיבור מהמחשב, אפשר להפעיל חבילות גם דרך הגדרות הטלוויזיה. [מדריך שחזור](docs/restore.md)

## אחרי עדכון קושחה

עדכון יכול להפעיל מחדש חבילות, להחזיר את מסך הבית של היצרן ולהוסיף אפליקציות. מפעילים שוב ADB לפי [ההתחלה המהירה](#התחלה-מהירה), בודקים מול הרשימה שהוחלה ומוסיפים <code dir="ltr">--fix</code> כדי להשבית שוב את החבילות הרלוונטיות.

<div dir="ltr" align="left">

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<your tv>/debloat.txt
```

</div>

[מדריך עדכוני קושחה](docs/firmware-updates.md) כולל קודי יציאה, בדיקה שבועית, פירוט של <code dir="ltr">--fix</code> ומידע על דגמים שמשאירים את מסך הבית המקורי או משביתים את מנגנון העדכון.

## פתרון בעיות

- **ב־macOS מתקבלת השגיאה “No route to host” גם כשהיציאה פתוחה.** בהגדרות המערכת, תחת פרטיות ואבטחה ואז רשת מקומית, מאשרים את Terminal,‏ iTerm2 או VS Code ופותחים אותו מחדש. ההרשאה שייכת לאפליקציה שמריצה את הפקודה. [פתרון בעיות חיבור](docs/TROUBLESHOOTING.md#connecting)
- **משביתים את מסך הבית המקורי רק אחרי שהחלופי מותקן ופועל.** אחרת הטלוויזיה עלולה לפתוח אפליקציית בדיקות יצרן. סקריפט ההתקנה דואג לסדר הפעולות.
- **ב־Google TV אשף ההגדרה <code dir="ltr">com.google.android.tungsten.setupwraith</code> עלול לתפוס את מסך הבית ולהיתקע בלולאת קריסות.** לפני השבתתו בודקים את <code dir="ltr">dumpsys wifi &#124; grep -o 'cname=[^ ]*'</code>. אם האשף שמר את הרשת, מתחברים אליה מחדש בהגדרות לפני ההשבתה. בכלי ההשבתה יש בדיקה לכך. השבתת האשף ב־Chromecast וב־Google TV Streamer של Google גם פוגעת בשידור מהטלפון. אם מסך הבית כבר נתקע, אפשר להפעיל את המקורי באמצעות <code dir="ltr">tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'</code>.

[מדריך פתרון בעיות מלא](docs/TROUBLESHOOTING.md)

## שאלות נפוצות

<details>
<summary>אפשר בלי מחשב או בלי ידע בתכנות?</summary>

כן. האפליקציות הבאות מבוססות על רשימות הפרויקט ומבצעות את הפעולות דרך Wi-Fi. הן מציגות תוכנית מראש, משביתות אפליקציות וכוללות שחזור.

| אפליקציה | מערכת | פיתוח |
|---:|---:|---:|
| [Clean Your TV app](https://github.com/udhaykumarbala/cleanyourtv-app) | טלפון Android | [@udhaykumarbala](https://github.com/udhaykumarbala) |
| [Tellyscope](https://github.com/man15h/tellyscope) | טלפון Android | [@man15h](https://github.com/man15h) |
| [TidyTV](https://github.com/jolan94/tidytv) | טלפון Android, דרך Google Play | [@jolan94](https://github.com/jolan94) |
| [CleanMyTV](https://github.com/wingedpanther/cleanmytv) | Mac ו־Windows | [@wingedpanther](https://github.com/wingedpanther) |

אלה פרויקטים עצמאיים, כל אחד ברישיון משלו. תקלות מדווחים במאגר של האפליקציה. [אפליקציות הקהילה](https://cleanyour.tv/app)

</details>

<details>
<summary>האם התהליך עלול להשבית את הטלוויזיה?</summary>

השינויים משביתים חבילות בפרופיל המשתמש. השבתת חבילה חיונית עלולה לפגוע בתפקוד, ולכן מחילים את הרשימה בקבוצות קטנות עם סקריפט שחזור זמין. אפשר להפעיל חבילות מחדש דרך הגדרות האפליקציות. אם הטלוויזיה לא עולה עד לשלב הזה, בדרך כלל אפשר לבצע איפוס להגדרות יצרן מתפריט השחזור. [מידע נוסף](docs/faq.md#is-this-safe-can-it-brick-the-tv)

</details>

<details>
<summary>מה קורה לעדכוני קושחה?</summary>

ברוב הרשימות מנגנון העדכון נשאר פעיל. אם הרשימה משביתה אותו, העדכון יגיע רק לאחר הפעלה מחדש. עדכון עשוי להחזיר חבילות שהושבתו. [מה עושים אחרי עדכון](#אחרי-עדכון-קושחה)

</details>

<details>
<summary>מה קורה לאפליקציות הסטרימינג?</summary>

הן ממשיכות להתעדכן דרך חנות Google Play. מסך הבית מציג ומפעיל את האפליקציות המותקנות באמצעות מנגנון ההפעלה הרגיל של Android.

</details>

<details>
<summary>איך מסדרים מחדש אפליקציות?</summary>

לוחצים לחיצה ארוכה על OK בכפתור ההגדרות ובוחרים ״סידור אפליקציות״. לוחצים OK כדי להרים אריח, מזיזים עם החצים ולוחצים ״חזרה״ כדי להניח אותו. לחיצה נוספת על ״חזרה״ מסיימת. בתפריט האריח אפשר לבחור ״הזזה״ כדי להתחיל ישירות. [מדריך הגדרות](docs/launcher.md#launcher-settings-and-the-default-home)

</details>

<details>
<summary>אפשר לעזור למישהו בבית אחר?</summary>

כן, דרך חיבור פרטי של [Tailscale](https://tailscale.com) בטלוויזיה או באמצעות מחשב או Apple TV שמנתבים אליה מהרשת המקומית. אין לפתוח את יציאת ADB בנתב לאינטרנט. [מדריך חיבור מרחוק](docs/REMOTE.md)

</details>

<details>
<summary>האם גישה דרך ADB פוגעת באבטחה?</summary>

ADB ברשת ביציאה 5555 אינו מוצפן. בחלק מהטלוויזיות כל מכשיר ברשת המקומית יכול לקבל גישה למעטפת הפקודות. כשמסיימים, מכבים את מתגי ניפוי השגיאות הזמינים באפשרויות למפתחים ומבטלים הרשאות ניפוי קודמות. מאתחלים ובודקים מהמחשב באמצעות <code dir="ltr">python3 tools/adb-closed.py &lt;tv ip&gt;</code> שהיציאה סגורה. מסך הבית ממשיך לפעול גם לאחר הכיבוי. [מידע נוסף](docs/faq.md#isnt-adb-access-a-security-hole)

</details>

<details>
<summary>אפשר לחסום פרסומות גם בתוך אפליקציות?</summary>

חלקית. ב־Android 9 ומעלה אפשר להשתמש ב־DNS פרטי, למשל [AdGuard DNS](https://adguard-dns.io), כדי לחסום דומיינים של פרסום ומעקב בכל המערכת. ספק ה־DNS יקבל את השאילתות של הטלוויזיה, והשיטה אינה מסירה פרסומות ב־YouTube. [הגדרה ושחזור](docs/faq.md#can-it-block-ads-inside-apps-too)

</details>

<details>
<summary>אילו הגדרות כדאי לבדוק בטלוויזיה עצמה?</summary>

אפשר לבדוק את מזהה הפרסום, הרשאות המיקום, שימוש ואבחון, סריקה זמינה תמיד ומצב אפליקציות בלבד. כדאי לשנות את ״שימוש ואבחון״ לפני השבתת חבילות, כי בחלק מהדגמים הדף הזה נעלם בעקבותיה. [נתיבים לפי מערכת](docs/faq.md#are-there-switches-in-the-tvs-own-settings-worth-flipping-too)

</details>

<details>
<summary>יש תמיכה ב־AirPlay?</summary>

הפרויקט אינו כולל שרת AirPlay. ‏Chromecast המובנה נשאר זמין לטלפוני Android. ל־iPhone אפשר להשתמש ב־[android-airplay-server](https://github.com/jqssun/android-airplay-server). [סקריפט התקנה מוצע](https://github.com/Gathin23/cleanyourtv/pull/84)

</details>

<details>
<summary>אפשר להוריד APK מוכן?</summary>

הפרויקט מספק קוד מקור לבנייה מקומית. משתמשים בפקודת הבנייה ב[התקנה הידנית](#התקנה-ידנית) עם ה־JDK וה־SDK שמפורטים שם.

</details>

## הסיפור מאחורי הפרויקט

ב־17 באוקטובר 2026 פרסם יוצר הפרויקט [תמונות לפני ואחרי](https://x.com/gathint/status/2111379569030939102) של הטלוויזיה של הוריו, אחרי ניקוי באמצעות Claude Code דרך Wi-Fi. הפרסום הגיע ל־8.6 מיליון צפיות. בתוך ארבעה ימים הקהילה תרמה רשימות ליותר מ־170 דגמים, בעזרת יותר מ־200 תורמים. עד 19 באוקטובר המאגר נקרא tv-vibehack, והקישורים הישנים עדיין עובדים.

בשבוע של 19–25 באוקטובר המאגר הגיע למקום הראשון ברשימת המאגרים הטרנדיים ב־GitHub. נכון ל־30 באוקטובר 2026 הוא צבר יותר מ־46 אלף כוכבים ו־5,700 פיצולים, ו־1.4 מיליון צפיות ו־260 אלף שכפולים בתוך 14 ימים. הרשימות כיסו 463 דגמים מ־124 מותגים. סיקור בתקשורת: 412 כתבות מ־54 מדינות ו־229 סרטונים ב־YouTube וב־Bilibili. [docs/PRESS.md](docs/PRESS.md)

## תרומה לפרויקט

התרומה השימושית ביותר היא רשימת השבתה שנבדקה על דגם הטלוויזיה שלכם. אפשר גם לשפר את מסך הבית או את ההנחיות לעוזר הקוד. אם בניתם מסך בית, ערכת עיצוב, כלי או גרסה לפלטפורמה אחרת על בסיס הפרויקט, אפשר להוסיף אותם ל־[SHOWCASE.md](SHOWCASE.md).

[מדריך תרומה ובדיקות](CONTRIBUTING.md) · [הנחיות לעוזרי קוד](AGENTS.md)

<a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://contrib.rocks/image?repo=Gathin23/cleanyourtv&columns=16&max=400" alt="התורמים לפרויקט"></a>

<details>
<summary>היסטוריית הכוכבים ב־GitHub</summary>

<a href="https://star-history.com/#Gathin23/cleanyourtv&Date"><img src="https://api.star-history.com/svg?repos=Gathin23/cleanyourtv&type=Date" alt="תרשים היסטוריית הכוכבים" width="640"></a>

</details>

## יצירת קשר

| נושא | איפה פונים |
|---:|---:|
| שאלות על הטלוויזיה | [GitHub Discussions](https://github.com/Gathin23/cleanyourtv/discussions) |
| עיתונות ופניות | [hello@itsgathin.com](mailto:hello@itsgathin.com) |
| בעיות אבטחה | מדווחים בפרטי דרך [לשונית האבטחה](https://github.com/Gathin23/cleanyourtv/security), לפי [SECURITY.md](SECURITY.md). |

## תנאי שימוש

- CleanYourTV הוא פרויקט קוד פתוח עצמאי, ללא קשר מסחרי, חסות או אישור מצד יצרני הטלוויזיות והפלטפורמות. שמות המותגים והדגמים משמשים לזיהוי הטלוויזיות שנבדקו והם סימני המסחר של בעליהם.
- הכלים משתמשים באפשרויות שהטלוויזיה מעמידה לרשות בעליה: אפשרויות למפתחים, ניפוי שגיאות ADB והשבתת חבילות באמצעות <code dir="ltr">pm disable-user</code>. הם אינם מבצעים root, צורבים קושחה או עוקפים נעילות, DRM או הגנות העתקה. המאגר אינו כולל תוכנות יצרן, קושחה או קובצי APK.
- החבילות מושבתות, ואפשר להחזיר את המצב באמצעות <code dir="ltr">tools/restore.sh</code>. השימוש בטלוויזיה שבבעלותכם ובאחריותכם. שינוי תוכנת הטלוויזיה עשוי להשפיע על האחריות או התמיכה.
- התוכן והכלים ניתנים כפי שהם, ללא אחריות מכל סוג, לפי רישיון MIT.

## רישיון

הפרויקט מופץ תחת [רישיון MIT](LICENSE). הגופן Outfit מופץ תחת [SIL Open Font License](launcher/FONT-LICENSE-OFL.txt).

</div>
