<p align="center">
  <a href="https://cleanyour.tv"><img src="https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/banner.png" alt="CleanYourTV. Votre téléviseur, sans les publicités. Retirez les applications superflues de n'importe quel Android TV ou Google TV par Wi-Fi et remplacez l'écran d'accueil rempli de publicités par un launcher épuré, au style Apple TV. Sans root, sans câble, rien de désinstallé, et une restauration en une ligne." width="800"></a>
</p>

<p align="center">
  Lire en : <a href="README.md">English</a> · <a href="README.he.md" lang="he" dir="rtl">עברית</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">正體中文</a> · <a href="README.vi.md">Tiếng Việt</a> · <a href="README.ja.md">日本語</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português</a> · <a href="README.nl.md">Nederlands</a> · <a href="README.de.md">Deutsch</a> · <a href="README.uk.md">Українська</a> · <a href="README.tr.md">Türkçe</a> · <a href="README.uz.md">Oʻzbekcha</a> · Français · <a href="README.es.md">Español</a>
</p>

<p align="center">
  <a href="https://github.com/Gathin23/cleanyourtv/stargazers"><img src="https://img.shields.io/github/stars/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Étoiles"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/pulls?q=is%3Amerged"><img src="https://img.shields.io/github/issues-search?query=repo%3AGathin23%2Fcleanyourtv%20is%3Apr%20is%3Amerged&style=flat&cacheSeconds=1800&label=merged%20PRs&color=cfffcf&labelColor=15181e" alt="PR fusionnées"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/network/members"><img src="https://img.shields.io/github/forks/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Forks"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://img.shields.io/github/contributors/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Contributeurs"></a>
  <a href="https://buymeacoffee.com/gathint"><img src="https://img.shields.io/badge/support-%E2%9D%A4-ff5e8a?style=flat&labelColor=15181e" alt="Soutenir le projet, offrir un café"></a>
  <a href="https://www.producthunt.com/products/cleanyourtv"><img src="https://img.shields.io/badge/Product%20Hunt-upvote%20%E2%96%B2-da552f?style=flat&logo=producthunt&logoColor=white&labelColor=15181e" alt="CleanYourTV sur Product Hunt"></a>
</p>

<p align="center">
  <a href="https://trendshift.io/repositories/310225"><img src="docs/trending-badge.png" alt="Dépôt n° 1 des tendances de la semaine, du 19 au 25 octobre 2026, via trendshift.io" width="360"></a>
</p>

# CleanYourTV : guide en français

Connectez-vous à un Android TV ou un Google TV par le Wi-Fi de votre domicile, désactivez les applications
préinstallées, les publicités et les services de collecte de données dont vous ne vous servez pas, puis
installez un écran d'accueil (launcher) épuré sur les téléviseurs compatibles.

Pas besoin de root, de reflasher le firmware ni de câble USB. Les applications système sont seulement
**désactivées**, jamais supprimées, et un script permet toujours de les rétablir.

Cette page présente les principales fonctionnalités et la façon de les utiliser. Chaque modèle de téléviseur,
chaque région et chaque version de firmware a ses propres applications et ses propres limites : vérifiez
d'abord ce qui concerne votre téléviseur.

En cas de divergence, le [README en anglais](README.md) fait foi. Les remarques propres à chaque modèle se
trouvent dans la [liste des téléviseurs pris en charge](examples/README.md).

