<p align="center">
  <a href="https://cleanyour.tv"><img src="https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/banner.png" alt="CleanYourTV. Tu televisor, sin anuncios. Elimina las aplicaciones innecesarias de cualquier Android TV o Google TV por Wi-Fi y reemplaza la pantalla de inicio llena de anuncios por un launcher limpio con aspecto de Apple TV. Sin root, sin cables, sin desinstalar nada y con restauración en una sola línea." width="800"></a>
</p>

<p align="center">
  Leer en: <a href="README.md">English</a> · <a href="README.he.md" lang="he" dir="rtl">עברית</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">正體中文</a> · <a href="README.vi.md">Tiếng Việt</a> · <a href="README.ja.md">日本語</a> · <a href="README.it.md">Italiano</a> · <a href="README.pt-BR.md">Português</a> · <a href="README.nl.md">Nederlands</a> · <a href="README.de.md">Deutsch</a> · <a href="README.uk.md">Українська</a> · <a href="README.tr.md">Türkçe</a> · <a href="README.uz.md">Oʻzbekcha</a> · <a href="README.fr.md">Français</a> · Español
</p>

<p align="center">
  <a href="https://github.com/Gathin23/cleanyourtv/stargazers"><img src="https://img.shields.io/github/stars/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Estrellas"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/pulls?q=is%3Amerged"><img src="https://img.shields.io/github/issues-search?query=repo%3AGathin23%2Fcleanyourtv%20is%3Apr%20is%3Amerged&style=flat&cacheSeconds=1800&label=merged%20PRs&color=cfffcf&labelColor=15181e" alt="PRs fusionadas"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/network/members"><img src="https://img.shields.io/github/forks/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Forks"></a>
  <a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://img.shields.io/github/contributors/Gathin23/cleanyourtv?style=flat&cacheSeconds=1800&color=cfffcf&labelColor=15181e" alt="Colaboradores"></a>
  <a href="https://buymeacoffee.com/gathint"><img src="https://img.shields.io/badge/support-%E2%9D%A4-ff5e8a?style=flat&labelColor=15181e" alt="Apoya el proyecto, invítame a un café"></a>
  <a href="https://www.producthunt.com/products/cleanyourtv"><img src="https://img.shields.io/badge/Product%20Hunt-upvote%20%E2%96%B2-da552f?style=flat&logo=producthunt&logoColor=white&labelColor=15181e" alt="CleanYourTV en Product Hunt"></a>
</p>

<p align="center">
  <a href="https://trendshift.io/repositories/310225"><img src="docs/trending-badge.png" alt="Repositorio en tendencia n.º 1 de la semana, del 19 al 25 de octubre de 2026, vía trendshift.io" width="360"></a>
</p>

# CleanYourTV: Guía en español

Conéctate a un Android TV o Google TV a través de la red Wi-Fi de tu casa, desactiva las aplicaciones preinstaladas, los anuncios y los servicios de recopilación de datos que no utilizas. Después, instala una pantalla de inicio (launcher) limpia en los televisores compatibles.

No se necesita root, ni reinstalar el firmware, ni usar cables USB. Las aplicaciones del sistema solo se **desactivan**, nunca se eliminan, y siempre dispones de un script para restaurarlas con una sola línea.

Esta página describe las características principales y su uso general. Cada modelo de televisor, región y versión de firmware cuenta con su propia lista de aplicaciones y particularidades; consulta primero la información específica de tu televisor.

En caso de discrepancia, prevalece el [README en inglés](README.md). Las notas específicas de cada modelo se encuentran en la [lista de televisores compatibles](examples/README.md).

> [!WARNING]
> **¿Tu televisor es Samsung, LG, Roku o de otra marca que no utiliza Android?**
> <br>CleanYourTV no funciona en esos dispositivos, pero aún puedes desactivar el rastreo paso a paso utilizando únicamente el mando a distancia: [cleanyour.tv/not-android](https://cleanyour.tv/not-android).

![Comparativa entre la pantalla de inicio original de Google TV y el launcher de CleanYourTV](docs/stock-vs-new-side.jpg)

## Comienza aquí

| Tienes | Ve a |
|---|---|
| **Un Android TV o Google TV** | [Inicio rápido](#inicio-rápido): un asistente de programación hace el trabajo en unos quince minutos. |
| **Un televisor Samsung, LG, Roku, Vizio o Hisense VIDAA** | [cleanyour.tv/not-android](https://cleanyour.tv/not-android): CleanYourTV no funciona en estos televisores, pero puedes desactivar el rastreo con el mando a distancia. |
| **Un móvil o portátil, sin asistente de programación** | Una de las [aplicaciones de la comunidad](https://cleanyour.tv/app) para móviles Android, Mac y Windows, desarrolladas por personas independientes. |
| **Quieres ver cómo queda antes** | [Lo que obtienes](#lo-que-obtienes): capturas de pantalla de la nueva pantalla de inicio. |

**Enlaces oficiales:** [cleanyour.tv](https://cleanyour.tv) y este repositorio. Otros dominios que usan este nombre, como cleanyourtv.com, no son gestionados por este proyecto ([#1004](https://github.com/Gathin23/cleanyourtv/issues/1004)). CleanYourTV no publica aplicaciones en ninguna tienda de aplicaciones, y clean-your-tv.net tampoco nos pertenece.

[Cómo funciona](#cómo-funciona) · [Inicio rápido](#inicio-rápido) · [Lo que obtienes](#lo-que-obtienes) · [Televisores compatibles](#televisores-compatibles) · [Hacerlo manualmente](#hacerlo-manualmente) · [El launcher](#el-launcher) · [Restauración](#restaurar-tu-televisor) · [Actualizaciones de firmware](#después-de-una-actualización-de-firmware) · [Preguntas frecuentes](#preguntas-frecuentes) · [Cómo contribuir](#cómo-contribuir)

## Cómo funciona

Los televisores con Android TV cuentan con el mismo puente de depuración (ADB) que los teléfonos Android. Activa la depuración por USB en las opciones para desarrolladores del televisor, autoriza tu ordenador una sola vez y el resto se realiza con herramientas estándar de Android:

| Paso | Qué ocurre |
|---|---|
| **Hacer copia de seguridad** | Guarda la lista de paquetes instalados y sus configuraciones. |
| **Desactivar** | Desactiva la telemetría del fabricante, los servicios de anuncios, las filas de contenido patrocinado y las aplicaciones innecesarias con `pm disable-user`. Nada se desinstala, por lo que un script de restauración puede deshacer cada cambio. |
| **Instalar** | Instala un launcher nativo y lo establece como pantalla de inicio predeterminada. |

**Sin root, sin cables, sin desinstalar nada y con restauración en una sola línea por si cambias de opinión.**

La cantidad y tipo de aplicaciones preinstaladas varía según la marca. Por eso, el prompt hace que tu asistente de programación identifique qué hay en tu televisor y te pregunte antes de modificar cualquier función o app que uses.

## Inicio rápido

| Tienes | Qué hacer |
|---|---|
| **Un ordenador** | Usa el prompt con un asistente de programación: sigue los tres pasos siguientes. |
| **Un ordenador, pero sin asistente de programación** | Ejecuta los tres pasos por tu cuenta: las instrucciones están en [Hacerlo manualmente](#hacerlo-manualmente). |
| **Un móvil o portátil, sin asistente de programación** | Elige una de las [aplicaciones de la comunidad](https://cleanyour.tv/app): aplicaciones gratuitas y de código abierto desarrolladas por personas independientes basadas en las listas de televisores de este proyecto. |

Los tres pasos siguientes están diseñados para ejecutarse con un asistente de programación: el ordenador debe estar en la misma red Wi-Fi que el televisor y disponer de una terminal, además de Python 3, un JDK entre las versiones 17 y 22 y Android SDK platform 34 con build-tools 34 (el asistente compila el launcher en ese entorno). Sin un asistente, [Hacerlo manualmente](#hacerlo-manualmente) es el mismo proceso, ejecutado por ti, con los comandos de instalación incluidos.

Puedes utilizar cualquiera de estos: Claude Code (en [0G Private Computer](https://pc.0g.ai/?utm_source=cleanyourtv&utm_medium=github&utm_campaign=readme) o en un [plan de Anthropic](https://claude.com/pricing)), [Codex](https://github.com/openai/codex), [Gemini CLI](https://github.com/google-gemini/gemini-cli) o [Antigravity CLI](https://antigravity.google/product/antigravity-cli).

| Dónde | Qué hacer |
|---|---|
| **1. En el televisor** | Ajustes → Preferencias del dispositivo → Información → pulsa OK siete veces en **Compilación** → vuelve atrás → Opciones para desarrolladores → activa **Depuración por USB**. Si hay una opción de Depuración de red o ADB inalámbrica, actívala también. Mantén el televisor encendido. |
| **2. En el ordenador** | Clona este repositorio y abre esa carpeta en tu asistente (`git clone https://github.com/Gathin23/cleanyourtv`). Luego pega el contenido de [`PROMPT.md`](PROMPT.md) e indica el modelo de tu televisor. |
| **3. Sigue el proceso** | Acepta la solicitud "¿Permitir depuración por USB?" en el televisor y responde a las preguntas: qué aplicaciones utilizas, el tamaño del dock y el orden de las apps, y si deseas mantener el asistente de voz, Chromecast y las actualizaciones. |

Clonar el repositorio es importante: de este modo, el asistente reutiliza los scripts `tools/disable.sh` y `launcher/build-and-install.sh` de este proyecto, en lugar de crear los suyos propios. Es en esos scripts donde residen la copia de seguridad, la protección de la pantalla de inicio y el registro necesario para restaurar todo.

El asistente realiza la copia de seguridad, desactiva las aplicaciones innecesarias, compila e instala el launcher y verifica cada paso mediante capturas de pantalla tomadas directamente del televisor. Con este repositorio como contexto, el proceso toma unos quince minutos.

## Lo que obtienes

Una pantalla de inicio con diseño similar a Apple TV, rápida y sin distracciones:

| Elemento | Cómo es |
|---|---|
| **Una pantalla de inicio totalmente negra** | Un dock con tus cuatro aplicaciones favoritas en una barra redondeada, al estilo de Apple TV. |
| **Una zona de destacados en la parte superior** | Muestra el nombre de la app seleccionada en letras grandes, con un resplandor ambiental en un color extraído del icono de la app. |
| **El resto de aplicaciones en una cuadrícula** | Se deslizan hacia arriba cuando pulsas la flecha abajo en el mando. |
| **Un único foco flotante con efecto de cristal** | Se desplaza suavemente de un elemento a otro. El icono seleccionado se eleva y muestra su nombre. |
| **Tres botones con efecto de cristal esmerilado** | Entradas (puertos HDMI con los nombres personalizados que hayas definido en los ajustes del televisor, AV y TV en directo), Wi-Fi o Ethernet con el nombre de la red, y Ajustes. |
| **Un saludo, un reloj discreto y la fecha** | Mostrados en la fuente [Outfit](https://fonts.google.com/specimen/Outfit), con un salvapantallas de reloj con el mismo estilo. Configura `show_stats` en la configuración o en un tema para ver una línea bajo el reloj con la CPU, memoria RAM libre y almacenamiento disponible. Permanece desactivada hasta que la habilitas y solo se actualiza mientras la pantalla de inicio está a la vista. |
| **Temas y tarjetas de módulos** | Los temas modifican los colores, el fondo de pantalla, el dock y las columnas sin necesidad de recompilar. Las tarjetas de módulos permiten que otras apps añadan un bloque, siempre con tu autorización explícita. |

Y un televisor que deja de molestarte: sin filas patrocinadas, sin recomendaciones invasivas de "Descubrir", sin telemetría enviada a servidores externos y sin aplicaciones que nunca pediste.

| | |
|---|---|
| ![La zona de destacados muestra la aplicación en foco sobre un brillo ambiental del color correspondiente](docs/focus-2-up-netflix.png) | ![El menú Entradas: puertos HDMI con el estado de conexión, AV y TV en directo](docs/inputs-menu.jpg) |
| *Zona de destacados y brillo ambiental* | *Entradas: HDMI con estado de conexión, AV y TV en directo* |
| ![La cuadrícula con el foco de cristal sobre un elemento y el nombre justo debajo](docs/focus-1-fancode.png) | ![El salvapantallas Reloj: un reloj discreto flotando sobre el resplandor](docs/clock-screensaver.png) |
| *La cuadrícula, el foco de cristal y el nombre del elemento* | *Salvapantallas Reloj, con movimiento para evitar retenciones en la pantalla* |
| ![El tema aurora: fondo de pantalla y botones personalizados, aplicados sin recompilar](themes/aurora/screenshot.jpg) | ![Seis columnas y un dock con seis aplicaciones definidas en el config.json del tema](themes/six-columns/screenshot.jpg) |
| *Tema Aurora, aplicado con `tools/theme.sh`* | *Seis columnas y un dock de seis aplicaciones, en el mismo launcher* |
| ![Moviendo un elemento: mantén OK pulsado, elige Mover, el icono se selecciona y los demás se atenúan](docs/tile-move.jpg) | ![La biblioteca de aplicaciones: aplicaciones instaladas, un toque añade una app de vuelta a la pantalla de inicio](docs/app-library.jpg) |
| *Mantén OK pulsado en un elemento y selecciona Mover: usa las flechas para reubicarlo y Atrás para soltarlo* | *Biblioteca de aplicaciones: un toque devuelve a la pantalla de inicio una aplicación retirada* |

## Televisores compatibles

Cualquier televisor que funcione con **Android TV** o **Google TV**.

| Plataforma | Qué funciona |
|---|---|
| **Android TV, Google TV** | La desactivación de aplicaciones innecesarias y el launcher. |
| **Fire TV** | Únicamente la desactivación de aplicaciones innecesarias, ya que Fire OS no permite definir un launcher de terceros como pantalla de inicio predeterminada. Y en el Fire TV Box de 2015 (Fire OS 5), ni siquiera eso: todas las operaciones `pm` son rechazadas. |
| **Ciertos modelos TCL comercializados en China** | No permiten instalar ningún launcher; los archivos de esos modelos lo indican en su encabezado. |
| **Samsung (Tizen), LG (webOS), Roku, Vizio, Hisense VIDAA** | No son compatibles (no disponen de ADB). En su lugar, desactiva el rastreo manualmente: [cleanyour.tv/not-android](https://cleanyour.tv/not-android). Consulta [SHOWCASE.md](SHOWCASE.md#other-platforms-fire-tv-and-lg-webos) para ver cuatro adaptaciones para webOS y [docs/devices/samsung-tizen.md](docs/devices/samsung-tizen.md) para saber qué responde un televisor Samsung de 2021 por Wi-Fi (sin ADB y, por tanto, sin `pm disable-user`) en caso de que valores una adaptación para Tizen. |

Listas probadas y enviadas por la comunidad, organizadas por carpetas de modelo en [`examples/`](examples/).

**Encuentra tu televisor:** busca en [cleanyour.tv/tvs](https://cleanyour.tv/tvs) o consulta la tabla completa en [examples/README.md](examples/README.md).

¿Tu televisor no aparece en la lista? El prompt identifica las aplicaciones innecesarias por sí mismo. Después, añade tu lista para que la siguiente persona con el mismo modelo no tenga que repetir el proceso: [CONTRIBUTING.md](CONTRIBUTING.md).

## Hacerlo manualmente

Todo lo que hace el asistente lo puedes ejecutar tú mismo. Antes, debes tener dos elementos instalados en tu ordenador:

| Necesitas | Para qué |
|---|---|
| **Python 3** (cualquier versión 3.x) | Para las herramientas. El comando para crear el entorno virtual instala automáticamente las dependencias necesarias. |
| **JDK 17 a 22** (un JDK completo, incluyendo `jlink`) y **Android SDK platform 34** con **build-tools 34.0.0 o cualquier versión 34.x más reciente** | Para compilar el launcher. JDK 23 y versiones posteriores impiden el funcionamiento de Gradle 8.9. La plataforma 34 es la requerida por el proceso de compilación; una versión posterior no la reemplaza. En macOS, si no tienes ninguno de estos componentes: `brew install openjdk@17 android-commandlinetools` y después `sdkmanager "platforms;android-34" "build-tools;34.0.0"`. En Linux, el script de instalación busca el SDK en `~/Android/Sdk` y el binario `java` en tu PATH. |

`tools/tv.py` se comunica con el televisor a través de ADB en red por el puerto 5555. Si dicho puerto permanece cerrado y el televisor ofrece Depuración inalámbrica mediante código de vinculación (como en Chromecast con Google TV y Google TV Streamer), realiza el emparejamiento una vez con el `adb` estándar. Después, puedes ejecutar `adb tcpip 5555` para abrir el puerto —lo cual habitualmente se mantiene solo hasta reiniciar el televisor— o bien mantenerlo cerrado: configura `TV_ADB_BINARY` apuntando a ese mismo `adb` y `TV_HOST` al puerto indicado en la pantalla de Depuración inalámbrica. De esta forma, las herramientas se conectan a través del canal cifrado sin que nada quede escuchando en el puerto 5555 ([tools/native-adb.md](tools/native-adb.md)). La guía de [solución de problemas](docs/TROUBLESHOOTING.md#connecting) detalla ambas opciones y los pasos de vinculación.

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # dirección IP de tu televisor

tools/venv/bin/python -I tools/tv.py --probe                 # modelo, versión de Android y firmware
tools/venv/bin/python -I tools/tv.py 'pm list packages'      # ejecuta un comando en el televisor
tools/disable.sh examples/xiaomi-mi-tv-4a/debloat.txt        # aplica una lista de limpieza de aplicaciones
tools/restore.sh examples/xiaomi-mi-tv-4a/debloat.txt        # revierte los cambios y restaura el launcher original
tools/venv/bin/python -I tools/check.py examples/xiaomi-mi-tv-4a/debloat.txt   # comprueba qué ha restaurado una actualización
TV_HOST=192.168.1.50 launcher/build-and-install.sh           # compila el launcher y lo define como pantalla de inicio
tools/theme.sh themes/aurora                                 # aplica un tema; --reset lo retira
```

| Herramienta | Qué hace |
|---|---|
| [`tools/tv.py`](tools/tv.py) | Cliente ADB programado enteramente en Python: terminal shell, ejecución por lotes, subida y descarga de archivos, reinicio y capturas de pantalla, con la posibilidad de pulsar teclas del mando antes. Funciona incluso cuando el binario `adb` del sistema está bloqueado. Ofrece además un [backend Google ADB con emparejamiento](tools/native-adb.md), opcional, para Depuración inalámbrica cifrada. |
| [`tools/docker-build.sh`](tools/docker-build.sh) | Compila el launcher o un complemento dentro de Docker (JDK 17, Android SDK 34), sin requerir otras herramientas en este ordenador aparte de Docker. Instala el resultado mediante `SKIP_BUILD=1`. [Guía](tools/docker-build.md). |
| [`tools/b64push.py`](tools/b64push.py) | Envía archivos de gran tamaño, como APKs, divididos en bloques codificados en base64 y comprueba su suma de verificación (checksum). |
| [`tools/relay.py`](tools/relay.py) | Redirige un puerto local al puerto ADB del televisor, para asistentes cuyo entorno aislado no puede acceder a tu red local. Escucha únicamente en este equipo. [Guía](tools/relay.md). |
| [`tools/disable.sh`](tools/disable.sh), [`restore.sh`](tools/restore.sh) | Aplican o deshacen una lista de limpieza de aplicaciones. La restauración emplea el estado registrado previamente a las modificaciones. |
| [`tools/recovery.py`](tools/recovery.py) | Genera el registro necesario para que `disable.sh` pueda deshacer sus propios cambios y muestra los comandos de restauración por lotes. |
| [`tools/check.py`](tools/check.py) | Compara el estado del televisor con la lista aplicada para detectar qué ha revertido una actualización de firmware; `--fix` vuelve a aplicar las modificaciones. |
| [`tools/adb-closed.py`](tools/adb-closed.py) | Comprueba si el puerto ADB en red del televisor está cerrado tras desactivar la depuración: devuelve 0 cuando el televisor rechaza la conexión, 1 mientras continúa abierto y 2 cuando no hay respuesta o la comprobación no se puede ejecutar. No requiere `nc`. |
| [`tools/philips-api.py`](tools/philips-api.py) | Solo para Philips: comprueba si la API de control JointSpace, empleada por Home Assistant, sigue respondiendo antes y después de la limpieza (`tools/venv/bin/python -I tools/philips-api.py <tv ip>`). Intenta primero el puerto 1926 por HTTPS y después el 1925; lee únicamente lo que responde, sin vinculación. |
| [`tools/privacy.py`](tools/privacy.py) | Audita el televisor buscando posibles aplicaciones y paquetes vinculados a voz, ACR y telemetría y muestra las evidencias de cada uno. Con `--write`, convierte los resultados en una lista comprensible para `disable.sh` y `restore.sh`. |
| [`tools/theme.sh`](tools/theme.sh), [`theme_restart.py`](tools/theme_restart.py) | Envían una carpeta de [`themes/`](themes/) al launcher y reinician la pantalla de inicio. |
| [`tools/manage.py`](tools/manage.py) | Muestra todos los complementos, temas y opciones con sus capturas de pantalla; indica qué está instalado en el televisor y permite instalar, actualizar o desinstalar cada elemento ejecutando el script de su autor. |
| [`tools/bench.py`](tools/bench.py) | Mide el rendimiento del launcher que responde al botón HOME: latencia de teclas, distancia recorrida por el foco, caídas de fotogramas (jank), inicio en frío, retorno al inicio y uso de CPU y memoria PSS en reposo. No altera nada en el televisor y almacena los datos brutos en formato JSON; no precisa instalación adicional. |
| [`tools/trace.py`](tools/trace.py), [`trace_report.py`](tools/trace_report.py) | Capturan trazas de Perfetto del launcher (retorno al inicio, inicio en frío y navegación con cruceta) y analizan en qué se invierte el tiempo, por ejecución y comparando antes y después. Opcional: requiere [`requirements-perf.txt`](tools/requirements-perf.txt). [Guía](tools/trace.md). |
| [`tools/flamechart.py`](tools/flamechart.py) | Genera un flame chart comparando trazas de antes y después, útil para pull requests: una pista por hilo en una escala temporal compartida, con el trabajo analizado resaltado en magenta y su tiempo total. Opcional, al igual que las herramientas de traza. [Guía](tools/flamechart.md). |
| [`tools/screendiff.py`](tools/screendiff.py) | Compara una captura previa y posterior pixel a pixel, ignorando el reloj o cualquier zona que definas. Guarda además el par de imágenes con el nombre de tu red Wi-Fi difuminado. Opcional, igual que las utilidades de traza. [Guía](tools/screendiff.md). |
| [`tools/race_calendar.py`](tools/race_calendar.py) | Incorpora el calendario de la temporada vigente de F1 a un tema para el modo fin de semana de carreras, mediante la API gratuita de Jolpica. |
| [`launcher/build-and-install.sh`](launcher/build-and-install.sh) | Compila una compilación de producción optimizada (unos 572 KB), la instala, la configura como pantalla de inicio y revierte el cambio si el launcher no toma el control adecuadamente. |

Los scripts nunca dejan el televisor sin una aplicación de inicio: únicamente desactivan un launcher original que reconocen y omiten esa aplicación en la lista hasta que otro launcher responda al botón HOME. Tus propias listas pueden almacenarse fuera del repositorio: las rutas son relativas al directorio desde el que ejecutas el comando, y las rutas absolutas también son válidas.

<details>
<summary><b>Windows (PowerShell)</b></summary>

Cada script dispone de un equivalente en PowerShell junto al original. Instala Python 3 y un JDK entre las versiones 17 y 22 y, en el SDK Manager, añade la plataforma 34 y build-tools 34 (34.0.0 o cualquier versión 34.x más reciente). El script utiliza `JAVA_HOME` cuando apunta a un JDK en dicho rango; de lo contrario, recurre al JDK integrado en Android Studio si cumple con esa condición:

```powershell
python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50
powershell -ExecutionPolicy Bypass -File tools\disable.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\restore.ps1 -TvHost 192.168.1.50 examples\xiaomi-mi-tv-4a\debloat.txt
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
```

Las dos últimas líneas realizan la restauración y aplican un tema, equivalentes a los comandos `tools/restore.sh` y `tools/theme.sh` descritos arriba. En Google TV, la restauración también recupera el launcher original: `-Stock com.google.android.apps.tv.launcherx`.

</details>

<details>
<summary><b>Cómo protegen la pantalla de inicio los scripts</b></summary>

Los scripts de instalación consultan al televisor qué launcher gestiona la pantalla de inicio y solo desactivan uno que reconozcan. Los cuatro launchers originales definidos en `KNOWN_STOCK_LAUNCHERS` son: `com.google.android.tvlauncher`, `com.google.android.apps.tv.launcherx`, `com.google.android.leanbacklauncher` y el `com.mitv.tvhome` de Xiaomi (PatchWall). Usa `STOCK_LAUNCHER=<paquete>` (`-StockLauncher`) para reemplazar este valor o `none` para no desactivar ningún launcher.

`tools/disable.sh` (`disable.ps1`) aplica la misma salvaguarda antes de desactivar cualquier aplicación:

- Si la lista contiene la aplicación que gestiona la pantalla de inicio y ninguna otra responde al botón HOME, omite esa entrada, informa del motivo y continúa con el resto de la lista. De este modo, una lista aplicada antes de cambiar de launcher no deja el televisor sin interfaz.
- El asistente de configuración inicial, los ajustes del sistema, el `FallbackHome` del fabricante y las aplicaciones de prueba de fábrica (como `com.duokan.factorytest` de Xiaomi) también responden al botón HOME y son excluidos deliberadamente de esa consideración.
- En Google TV, el script mantiene además el launcher original activo mientras `setupwraith` responda al botón HOME y continúe habilitado (por no estar en la lista o por haberse conservado para mantener la red Wi-Fi). `setupwraith` toma prioridad antes que un launcher nuevo que aún no haya sido elegido como pantalla de inicio y asumiría el control indeseadamente.

Vuelve a ejecutar la misma lista una vez que `launcher/build-and-install.sh` establezca el nuevo launcher como pantalla de inicio para que la entrada correspondiente sea procesada. `FORCE_HOME=1` (`$env:FORCE_HOME`) fuerza la desactivación en cualquier circunstancia.

</details>

## El micrófono y lo que se puede afirmar sobre él

La pregunta "¿Cuáles de estos paquetes están escuchando?" no tiene una respuesta honesta en una sola frase: una comprobación de permisos revelará cincuenta paquetes del sistema con `RECORD_AUDIO` que no graban nada, mientras que los módulos de ACR pueden analizar la pantalla sin requerir permisos de sonido. El script [`tools/privacy.py`](tools/privacy.py) expone lo que el televisor declara realmente, con las evidencias para cada posible función de voz, ACR y telemetría: `TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/privacy.py`. El modificador `--write` transforma los hallazgos en una lista estándar de desactivación. Lo que dicha auditoría no puede determinar es la intencionalidad detrás de cada función: [docs/privacy.md](docs/privacy.md) detalla las razones.

## El launcher

Una pantalla de inicio nativa y muy ligera para Android TV: **unos 572 KB**, programada en Java puro y pensada para televisores con tan solo 1 GB de RAM. Sin desenfoques pesados, sin vídeo de fondo, sin permisos de red y sin servicios en segundo plano. El **televisor** necesita ejecutar Android 8.0 (API 26) o superior, según el `minSdk` establecido en [`launcher/app/build.gradle`](launcher/app/build.gradle). Para conocer qué funciones siguen activas en versiones anteriores, consulta [Solución de problemas](docs/TROUBLESHOOTING.md).

| Característica | Cómo funciona |
|---|---|
| **Configuración** | Orden del dock, aplicaciones ocultas, app de TV en directo, de 4 a 7 elementos por fila y animaciones reducidas. Todo definido en [`config.xml`](launcher/app/src/main/res/values/config.xml). |
| **Ajustes en el televisor** | Mantén OK pulsado en Ajustes para acceder a las opciones del launcher. Opcionalmente, `launcher_settings_button` permite que una pulsación simple las abra directamente; la configuración del televisor continúa accesible desde la propia página de ajustes y mediante el icono correspondiente. |
| **Temas** | Fondo, fondo de pantalla y diseño definidos en un `config.json` enviado por Wi-Fi, sin recompilar. 29 temas están incluidos en [`themes/`](themes/), incluyendo el `default/` integrado. |
| **31 idiomas** | Sigue el idioma del televisor, con un diseño totalmente reflejado de derecha a izquierda en hebreo. |
| **Biblioteca de aplicaciones** | Opcional: permite explorar todas las aplicaciones instaladas y agregar o retirar accesos directos de la pantalla de inicio. Consulta [la guía de la biblioteca de aplicaciones](docs/app-library.md). |
| **Menú contextual de elementos** | Habilitado por defecto (`tile_menu`): mantén OK pulsado sobre un elemento para acceder a Mover, Quitar de la pantalla de inicio e Información de la app. Si se desactiva, mantener OK pulsado abre Información de la app directamente. |
| **Tarjetas de módulos** | Cualquier aplicación de TV puede incorporar su propio bloque informativo; autorizas cada una una sola vez. Consulta [`docs/modules.md`](docs/modules.md). |
| **Complementos** | Aplicaciones opcionales que se integran como tarjetas de módulos, ubicadas en [`addons/`](addons/) y nunca incrustadas en el launcher. |
| **Salvapantallas con reloj** | Un reloj en desplazamiento continuo sobre el brillo ambiental para prevenir marcas persistentes en paneles OLED y LCD. |
| **Teclas del mando a distancia** | Mantiene operativos los botones de Entradas, YouTube y Ajustes incluso tras desactivar el launcher original. |

Temas, opciones y complementos, todos con capturas de pantalla: [cleanyour.tv/addons](https://cleanyour.tv/addons).

**Aprende más:** [docs/launcher.md](docs/launcher.md) detalla cada parámetro de la pantalla de ajustes del launcher, cómo modificar el launcher predeterminado, los idiomas, las teclas del mando, el salvapantallas con reloj, la navegación y cómo compilar el proyecto por tu cuenta.

## Restaurar tu televisor

Conserva la lista que hayas aplicado. Una sola línea devuelve el televisor a su estado original:

```bash
TV_HOST=192.168.1.50 tools/restore.sh examples/<tu-televisor>/debloat.txt                                    # Android TV
TV_HOST=192.168.1.50 tools/restore.sh examples/<tu-televisor>/debloat.txt com.google.android.apps.tv.launcherx  # Google TV
```

| Elemento | Cómo funciona |
|---|---|
| **El registro se crea primero** | Antes de que `tools/disable.sh` modifique nada, almacena el estado previo del televisor en `~/.cleanyourtv/`. |
| **La restauración recupera dicho estado** | Un paquete que ya hubieras desactivado previamente seguirá desactivado, y la pantalla de inicio regresará al componente que la gestionaba originalmente. |
| **El launcher continúa instalado** | Si deseas desinstalarlo por completo, puedes hacerlo desde Ajustes → Aplicaciones en el propio televisor. |
| **¿Sin ordenador a mano?** | Si no puedes conectar con el televisor, el menú Ajustes → Aplicaciones del propio sistema permite volver a habilitar los paquetes manualmente. |

**Más información:** [docs/restore.md](docs/restore.md) expone qué contiene el registro de recuperación, cuándo resulta necesario especificar el launcher original y cómo actuar si dicho registro no existe.

## Después de una actualización de firmware

Una actualización oficial se instala sobre el sistema ya configurado: los paquetes desactivados pueden reactivarse, la pantalla de inicio vuelve a quedar bajo el control del launcher original y nuevas aplicaciones preinstaladas pueden aparecer sin estar catalogadas. Vuelve a habilitar ADB, tal como se indica en el paso 1 de [Inicio rápido](#inicio-rápido), y compara el estado del televisor con tu lista aplicada. La opción `--fix` restaura las modificaciones:

```bash
TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py examples/<tu-televisor>/debloat.txt
```

**Más información:** [docs/firmware-updates.md](docs/firmware-updates.md) detalla los códigos de salida, cómo programar una verificación periódica, qué modifica el parámetro `--fix`, qué marcas conservan el launcher original y cuáles desactivan el actualizador del sistema.

## Cuando algo sale mal

La recopilación completa de incidencias y soluciones se halla en [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md). Estos son los tres aspectos fundamentales:

- 🍎 **macOS bloquea `adb connect`** ("No route to host") debido a los permisos de privacidad de red local. `tools/tv.py` solventa el inconveniente sin recurrir al binario del sistema.
- ⚠️ **Desactiva el launcher original únicamente cuando el nuevo se encuentre instalado y operativo**, pues de lo contrario la pantalla de inicio podría abrir una app de pruebas de fábrica. El script de instalación gestiona automáticamente las operaciones en el orden adecuado.
- 🔁 **En Google TV, el asistente de configuración inicial** (`com.google.android.tungsten.setupwraith`) puede tomar el control de la pantalla de inicio y provocar un bucle de cierres forzados tras desactivar el launcher original. Desactívalo también e inclúyelo en tu `debloat.txt` para que el script de restauración pueda reactivarlo si es necesario.
  - Pero antes comprueba `dumpsys wifi | grep -o 'cname=[^ ]*'`: si la conexión Wi-Fi fue guardada durante la configuración inicial, pertenece a `setupwraith`, y desactivarlo borrará la red guardada. Conéctate de nuevo desde Ajustes antes de continuar; `tools/disable.sh` omite `setupwraith` hasta que hayas realizado ese paso.
  - En los dispositivos Chromecast con Google TV y Google TV Streamer, esto impide además la transmisión de contenidos desde el teléfono móvil. Consulta [Solución de problemas](docs/TROUBLESHOOTING.md).
  - ¿Te has quedado bloqueado en esa situación? Ejecuta:
    `tools/tv.py 'pm enable --user 0 com.google.android.apps.tv.launcherx'`.

## Preguntas frecuentes

<details>
<summary><b>No sé programar ni tengo portátil. ¿Puedo utilizarlo de todas formas?</b></summary>

Sí. Desarrolladores independientes han creado aplicaciones gratuitas y de código abierto a partir de las listas de televisores de CleanYourTV. Realizan la optimización a través de la red Wi-Fi sin precisar un asistente de código. Cada aplicación presenta primero un plan de cambios, se limita únicamente a desactivar aplicaciones y ofrece la posibilidad de restaurar todo el sistema:

| Aplicación | Plataforma | Creada por |
|---|---|---|
| [Clean Your TV app](https://github.com/udhaykumarbala/cleanyourtv-app) | Móvil Android | [@udhaykumarbala](https://github.com/udhaykumarbala) |
| [Tellyscope](https://github.com/man15h/tellyscope) | Móvil Android | [@man15h](https://github.com/man15h) |
| [TidyTV](https://github.com/jolan94/tidytv) | Móvil Android, Google Play | [@jolan94](https://github.com/jolan94) |
| [CleanMyTV](https://github.com/wingedpanther/cleanmytv) | Mac y Windows | [@wingedpanther](https://github.com/wingedpanther) |

Dichas aplicaciones son desarrollos independientes, sin relación formal ni respaldo por parte de CleanYourTV o de su mantenedor. Cada una se ofrece en el estado en que se encuentra bajo su propia licencia. Notifica cualquier inconveniente en sus respectivos repositorios. Consulta más información en [cleanyour.tv/app](https://cleanyour.tv/app).

</details>

<details>
<summary><b>¿Es seguro? ¿Puedo dejar inservible mi televisor?</b></summary>

Nada se escribe en el almacenamiento del firmware, no se utiliza acceso root ni se borra ningún archivo; los paquetes únicamente se desactivan para el perfil de usuario. El posible riesgo reside en desactivar algún componente del que dependa el televisor. Por este motivo, las listas se aplican en pequeños bloques y el script de restauración debe estar preparado. La propia pantalla de Ajustes → Aplicaciones del televisor permite volver a activar paquetes, incluso sin necesidad de herramientas externas. Si el televisor no iniciara lo suficiente para acceder a ella, habitualmente es posible restaurarlo de fábrica mediante el menú de recuperación (recovery).

Más información: [docs/faq.md](docs/faq.md#is-this-safe-can-it-brick-the-tv)

</details>

<details>
<summary><b>¿Qué ocurre con las actualizaciones de firmware?</b></summary>

El actualizador del sistema permanece activo en la mayoría de las listas, por lo que las actualizaciones de firmware continúan recibiéndose con normalidad. Ciertas listas desactivan el propio actualizador del televisor; en esos casos, las actualizaciones no se instalarán hasta que vuelvas a activarlo. Cuando se instala una actualización, esta sobrescribe partes del sistema configurado, haciendo que algunos paquetes reaparezcan. En tal circunstancia, basta con ejecutar el script nuevamente: consulta [Después de una actualización de firmware](#después-de-una-actualización-de-firmware).

</details>

<details>
<summary><b>¿Dejarán de funcionar mis aplicaciones?</b></summary>

Las aplicaciones de streaming siguen recibiendo sus actualizaciones mediante Google Play Store, la cual permanece inalterada. El launcher únicamente cataloga las aplicaciones instaladas y las abre mediante intents estándar de Android que llevan funcionando de forma estable más de una década.

</details>

<details>
<summary><b>¿Cómo reordeno mis aplicaciones?</b></summary>

Mantén OK pulsado sobre **Ajustes** (en la esquina superior derecha) y selecciona **Reordenar aplicaciones**. Pulsa OK para seleccionar una aplicación, usa las flechas de dirección para moverla, pulsa Atrás para fijar su nueva posición y pulsa Atrás otra vez para finalizar. Mantener OK pulsado sobre un elemento ofrece también la opción **Mover** (`tile_menu`, habilitada por defecto), que selecciona la aplicación inmediatamente. Más información: [docs/launcher.md](docs/launcher.md#launcher-settings-and-the-default-home)

</details>

<details>
<summary><b>¿Puedo hacerlo en casa de otra persona?</b></summary>

Sí, recurriendo a una conexión privada mediante [Tailscale](https://tailscale.com), en lugar de tener que compartir la misma red Wi-Fi local. Puedes instalar Tailscale en el televisor de la otra persona y compartir el acceso con tu cuenta, o emplear un ordenador o Apple TV en su domicilio para redirigir la conexión. Nunca lleves a cabo este procedimiento abriendo el puerto ADB directamente en el router de su vivienda. Consulta el procedimiento en [docs/REMOTE.md](docs/REMOTE.md).

</details>

<details>
<summary><b>¿El acceso ADB no supone una vulnerabilidad de seguridad?</b></summary>

Es necesario pulsar una vez sobre "Permitir siempre desde este ordenador" en la pantalla del televisor, exactamente igual que durante el desarrollo de una app para Android. ADB en red transmite los datos sin cifrar por el puerto 5555 y, en ciertos firmwares, cualquier dispositivo en la red local podría abrir una terminal. Por este motivo, cierra dicho puerto al finalizar:

1. En el televisor: Opciones para desarrolladores → desactiva **Depuración por USB**, **Depuración de red/ADB** y **Depuración inalámbrica**, según las opciones disponibles en tu modelo, y revoca las autorizaciones de depuración USB.
2. Reinicia el televisor y, en el ordenador, confirma que el puerto ha quedado cerrado:
   `python3 tools/adb-closed.py <tv ip>` debe indicar que el puerto está cerrado.

El launcher continuará funcionando con total normalidad sin que la depuración permanezca activa. Más detalles, incluyendo cómo comprobar el puerto específico de la depuración inalámbrica: [docs/faq.md](docs/faq.md#isnt-adb-access-a-security-hole)

</details>

<details>
<summary><b>¿Bloquea también la publicidad dentro de las aplicaciones?</b></summary>

Parcialmente, mediante el DNS privado de Android (Android 9 o superior). Configura un servidor DNS que filtre publicidad, como [AdGuard DNS](https://adguard-dns.io), y los dominios de anuncios y rastreadores de todas las aplicaciones dejarán de resolver en todo el sistema, incluso tras reiniciar. Como contrapartida: dicho proveedor procesará las consultas DNS emitidas por el televisor, y esto no elimina los anuncios insertados en los vídeos de YouTube.

Más información, con los dos comandos necesarios y las instrucciones para restablecer los valores originales: [docs/faq.md](docs/faq.md#can-it-block-ads-inside-apps-too)

</details>

<details>
<summary><b>¿Hay opciones en los propios Ajustes del televisor que convenga desactivar?</b></summary>

Sí, varias, y ninguna de ellas requiere ADB: Identificador de publicidad, Ubicación, Uso y diagnóstico y Búsqueda de redes siempre disponible, además del modo Solo aplicaciones en caso de conservar el launcher original. Desactiva Uso y diagnóstico antes de limpiar las aplicaciones; en algunos modelos de televisor, la página correspondiente a dicha opción se elimina durante el proceso.

Más información, con las rutas exactas para Google TV, Android TV y Fire OS: [docs/faq.md](docs/faq.md#are-there-switches-in-the-tvs-own-settings-worth-flipping-too)

</details>

<details>
<summary><b>¿AirPlay?</b></summary>

No mediante este repositorio directamente. Chromecast integrado sigue operativo para dispositivos Android. Para iPhone, la solución comunitaria [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server) funciona adecuadamente; dispones de un script de instalación en la [PR #84](https://github.com/Gathin23/cleanyourtv/pull/84).

</details>

<details>
<summary><b>¿Puedo simplemente descargar el APK?</b></summary>

Compílalo con el script de una sola línea indicado en [Hacerlo manualmente](#hacerlo-manualmente). El proceso toma cerca de un minuto y te otorga el control absoluto sobre el software que se instala en tu televisor. Para ello son necesarios el JDK y el Android SDK descritos en dicha sección. No se distribuyen binarios precompilados.

</details>

## La historia

El 17 de octubre de 2026 publiqué [una comparativa de antes y después](https://x.com/gathint/status/2111379569030939102) del televisor de mis padres, optimizado con Claude Code a través de Wi-Fi. La publicación alcanzó 8,6 millones de visualizaciones, otros usuarios comenzaron a realizar lo mismo en sus propios televisores y sus respuestas dieron origen a las listas de este repositorio: más de 170 modelos de televisor, aportados por más de 200 colaboradores, en los primeros cuatro días. Hasta el 19 de octubre el repositorio se denominó tv-vibehack; los enlaces antiguos continúan redirigiendo correctamente.

Dos semanas más tarde, el proyecto se convirtió en el repositorio n.º 1 en tendencias en GitHub durante la semana del 19 al 25 de octubre, sumando más de 46 000 estrellas y 5 700 forks, así como 1,4 millones de visualizaciones y 260 000 clones en 14 días. Las listas ahora abarcan 472 modelos de TV de 129 marcas. La repercusión: 534 artículos de 57 países y 663 vídeos en YouTube y Bilibili, todos recopilados en [docs/PRESS.md](docs/PRESS.md) (datos del 9 de noviembre de 2026).

## Cómo contribuir

La contribución más valiosa es la lista de limpieza de aplicaciones de tu televisor. De ese modo, la siguiente persona con tu mismo modelo no tendrá que averiguar todo desde cero. [CONTRIBUTING.md](CONTRIBUTING.md) expone el formato y las comprobaciones requeridas. Si colaboras mediante un asistente de programación, [AGENTS.md](AGENTS.md) describe las mismas directrices para él.

| Cómo ayudar | Qué significa |
|---|---|
| **Lista de limpieza de tu televisor** | Es la colaboración más útil: la próxima persona con ese modelo se ahorrará toda la etapa de identificación. |
| **Launcher y prompt** | Las mejoras en el launcher y los refinamientos para que el prompt resulte más claro son igualmente bienvenidos. |
| **¿Has creado algo a partir de este proyecto?** | Un launcher, una adaptación o un tema: añádelo a [SHOWCASE.md](SHOWCASE.md), que ya reúne 243 proyectos organizados en pantallas de inicio, forks, complementos, herramientas y otras plataformas, con un índice por marca de televisor. |

Las personas cuyo trabajo forma parte de este repositorio:

<a href="https://github.com/Gathin23/cleanyourtv/graphs/contributors"><img src="https://contrib.rocks/image?repo=Gathin23/cleanyourtv&columns=16&max=400" alt="Colaboradores"></a>

<details>
<summary><strong>Historial de estrellas</strong></summary>

<a href="https://star-history.com/#Gathin23/cleanyourtv&Date"><img src="https://api.star-history.com/svg?repos=Gathin23/cleanyourtv&type=Date" alt="Gráfico del historial de estrellas" width="640"></a>

</details>

## Contacto

| Motivo | Dónde |
|---|---|
| **Preguntas sobre tu televisor** | [GitHub Discussions](https://github.com/Gathin23/cleanyourtv/discussions) |
| **Prensa y consultas generales** | [hello@itsgathin.com](mailto:hello@itsgathin.com) |
| **Problemas de seguridad** | Notifícalos de forma privada mediante la pestaña [Security](https://github.com/Gathin23/cleanyourtv/security). Consulta también [`SECURITY.md`](SECURITY.md). |

## Información legal

- CleanYourTV es un proyecto independiente de código abierto. No está afiliado, respaldado ni patrocinado por ningún fabricante de televisores ni plataforma. Los nombres comerciales y modelos se emplean exclusivamente para señalar en qué dispositivos ha sido probada una lista y continúan siendo marcas registradas de sus respectivos titulares.
- El proyecto opera únicamente mediante las configuraciones que el propio televisor pone a disposición de su propietario: Opciones para desarrolladores, depuración ADB y el comando `pm disable-user` de Android, equivalente a la opción Desactivar en Ajustes > Aplicaciones. No realiza root en el televisor, no escribe firmware y no elude mecanismos de bloqueo, DRM o protección contra copia. El repositorio no contiene software, firmware ni archivos APK de fabricantes.
- Nada se desinstala, y `tools/restore.sh` restaura la totalidad de los cambios. Realizas el proceso en un televisor de tu propiedad, bajo tu entera responsabilidad. Modificar el software de un televisor puede repercutir en su garantía o servicio técnico oficial.
- La totalidad del contenido se proporciona "tal cual", sin garantías de ninguna naturaleza, bajo la licencia MIT indicada a continuación.

## Licencia

MIT. Consulta [`LICENSE`](LICENSE). La tipografía Outfit se distribuye bajo la licencia SIL Open Font License; consulta `launcher/FONT-LICENSE-OFL.txt`.