> [!WARNING]
> **Votre téléviseur est un Samsung, un LG, un Roku ou une autre marque qui n'utilise pas Android ?**
> <br>CleanYourTV ne fonctionne pas sur ces appareils, mais vous pouvez quand même couper le pistage étape par étape, avec la seule télécommande : [cleanyour.tv/not-android](https://cleanyour.tv/not-android).

![Comparaison entre l'écran d'accueil d'origine de Google TV et le launcher CleanYourTV](docs/stock-vs-new-side.jpg)

## Par où commencer

| Vous avez | Allez à |
|---|---|
| **Un Android TV ou un Google TV** | [Démarrage rapide](#démarrage-rapide) : un assistant de code fait le travail en une quinzaine de minutes. |
| **Un téléviseur Samsung, LG, Roku, Vizio ou Hisense VIDAA** | [cleanyour.tv/not-android](https://cleanyour.tv/not-android) : CleanYourTV ne fonctionne pas dessus, mais vous pouvez couper le pistage avec votre télécommande. |
| **Un téléphone ou un ordinateur portable, sans agent de code** | L'une des [applications de la communauté](https://cleanyour.tv/app) pour téléphones Android, Mac et Windows, créées par des développeurs indépendants. |
| **Envie de voir le résultat d'abord** | [Ce que vous obtenez](#ce-que-vous-obtenez) : des captures d'écran du nouvel écran d'accueil. |

**Liens officiels :** [cleanyour.tv](https://cleanyour.tv) et ce dépôt. Les autres domaines qui utilisent ce nom,
comme cleanyourtv.com, ne sont pas gérés par ce projet ([#1004](https://github.com/Gathin23/cleanyourtv/issues/1004)).
CleanYourTV ne publie aucune application dans aucune boutique, et clean-your-tv.net ne nous appartient pas non plus.

[Comment ça marche](#comment-ça-marche) · [Démarrage rapide](#démarrage-rapide) · [Ce que vous obtenez](#ce-que-vous-obtenez) ·
[Téléviseurs pris en charge](#téléviseurs-pris-en-charge) · [Le faire à la main](#le-faire-à-la-main) · [Le launcher](#le-launcher) ·
[Restauration](#restaurer-votre-téléviseur) · [Mises à jour du firmware](#après-une-mise-à-jour-du-firmware) ·
[Questions](#questions-fréquentes) · [Contribuer](#contribuer)

## Comment ça marche

Les téléviseurs Android disposent du même pont de débogage que les téléphones Android. Activez le débogage USB
dans les options pour les développeurs du téléviseur, autorisez votre ordinateur une seule fois, et tout le
reste relève de l'outillage Android standard :

| Étape | Ce qui se passe |
|---|---|
| **Sauvegarder** | La liste des paquets installés et les réglages. |
| **Désactiver** | La télémétrie du fabricant, les services publicitaires, les rangées de contenus et les applications préinstallées inutiles, avec `pm disable-user`. Rien n'est désinstallé, si bien qu'un script de restauration peut annuler chaque étape. |
| **Installer** | Un petit launcher natif, défini comme écran d'accueil par défaut. |

**Sans root, sans câble, rien de désinstallé, et une restauration en une ligne si vous changez d'avis.**

Les applications superflues varient selon la marque : le prompt demande donc à votre assistant de code de les
repérer sur votre téléviseur et de vous demander votre avis avant de toucher à quoi que ce soit que vous
pourriez utiliser.

## Démarrage rapide

| Vous avez | Faites ceci |
|---|---|
| **Un ordinateur** | Utilisez le prompt avec un assistant de code : les trois étapes ci-dessous. |
| **Un ordinateur, sans assistant de code** | Suivez vous-même les trois étapes : les commandes sont dans [Le faire à la main](#le-faire-à-la-main). |
| **Un téléphone ou un ordinateur portable, sans assistant de code** | Choisissez l'une des [applications de la communauté](https://cleanyour.tv/app) : des applications gratuites et open source, construites sur ces listes de téléviseurs par des développeurs indépendants. |

Les trois étapes ci-dessous sont écrites pour un assistant de code : l'ordinateur doit être sur le même Wi-Fi
que le téléviseur et disposer d'un shell, de Python 3, d'un JDK de la version 17 à la version 22, et de la
plateforme 34 du SDK Android avec les build-tools 34 (c'est là que l'assistant compile le launcher).
Sans assistant, [Le faire à la main](#le-faire-à-la-main)
décrit le même travail, mené par vous, avec les commandes d'installation.

N'importe lequel de ceux-ci convient : Claude Code (sur [0G Private Computer](https://pc.0g.ai/?utm_source=cleanyourtv&utm_medium=github&utm_campaign=readme) ou avec un [abonnement Anthropic](https://claude.com/pricing)), [Codex](https://github.com/openai/codex), [Gemini CLI](https://github.com/google-gemini/gemini-cli), [Antigravity CLI](https://antigravity.google/product/antigravity-cli) ou [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness).

| Où | Que faire |
|---|---|
| **1. Sur le téléviseur** | Paramètres → Préférences de l'appareil → À propos → appuyez sept fois sur OK sur **Build** → retour → Options pour les développeurs → activez le **Débogage USB**. Si vous voyez une option Débogage réseau ou Débogage ADB, activez-la aussi. Laissez le téléviseur allumé. |
| **2. Sur votre ordinateur** | Clonez le dépôt et ouvrez ce dossier dans votre assistant (`git clone https://github.com/Gathin23/cleanyourtv`), puis collez [`PROMPT.md`](PROMPT.md) en indiquant le modèle de votre téléviseur. |
| **3. Suivez le déroulement** | Acceptez « Autoriser le débogage USB ? » sur le téléviseur, puis répondez à la liste de questions : quelles applications vous utilisez, la taille du dock et l'ordre des applications, et s'il faut garder l'assistant vocal, Chromecast et les mises à jour. |

Le clonage compte : l'assistant réutilise les scripts `tools/disable.sh` et `launcher/build-and-install.sh` de ce
dépôt au lieu d'écrire les siens, et c'est là que se trouvent la sauvegarde, la protection de l'écran d'accueil
et l'enregistrement servant à la restauration.

L'assistant sauvegarde, nettoie, compile et installe le launcher, et vérifie chaque étape avec des captures
d'écran récupérées sur le téléviseur. Avec ce dépôt comme contexte, cela prend une quinzaine de minutes.

## Ce que vous obtenez

Un écran d'accueil au style Apple TV, qui sait se faire oublier :

| Élément | À quoi il ressemble |
|---|---|
| **Un accueil noir profond** | Un dock de vos quatre applications préférées dans un bandeau arrondi, là où Apple TV le place. |
| **Une étagère supérieure** | Affiche en grands caractères le nom de l'application sélectionnée, avec un halo d'ambiance dont la couleur est tirée de son illustration. |
| **Le reste de vos applications en grille** | Elle remonte quand vous appuyez sur Bas. |
| **Un seul reflet de verre flottant** | Il bondit de vignette en vignette. La vignette sélectionnée se soulève et affiche son nom. |
| **Trois boutons en verre dépoli** | Entrées (les ports HDMI sous les noms que vous leur avez donnés dans les réglages du téléviseur, AV, TV en direct), Wi-Fi ou Ethernet avec le nom du réseau, Paramètres. |
| **Un message d'accueil, une horloge fine et la date** | En police [Outfit](https://fonts.google.com/specimen/Outfit), avec un économiseur d'écran Horloge assorti. Activez `show_stats` dans la configuration ou dans un thème pour afficher une ligne sous l'horloge : CPU, mémoire libre et stockage libre. Elle reste désactivée tant que vous ne l'activez pas, et ne se rafraîchit que lorsque l'accueil est à l'écran. |
| **Thèmes et cartes de modules** | Les thèmes changent les couleurs, le fond d'écran, le dock et les colonnes sans recompilation, et les cartes de modules permettent à d'autres applications d'ajouter une vignette, uniquement avec votre accord. |

Et un téléviseur qui cesse de jouer contre vous : plus de rangées sponsorisées, plus de rappels « Découvrir »,
plus d'analytique qui renvoie des données, plus d'applications que vous n'avez jamais demandées.

| | |
|---|---|
| ![L'étagère supérieure affiche le nom de l'application sélectionnée sur un halo de sa couleur](docs/focus-2-up-netflix.png) | ![La fenêtre Entrées : ports HDMI avec ce qui y est branché, AV et TV en direct](docs/inputs-menu.jpg) |
| *Étagère supérieure et halo d'ambiance* | *Entrées : HDMI avec l'état de connexion, AV, TV en direct* |
| ![La grille avec le reflet de verre sur une vignette et son nom en dessous](docs/focus-1-fancode.png) | ![L'économiseur d'écran Horloge : une horloge fine qui dérive sur le halo](docs/clock-screensaver.png) |
| *La grille, un seul reflet de verre, le nom de la vignette* | *Économiseur d'écran Horloge : il se déplace pour que rien ne marque l'écran* |
| ![Le thème aurora : un fond d'écran et des boutons teintés, envoyés sans recompilation](themes/aurora/screenshot.jpg) | ![Six colonnes et un dock de six applications, depuis le config.json d'un thème](themes/six-columns/screenshot.jpg) |
| *Thème Aurora, envoyé avec `tools/theme.sh`* | *Six colonnes et un dock de six applications, même launcher* |
| ![Déplacer une vignette : OK maintenu, Déplacer choisi, la vignette est saisie et les autres s'assombrissent](docs/tile-move.jpg) | ![La bibliothèque d'applications : les applications installées, une pression remet une application sur l'accueil](docs/app-library.jpg) |
| *Maintenez OK sur une vignette, puis Déplacer : les flèches la portent, Retour la pose* | *Bibliothèque d'applications : une pression remet sur l'accueil une application retirée* |

## Téléviseurs pris en charge

Tout appareil sous **Android TV** ou **Google TV**.

| Plateforme | Ce qui fonctionne |
|---|---|
| **Android TV, Google TV** | Le nettoyage et le launcher. |
| **Fire TV** | Seulement la partie nettoyage, car Fire OS ne cède pas l'écran d'accueil à un launcher tiers. Et sur la Fire TV Box de 2015 (Fire OS 5), même cette partie échoue : toutes les voies `pm` sont refusées. |
| **Quelques modèles TCL destinés au marché chinois** | Ils refusent d'installer le moindre launcher ; leurs listes le signalent dans l'en-tête. |
| **Samsung (Tizen), LG (webOS), Roku, Vizio, Hisense VIDAA** | Non pris en charge (pas d'ADB). Coupez plutôt le pistage : [cleanyour.tv/not-android](https://cleanyour.tv/not-android). Voir [SHOWCASE.md](SHOWCASE.md#other-platforms-fire-tv-and-lg-webos) pour quatre portages webOS, et [docs/devices/samsung-tizen.md](docs/devices/samsung-tizen.md) pour ce qu'un téléviseur Samsung de 2021 répond par le Wi-Fi (pas d'ADB, donc pas de `pm disable-user`) si vous envisagez un portage Tizen. |

Des listes testées par les contributeurs, un dossier par modèle dans [`examples/`](examples/).

**Trouvez votre téléviseur :** cherchez sur [cleanyour.tv/tvs](https://cleanyour.tv/tvs) ou dans le tableau complet de
[examples/README.md](examples/README.md).

Votre téléviseur n'y figure pas ? Le prompt repère lui-même les applications superflues. Ajoutez ensuite votre
liste, et la prochaine personne qui a votre modèle sautera cette étape : [CONTRIBUTING.md](CONTRIBUTING.md).

## Le faire à la main

Tout ce que fait l'assistant, vous pouvez le lancer vous-même. Deux choses doivent d'abord être présentes sur
l'ordinateur :

| Il vous faut | Pour |
|---|---|
| **Python 3** (n'importe quelle 3.x) | Les outils. La commande venv ci-dessous installe ce qu'ils importent. |
| **JDK 17 à 22** (un JDK complet, avec `jlink`) et **la plateforme 34 du SDK Android** avec **les build-tools 34.0.0 ou toute 34.x plus récente** | Compiler le launcher. À partir du JDK 23, Gradle 8.9 ne fonctionne plus. La plateforme 34 est celle que lit la compilation ; une plateforme plus récente ne la remplace pas. Sur macOS, si rien de tout cela n'est installé : `brew install openjdk@17 android-commandlinetools`, puis `sdkmanager "platforms;android-34" "build-tools;34.0.0"`. Sous Linux, le script d'installation cherche dans `~/Android/Sdk` et cherche `java` dans votre PATH. |

`tools/tv.py` communique avec l'ADB réseau sur le port 5555. Si ce port reste fermé et que le téléviseur propose
le Débogage sans fil avec un code d'association (c'est le cas du Chromecast avec Google TV et du Google TV
Streamer), associez-le une fois avec l'`adb` standard. Ensuite, soit vous lancez `adb tcpip 5555` pour ouvrir le
port, ce qui ne dure en général que jusqu'au prochain redémarrage du téléviseur, soit vous le laissez fermé :
faites pointer `TV_ADB_BINARY` vers ce même `adb` et `TV_HOST` vers le port qu'affiche l'écran Débogage sans fil,
et les outils joignent le téléviseur par la connexion associée, sans que rien n'écoute sur le 5555
([tools/native-adb.md](tools/native-adb.md)). Le [dépannage](docs/TROUBLESHOOTING.md#connecting)
décrit les deux méthodes, avec les étapes d'association.

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # l'adresse IP de votre téléviseur

tools/venv/bin/python -I tools/tv.py --probe                 # modèle, version d'Android, firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # une commande shell sur le téléviseur
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # appliquer une liste de nettoyage
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # l'annuler, launcher d'origine rétabli
tools/venv/bin/python -I tools/check.py examples/xiaomi-mi-tv-4a/debloat.txt   # ce qu'une mise à jour du firmware a défait
launcher/build-and-install.sh                                # compiler le launcher et en faire l'accueil (nécessite l'export TV_HOST ci-dessus)
tools/theme.sh themes/aurora                                 # envoyer un thème ; --reset le retire
tools/venv/bin/python -I tools/wallpaper_prep.py ~/Pictures/trip mytheme/wallpapers   # vos photos, dimensionnées et assombries pour le téléviseur
```

| Outil | Ce qu'il fait |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Client ADB en pur Python : shell, lots de commandes, envoi, récupération, redémarrage et captures d'écran, avec la possibilité d'appuyer d'abord sur des touches de la télécommande. Fonctionne là où le binaire `adb` standard est bloqué. [Backend ADB Google associé](tools/native-adb.md) en option pour le Débogage sans fil chiffré. |
| [`tools/docker-build.sh`](tools/docker-build.sh) | Compile le launcher ou un add-on dans Docker (JDK 17, SDK Android 34) : cet ordinateur n'a besoin que de Docker. Installez le résultat avec `SKIP_BUILD=1`. [Guide](tools/docker-build.md). |
| [`tools/b64push.py`](tools/b64push.py) | Envoie de gros fichiers, comme des APK, par morceaux en base64 et vérifie la somme de contrôle. |
| [`tools/relay.py`](tools/relay.py) | Redirige un port local vers le port ADB du téléviseur, pour les assistants dont le bac à sable ne peut pas atteindre votre réseau. N'écoute que sur cet ordinateur. [Guide](tools/relay.md). |
| [`tools/disable.sh`](tools/disable.sh), [`restore.sh`](tools/restore.sh) | Appliquent ou annulent une liste de nettoyage, l'annulation partant de l'état enregistré avant le changement. |
| [`tools/recovery.py`](tools/recovery.py) | Écrit l'enregistrement dont `disable.sh` a besoin pour s'annuler, et affiche le lot de restauration. |
| [`tools/check.py`](tools/check.py) | Compare le téléviseur à la liste appliquée, pour voir ce qu'une mise à jour du firmware a défait ; `--fix` le remet en place. |
| [`tools/match.py`](tools/match.py) | Lit un `pm list packages` enregistré et affiche, pour chaque paquet, combien de listes d'`examples/` le désactivent, combien le laissent en commentaire, et leur remarque la plus fréquente ; les paquets centraux d'Android apparaissent comme `never`. Hors ligne, ne modifie rien (`tools/venv/bin/python -I tools/match.py packages.txt`). |
| [`tools/adb-closed.py`](tools/adb-closed.py) | Vérifie que le port ADB réseau du téléviseur est fermé une fois le débogage coupé : code de sortie 0 quand le téléviseur refuse la connexion, 1 tant qu'il est encore ouvert, 2 quand rien n'a répondu ou que la vérification n'a pas pu s'exécuter. Pas besoin de `nc`. |
| [`tools/philips-api.py`](tools/philips-api.py) | Philips uniquement : vérifie que l'API de contrôle JointSpace qu'utilise Home Assistant répond toujours, avant et après un nettoyage (`tools/venv/bin/python -I tools/philips-api.py <tv ip>`). Le port 1926 en HTTPS d'abord, puis le 1925 ; ne lit que ce qui répond sans association. |
| [`tools/privacy.py`](tools/privacy.py) | Audite le téléviseur à la recherche de candidats voix, ACR et télémétrie, et affiche les éléments de preuve pour chacun ; `--write` transforme les résultats en une liste que `disable.sh` et `restore.sh` savent déjà lire. |
| [`tools/theme.sh`](tools/theme.sh), [`theme_restart.py`](tools/theme_restart.py) | Envoient un dossier de [`themes/`](themes/) au launcher et redémarrent l'accueil. |
| [`tools/wallpaper_prep.py`](tools/wallpaper_prep.py) | Transforme un dossier de vos propres photos (JPEG, PNG, WebP, HEIC) en JPEG 1280×720 assombris de moins de 300 Ko, pour le dossier `wallpapers/` d'un thème. Nécessite Pillow, plus pillow-heif pour le HEIC (`tools/requirements-photos.txt`). Ne touche jamais aux originaux. [Mode d'emploi](#le-launcher). |
| [`tools/manage.py`](tools/manage.py) | Liste chaque add-on, thème et option avec sa capture d'écran, montre ce qui est sur le téléviseur, et en installe, met à jour ou retire un en lançant le propre script de son auteur. |
| [`tools/bench.py`](tools/bench.py) | Mesure le launcher qui répond à HOME : latence des touches, distance parcourue par la sélection, images saccadées, démarrage à froid, retour à l'accueil, et le CPU et la PSS consommés au repos. Ne modifie rien sur le téléviseur et écrit les mesures brutes par touche en JSON ; aucune installation supplémentaire. |
| [`tools/trace.py`](tools/trace.py), [`trace_report.py`](tools/trace_report.py) | Enregistrent des traces Perfetto du launcher (retour à l'accueil, démarrage à froid, croix directionnelle) et indiquent où passe son temps, par exécution et en avant/après. Facultatif : nécessite [`requirements-perf.txt`](tools/requirements-perf.txt). [Guide](tools/trace.md). |
| [`tools/flamechart.py`](tools/flamechart.py) | Dessine un flame chart avant/après à partir de ces traces, pour une pull request : une ligne par thread sur une échelle de temps commune, le travail visé par un changement en magenta avec son total. Facultatif, comme les outils de trace. [Guide](tools/flamechart.md). |
| [`tools/screendiff.py`](tools/screendiff.py) | Compare pixel par pixel une capture avant et une capture après, en ignorant l'horloge ou tout ce que vous masquez, et écrit la paire avec le nom de votre Wi-Fi flouté. Facultatif, comme les outils de trace. [Guide](tools/screendiff.md). |
| [`tools/race_calendar.py`](tools/race_calendar.py) | Écrit le calendrier F1 de la saison dans un thème pour le mode week-end de course, à partir de l'API gratuite Jolpica. |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | Compile une version release allégée (environ 572 Ko), l'installe, bascule l'accueil, et revient en arrière si l'accueil ne pointe pas dessus. |

Les scripts ne laissent jamais le téléviseur sans application d'accueil : ils ne désactivent qu'un launcher
d'origine qu'ils reconnaissent, et ignorent l'application d'accueil présente dans une liste tant qu'un autre
launcher ne répond pas à HOME. Vos propres listes peuvent se trouver hors du dépôt : les chemins de liste sont
relatifs au dossier depuis lequel vous lancez la commande, et les chemins absolus fonctionnent aussi.

<details>
<summary><b>Windows (PowerShell)</b></summary>

Chaque script a un jumeau PowerShell à côté de lui. Installez Python 3 et un JDK de la version 17 à la
version 22, et ajoutez dans le SDK Manager la plateforme 34 et les build-tools 34 (34.0.0 ou toute 34.x plus
récente). Le script utilise `JAVA_HOME` lorsqu'il pointe vers un JDK de cette plage, et sinon le JDK fourni avec
Android Studio lorsque celui-ci est dans la même plage :

```powershell
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
```

Les deux dernières lignes sont l'annulation et l'envoi d'un thème, les équivalents des lignes `tools/restore.sh`
et `tools/theme.sh` que les sections ci-dessus donnent en bash. Sur Google TV, l'annulation prend aussi le
launcher d'origine : `-Stock com.google.android.apps.tv.launcherx`.

</details>

<details>
<summary><b>Comment les scripts protègent l'écran d'accueil</b></summary>

Les scripts d'installation demandent au téléviseur quel launcher détient l'accueil et ne désactivent que ceux
qu'ils reconnaissent, les quatre de `KNOWN_STOCK_LAUNCHERS` : `com.google.android.tvlauncher`,
`com.google.android.apps.tv.launcherx`, `com.google.android.leanbacklauncher` et `com.mitv.tvhome` de Xiaomi
(PatchWall). Passez `STOCK_LAUNCHER=<package>` (`-StockLauncher`) pour forcer un autre choix, ou `none` pour ne
pas y toucher.

`tools/disable.sh` (`disable.ps1`) applique la même règle avant de désactiver quoi que ce soit :

- Si la liste contient l'application qui détient l'accueil et qu'aucune autre application du téléviseur ne
  répond à HOME, il ignore cette entrée, explique pourquoi, et désactive le reste de la liste. Une liste
  appliquée avant le passage de relais au nouveau launcher ne peut donc pas laisser le téléviseur sans rien à
  démarrer.
- L'assistant de configuration, les applications de réglages, un `FallbackHome` du fabricant et les applications
  de test d'usine (`com.duokan.factorytest` de Xiaomi) répondent aussi à HOME et ne comptent pas.
- Sur Google TV, il conserve aussi le détenteur de l'accueil tant que setupwraith répond à HOME et reste activé
  (absent de la liste, ou conservé pour le réseau Wi-Fi) : setupwraith répond avant un launcher installé qui
  n'est pas encore l'accueil, et prendrait donc l'accueil.

Relancez la même liste une fois que `launcher/build-and-install.sh` a fait du nouveau launcher l'accueil, et
l'entrée est appliquée ; `FORCE_HOME=1` (`$env:FORCE_HOME`) la désactive malgré tout.

</details>

## Le micro, et ce qu'on peut en dire

« Lequel de ces paquets écoute ? » n'a pas de réponse honnête en une ligne : une analyse des permissions montre
une cinquantaine de paquets système avec `RECORD_AUDIO` qui n'enregistrent rien, et les clients ACR voient
l'écran sans aucune permission audio. [`tools/privacy.py`](tools/privacy.py) rapporte ce que le téléviseur dit
réellement, avec les éléments de preuve pour chaque candidat voix, ACR et télémétrie :
`TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/privacy.py`, et `--write` les transforme en une liste de
nettoyage normale. Ce que l'audit ne peut pas faire, c'est prouver une intention : [docs/privacy.md](docs/privacy.md)
explique pourquoi.

## Le launcher

Un petit écran d'accueil natif pour Android TV : **environ 572 Ko**, en Java simple, conçu pour des téléviseurs
avec 1 Go de RAM. Pas de flou, pas de vidéo, pas de permission réseau, pas de services en arrière-plan. Le
**téléviseur** doit être sous Android 8.0 (API 26) ou plus récent, le `minSdk` de
[`launcher/app/build.gradle`](launcher/app/build.gradle) ; ce qui reste possible sur un modèle plus ancien est
décrit dans le [dépannage](docs/TROUBLESHOOTING.md).

| Quoi | Comment ça marche |
|---|---|
| **Configuration** | Ordre du dock, applications masquées, application de TV en direct, de 4 à 7 vignettes par rangée, animations réduites. Tout se trouve dans [`config.xml`](launcher/app/src/main/res/values/config.xml) |
| **Réglages sur le téléviseur** | Maintenez OK sur Paramètres pour ouvrir les réglages du launcher. L'option `launcher_settings_button` permet de les ouvrir directement d'un simple clic ; les réglages du téléviseur restent accessibles depuis la page des réglages et leur vignette d'application. |
| **Thèmes** | Arrière-plan, fond d'écran et disposition à partir d'un `config.json` envoyé par Wi-Fi, sans recompilation. 29 thèmes sont fournis dans [`themes/`](themes/), y compris le `default/` intégré |
| **31 langues** | Suit la langue du téléviseur, avec une disposition de droite à gauche entièrement inversée pour l'hébreu |
| **Bibliothèque d'applications** | Facultative : parcourez toutes les applications installées et ajoutez ou retirez des raccourcis sur l'accueil. Voir [le guide de la bibliothèque d'applications](docs/app-library.md) |
| **Menu des vignettes** | Activé par défaut (`tile_menu`) : maintenez OK sur une vignette pour Déplacer et Infos sur l'appli, plus Retirer de l'accueil lorsque la bibliothèque d'applications est activée. Désactivé, maintenir OK ouvre Infos sur l'appli |
| **Cartes de modules** | N'importe quelle application TV peut proposer sa propre vignette ; vous approuvez chacune une seule fois. Voir [`docs/modules.md`](docs/modules.md) |
| **Add-ons** | Des applications facultatives qui se branchent comme cartes de modules, rangées dans [`addons/`](addons/) et jamais intégrées au launcher |
| **Économiseur d'écran Horloge** | Une horloge qui dérive sur le halo, pour que rien ne marque l'écran |
| **Touches de la télécommande** | Les boutons Entrée, YouTube et engrenage continuent de fonctionner une fois le launcher d'origine écarté |

Thèmes, options et add-ons réunis, avec captures d'écran : [cleanyour.tv/addons](https://cleanyour.tv/addons).

**Pour aller plus loin :** [docs/launcher.md](docs/launcher.md) détaille la page des réglages du launcher ligne par
ligne, le changement d'accueil par défaut, les langues, les touches de la télécommande, l'économiseur d'écran
Horloge, la navigation et la compilation par vous-même.

<details>
<summary><b>Vos propres photos en arrière-plan</b></summary>

Un thème peut faire défiler un dossier d'images derrière l'accueil, sans recompilation. Aucune image n'est fournie
avec le dépôt : vous apportez les vôtres, et c'est à vous de vous assurer que vous avez le droit de les afficher.

1. **Créez un dossier de thème.** Copiez un thème qui vous plaît depuis le [dossier des thèmes](themes/), par exemple
   `themes/aurora`, dans un nouveau dossier comme `mytheme/`, ou partez d'un `config.json` vide.
2. **Préparez les photos.** Une photo lumineuse de 12 Mpx est lourde pour un téléviseur de 1 à 2 Go et noie
   l'horloge et les vignettes. `tools/wallpaper_prep.py` recadre au centre en 16:9, enregistre des JPEG 1280×720 de
   moins de 300 Ko, et assombrit l'image elle-même, si bien que `wallpaper_dim` peut rester à 0. Les photos HEIC des
   téléphones fonctionnent. Vos originaux ne sont jamais modifiés. Pillow s'installe dans le `tools/venv`
   créé par le démarrage rapide, et non dans un Python système : celui-ci n'a pas le paquet, et sous
   Ubuntu son pip n'a pas le droit de l'installer.

   ```bash
   tools/venv/bin/pip install -r tools/requirements-photos.txt
   tools/venv/bin/python -I tools/wallpaper_prep.py ~/Pictures/trip mytheme/wallpapers
   ```

   Options : `--brightness 0.25` (de 0.1 à 1.0), `--gradient 0.6` (davantage de noir vers le dock), `--size 1280x720`,
   `--max-kb 300`.
3. **Réglez le défilement** dans `mytheme/config.json` :

   ```json
   { "wallpaper": true, "wallpaper_rotation_minutes": 1, "wallpaper_order": "shuffle", "wallpaper_dim": 0 }
   ```

   | Clé | Remarques |
   |---|---|
   | `wallpaper_rotation_minutes` | En minutes entières, de 0 à 1440. **1 est le plus rapide.** `0` signifie pas de défilement, et une fraction comme `0.25` est lue comme `0`, ce qui l'arrête. |
   | `wallpaper_order` | `shuffle` ne montre jamais deux fois de suite la même image ; `sequential` suit les noms de fichiers. |
   | `wallpaper_dim` | Voile noir, en pourcentage. Laissez à 0 si les images sont déjà assombries. |
   | `dock_glass`, `dock_opacity` | Un bandeau de dock en verre dépoli (de 30 à 100 %) pour que l'image se voie derrière les vignettes. |
   | `greeting_name` | Un prénom après le message d'accueil : « Bonsoir, Sam ». |
   | `show_stats`, `app_library`, `reduced_motion` | Une ligne de statistiques sous l'horloge, un bouton Bibliothèque d'applications, des animations plus calmes pour les GPU modestes. |

4. **Envoyez-le au téléviseur** (cela redémarre l'accueil une fois, et garde l'assistant de la touche Accueil en fonctionnement) :

   ```bash
   tools/theme.sh mytheme            # Windows : tools\theme.ps1 -TvHost 192.168.1.50 mytheme
   ```

   Sur le téléviseur, **Réglages du launcher → Fond d'écran** propose une seule image, **Aucun**, ou **Thème par
   défaut**, qui relance le défilement.
5. **Retirez-le** avec `tools/theme.sh --reset`.

Ce qui n'est pas possible : il n'y a pas de fondu enchaîné (l'image change d'un coup), pas de défilement plus rapide
qu'une minute, et l'économiseur d'écran Horloge n'affiche que l'heure et la date, jamais de photos. Sans dossier
`wallpapers/`, le thème fonctionne quand même et l'accueil garde sa couleur unie. Une image est décodée en mémoire
tant que l'accueil l'affiche : environ 4 Mo en 1080p avec la couleur 16 bits par défaut, 8 Mo avec
`wallpaper_true_color` (voir [themes/README.md](themes/README.md)) ; gardez donc des images légères sur un
téléviseur de 1 Go.

</details>

## Restaurer votre téléviseur

Conservez la liste que vous avez appliquée. Une seule ligne remet le téléviseur dans son état d'origine :

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

| Quoi | Comment |
|---|---|
| **D'abord un enregistrement** | Avant que `tools/disable.sh` ne modifie quoi que ce soit, il note l'état du téléviseur dans `~/.cleanyourtv/`. |
| **La restauration le rejoue** | Un paquet que vous aviez désactivé vous-même reste désactivé, et l'accueil revient au composant qui le détenait. |
| **Le launcher reste installé** | Supprimez-le depuis Paramètres → Applications sur le téléviseur si vous voulez qu'il disparaisse. |
| **Pas d'ordinateur ?** | Si le téléviseur est injoignable, sa propre page Paramètres → Applications permet aussi de réactiver les paquets. |

**Pour aller plus loin :** [docs/restore.md](docs/restore.md) explique ce que contient l'enregistrement, quand
l'argument du launcher d'origine compte, et ce qui se passe sans enregistrement.

## Après une mise à jour du firmware

Une mise à jour s'installe par-dessus le système nettoyé : les paquets de la liste reviennent, l'accueil repasse
au launcher d'origine, et de nouvelles applications préinstallées apparaissent, qu'aucune liste ne mentionne.
Rouvrez l'ADB comme à l'étape 1 du [Démarrage rapide](#démarrage-rapide), puis comparez le téléviseur à la liste
appliquée ; `--fix` le remet en place :

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<your tv>/debloat.txt
```

**Pour aller plus loin :** [docs/firmware-updates.md](docs/firmware-updates.md) détaille les codes de sortie, une
vérification hebdomadaire, ce que `--fix` modifie, les téléviseurs qui gardent le launcher d'origine comme
accueil, et les listes qui désactivent le programme de mise à jour.

## Quand quelque chose tourne mal

La liste complète des pièges rencontrés se trouve dans [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md). Les
trois plus importants :

- 🍎 **macOS signale « No route to host » alors que le port est ouvert** : la confidentialité Réseau local empêche l'application de terminal d'accéder au sous-réseau local, et cette permission appartient à l'application plutôt qu'au client, si bien que l'`adb` standard et `tools/tv.py` échouent de la même façon (constaté sur macOS 26). Autorisez Terminal, iTerm2 ou VS Code dans Réglages Système → Confidentialité et sécurité → Réseau local, puis quittez-le et rouvrez-le ([dépannage](docs/TROUBLESHOOTING.md#connecting)).
- ⚠️ **Ne désactivez le launcher d'origine qu'une fois le nouveau installé et en marche**, sinon l'accueil atterrit sur une application de test d'usine. Le script d'installation respecte cet ordre pour vous.
- 🔁 **Sur Google TV, l'assistant de configuration** (`com.google.android.tungsten.setupwraith`) peut prendre l'accueil et faire planter le système en boucle une fois le launcher d'origine désactivé. Désactivez-le aussi, et inscrivez-le dans votre `debloat.txt` pour que la restauration le rétablisse.
  - Mais vérifiez d'abord `dumpsys wifi | grep -o 'cname=[^ ]*'` : si le réseau Wi-Fi a été enregistré pendant la configuration, il appartient à setupwraith, et le désactiver supprime le réseau (reconnectez-vous d'abord au réseau dans les Paramètres ; `tools/disable.sh` ignore setupwraith tant que ce n'est pas fait).
  - Sur les Chromecast de Google et sur le Google TV Streamer, cela coûte aussi la diffusion depuis un téléphone, voir le [dépannage](docs/TROUBLESHOOTING.md).
  - Déjà bloqué ? `tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`. Sur certains modèles (constaté sur un TCL Smart TV Pro, Android 14), c'est sans retour : revenir ensuite à ce launcher nécessite l'ADB, ou bien gardez launcherx et utilisez `stock_home_redirect`, voir le [dépannage](docs/TROUBLESHOOTING.md#working-on-the-tv).

## Questions fréquentes

<details>
<summary><b>Je ne code pas et je n'ai pas d'ordinateur portable. Puis-je quand même le faire ?</b></summary>

Oui. Des développeurs indépendants ont créé, sur la base des listes de téléviseurs de CleanYourTV, des applications
gratuites et open source qui font le nettoyage par Wi-Fi, sans agent de code. Chacune présente d'abord un plan,
se contente de désactiver des applications, et peut tout rétablir :

| Application | Pour | Par |
|---|---|---|
| [Clean Your TV app](https://github.com/udhaykumarbala/cleanyourtv-app) | Téléphone Android | [@udhaykumarbala](https://github.com/udhaykumarbala) |
| [Tellyscope](https://github.com/man15h/tellyscope) | Téléphone Android | [@man15h](https://github.com/man15h) |
| [TidyTV](https://github.com/jolan94/tidytv) | Téléphone Android, Google Play | [@jolan94](https://github.com/jolan94) |
| [CleanMyTV](https://github.com/wingedpanther/cleanmytv) | Mac et Windows | [@wingedpanther](https://github.com/wingedpanther) |

Ces applications sont des projets indépendants, ni affiliés à CleanYourTV ou à son mainteneur, ni approuvés par
eux, et chacune est fournie en l'état sous sa propre licence. Signalez les problèmes d'une application dans le
dépôt de celle-ci. Plus d'informations sur [cleanyour.tv/app](https://cleanyour.tv/app).

</details>

<details>
<summary><b>Est-ce sans danger ? Cela peut-il briquer le téléviseur ?</b></summary>

Rien n'est flashé, rooté ni supprimé ; les paquets sont désactivés
dans le profil utilisateur. Le risque est de désactiver quelque chose dont le téléviseur dépend, c'est pourquoi
les listes sont appliquées par petits lots, avec un script de restauration prêt. La page Paramètres → Applications
du téléviseur permet de réactiver les paquets même sans les outils. Si le téléviseur ne démarre plus assez loin
pour cela, une réinitialisation d'usine depuis son menu de récupération est généralement la voie de retour.

Plus de détails : [docs/faq.md](docs/faq.md#is-this-safe-can-it-brick-the-tv)

</details>

<details>
<summary><b>Et les mises à jour du firmware ?</b></summary>

Le programme de mise à jour reste actif dans la plupart des listes, si bien que les mises à jour du firmware
arrivent comme d'habitude. Certaines listes désactivent le programme de mise à jour du téléviseur, et avec
celles-ci une mise à jour du firmware n'arrive que lorsque vous le réactivez. Quand une mise à jour s'installe,
elle passe par-dessus le système nettoyé : un paquet revient et vous relancez le script, voir
[Après une mise à jour du firmware](#après-une-mise-à-jour-du-firmware).

</details>

<details>
<summary><b>Les applications ne vont-elles pas cesser de fonctionner ?</b></summary>

Les applications de streaming continuent de se mettre à jour via le Play Store, auquel on ne touche pas.
Le launcher se contente de lister ce qui est installé et de le lancer avec des intents stables depuis une décennie.

</details>

<details>
<summary><b>Comment réorganiser mes applications ?</b></summary>

Maintenez OK sur **Paramètres** (en haut à droite) et choisissez **Réorganiser les applications**. OK saisit une
application, les flèches la déplacent, Retour la pose, et Retour une seconde fois termine. Maintenir OK sur une
vignette propose aussi **Déplacer** (`tile_menu`, activé par défaut), qui saisit directement cette application.
Plus de détails : [docs/launcher.md](docs/launcher.md#launcher-settings-and-the-default-home)

</details>

<details>
<summary><b>Puis-je le faire pour quelqu'un qui habite ailleurs ?</b></summary>

Oui, par une liaison [Tailscale](https://tailscale.com) privée
au lieu du même Wi-Fi : soit Tailscale sur son téléviseur, partagé avec vous, soit un ordinateur ou une Apple TV
chez cette personne qui fait le relais. Jamais en ouvrant le port ADB sur sa box. Pas à pas dans
[docs/REMOTE.md](docs/REMOTE.md).

</details>

<details>
<summary><b>L'accès ADB n'est-il pas une faille de sécurité ?</b></summary>

C'est une seule autorisation explicite « autoriser cet ordinateur » sur le téléviseur, la
même que pour développer une application Android. L'ADB réseau circule en clair sur le port 5555, et sur certains
firmwares n'importe quel appareil du réseau local obtient un shell : fermez-le donc quand vous avez terminé :

1. Sur le téléviseur : Options pour les développeurs → désactivez le **Débogage USB**, le **Débogage réseau/ADB** et
   le **Débogage sans fil**, selon ce que propose le téléviseur, et révoquez les autorisations de débogage USB.
2. Redémarrez le téléviseur, puis vérifiez depuis votre ordinateur que le port a disparu :
   `python3 tools/adb-closed.py <tv ip>` doit indiquer que le port est fermé.

Le launcher continue de fonctionner sans le débogage. Plus de détails, y compris la vérification du port propre au
Débogage sans fil : [docs/faq.md](docs/faq.md#isnt-adb-access-a-security-hole)

</details>

<details>
<summary><b>Peut-il aussi bloquer les publicités dans les applications ?</b></summary>

En partie, avec le DNS privé d'Android (Android 9 et plus récent). Faites-le
pointer vers un résolveur qui bloque les publicités, comme [AdGuard DNS](https://adguard-dns.io), et les domaines
de publicité et de pistage de toutes les applications cessent d'être résolus, pour tout le système, même après un
redémarrage. La contrepartie : ce résolveur voit alors chaque requête DNS du téléviseur, et cela ne retire pas les
publicités de YouTube.

Plus de détails, avec les deux commandes et la façon de revenir en arrière : [docs/faq.md](docs/faq.md#can-it-block-ads-inside-apps-too)

</details>

<details>
<summary><b>Y a-t-il aussi des réglages dans les Paramètres du téléviseur qui valent la peine d'être modifiés ?</b></summary>

Quelques-uns, et ils ne demandent pas d'ADB : l'identifiant publicitaire, la Localisation, Utilisation et
diagnostics et la Recherche toujours disponible, plus le mode Applications uniquement là où le launcher d'origine
est conservé. Modifiez Utilisation et diagnostics avant le nettoyage ; sur certains téléviseurs, sa page disparaît
avec lui.

Plus de détails, avec les chemins pour Google TV, Android TV et Fire OS :
[docs/faq.md](docs/faq.md#are-there-switches-in-the-tvs-own-settings-worth-flipping-too)

</details>

<details>
<summary><b>AirPlay ?</b></summary>

Pas depuis ce dépôt. Chromecast intégré reste disponible pour les téléphones Android. Pour les iPhone,
[jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) fonctionne ; un script
d'installation pour celui-ci se trouve dans la [PR #84](https://github.com/Gathin23/cleanyourtv/pull/84).

</details>

<details>
<summary><b>Puis-je simplement télécharger l'APK ?</b></summary>

Compilez-le avec le script d'une ligne de [Le faire à la main](#le-faire-à-la-main) ; cela prend une minute et vous
gardez le contrôle de ce qui va sur votre téléviseur. Il faut pour cela le JDK et le SDK Android indiqués dans
cette section. Pas de binaires précompilés.

</details>

## L'histoire

Le 17 octobre 2026, j'ai publié [un avant/après](https://x.com/gathint/status/2111379569030939102) du téléviseur
de mes parents, nettoyé par Claude Code via le Wi-Fi. La publication a atteint 8,6 millions de vues, des gens se
sont mis à faire de même sur leurs propres téléviseurs, et leurs réponses sont devenues les listes de ce dépôt :
plus de 170 modèles de téléviseurs, venant de plus de 200 contributeurs, en quatre jours. Le dépôt s'appelait
tv-vibehack jusqu'au 19 ; les anciens liens fonctionnent toujours.

Deux semaines plus tard, c'était le dépôt n° 1 des tendances sur GitHub pour la semaine du 19 au 25 octobre, avec
plus de 46 000 étoiles et 5 700 forks, ainsi que 1,4 million de vues et 260 000 clones en 14 jours. Les listes
couvrent désormais 472 modèles de téléviseurs de 129 marques. La couverture médiatique : 534 articles de 57 pays et
663 vidéos sur YouTube et Bilibili, tous recensés dans [docs/PRESS.md](docs/PRESS.md)
(chiffres au 9 novembre 2026).

## Contribuer

La contribution la plus utile est la liste de nettoyage de votre téléviseur, pour que la prochaine personne qui a ce
modèle n'ait pas à tout redécouvrir. [CONTRIBUTING.md](CONTRIBUTING.md) décrit le format et les tests que nous
demandons. Vous contribuez par l'intermédiaire d'un agent de code ? [AGENTS.md](AGENTS.md) lui donne les mêmes règles.

| Comment aider | Ce que cela signifie |
|---|---|
| **La liste de nettoyage de votre téléviseur** | La plus utile : la prochaine personne qui a ce modèle saute l'étape de découverte. |
| **Launcher et prompt** | Les améliorations du launcher et une formulation plus précise du prompt sont aussi les bienvenues. |
| **Vous avez construit quelque chose par-dessus ?** | Un launcher, un portage, un thème : ajoutez-le à [SHOWCASE.md](SHOWCASE.md), qui rassemble déjà 243 projets, classés en écrans d'accueil, forks, add-ons, outils et autres plateformes, avec un index par marque de téléviseur. |

Les personnes dont le travail figure dans ce dépôt :

<a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://contrib.rocks/image?repo=Gathin23/cleanyourtv&columns=16&max=400" alt="Contributeurs"></a>

<details>
<summary><strong>Historique des étoiles</strong></summary>

<a href="https://star-history.com/#Gathin23/cleanyourtv&Date"><img src="https://api.star-history.com/svg?repos=Gathin23/cleanyourtv&type=Date" alt="Graphique de l'historique des étoiles" width="640"></a>

</details>

## Contact

| Pour | Où |
|---|---|
| **Questions sur votre téléviseur** | [GitHub Discussions](https://github.com/Gathin23/cleanyourtv/discussions) |
| **Presse et demandes** | [hello@itsgathin.com](mailto:hello@itsgathin.com) |
| **Problèmes de sécurité** | Signalez-les en privé via l'[onglet Security](https://github.com/Gathin23/cleanyourtv/security), voir [`SECURITY.md`](SECURITY.md) |

## Mentions légales

- CleanYourTV est un projet open source indépendant. Il n'est affilié à aucun fabricant de téléviseurs ni à aucune
  plateforme, et n'est ni approuvé ni sponsorisé par eux. Les noms de marques et de modèles ne servent qu'à indiquer
  sur quel téléviseur une liste a été testée, et restent les marques de leurs propriétaires.
- Il fonctionne uniquement grâce aux réglages que le téléviseur offre lui-même à son propriétaire : les Options pour
  les développeurs, le débogage ADB et la commande `pm disable-user` d'Android, l'équivalent de Paramètres >
  Applications > Désactiver. Il ne roote pas le téléviseur, ne flashe aucun firmware et ne contourne aucun verrou,
  DRM ou protection contre la copie, et le dépôt ne contient aucun logiciel, firmware ou APK de fabricant.
- Rien n'est désinstallé, et `tools/restore.sh` remet tout en place. Vous l'utilisez sur un téléviseur qui vous
  appartient, à vos propres risques, et modifier le logiciel d'un téléviseur peut avoir des conséquences sur sa
  garantie ou son assistance.
- Tout ce qui se trouve ici est fourni en l'état, sans garantie d'aucune sorte, sous la licence MIT ci-dessous.

## Licence

MIT, voir [`LICENSE`](LICENSE). La police Outfit est sous licence SIL Open Font License, voir
`launcher/FONT-LICENSE-OFL.txt`.
