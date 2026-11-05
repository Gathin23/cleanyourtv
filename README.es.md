<p align="center">
  <a href="https://cleanyour.tv"><img src="docs/banner.png" alt="CleanYourTV: Tu televisor, sin anuncios" width="800"></a>
</p>

<p align="center">
  <a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="README.zh-TW.md">正體中文</a> · <a href="README.vi.md">Tiếng Việt</a> · <a href="README.ja.md">日本語</a> · <a href="README.it.md">Italiano</a> · <a href="README.nl.md">Nederlands</a> · Español
</p>

<p align="center">
  <a href="https://cleanyour.tv">Sitio del proyecto</a> ·
  <a href="PROMPT.md">Prompt para el asistente</a> ·
  <a href="examples/README.md">Lista de modelos de TV</a> ·
  <a href="SHOWCASE.md">Proyectos de la comunidad</a>
</p>

# CleanYourTV: Guía en español

Conéctate por Wi-Fi a tu Android TV o Google TV, desactiva el bloatware, la publicidad y la telemetría innecesaria, e instala un launcher limpio y minimalista similar a Apple TV en los modelos compatibles. No requiere root, no requiere flashear firmwares y no necesita cables. Las aplicaciones del sistema solo son **desactivadas**, nunca desinstaladas, y se incluye un script para restaurarlo todo con una sola línea.

Esta guía presenta el funcionamiento y uso general. Cada modelo, región y versión de firmware posee componentes particulares; consulta siempre el archivo correspondiente a tu modelo antes de aplicar cambios. En caso de discrepancia, la versión de referencia es el [README principal en inglés](README.md), y las notas por modelo se encuentran en la [lista de ejemplos de TV](examples/README.md).

**¿Tienes un televisor Samsung (Tizen), LG (webOS), Roku o no-Android?** CleanYourTV no puede ejecutarse en esos sistemas, pero puedes desactivar el rastreo manualmente desde el mando a distancia: [cleanyour.tv/not-android](https://cleanyour.tv/not-android).

![Comparativa entre la interfaz original de Google TV y el launcher CleanYourTV](docs/stock-vs-new-side.jpg)

## Cómo funciona

Habilita la depuración USB en las Opciones de Desarrollador del televisor, autoriza la conexión de depuración ADB en red desde tu ordenador y sigue estos tres pasos:

1. **Copia de seguridad:** El script o asistente guarda el estado de los paquetes instalados y sus configuraciones. Si usas el [prompt para asistentes de código](PROMPT.md), el asistente realiza una copia completa antes de tocar el sistema; si ejecutas `tools/disable.sh` o `tools/disable.ps1`, el script almacena automáticamente el registro necesario para revertir los cambios.
2. **Desactivar bloatware:** Desactiva con `pm disable-user --user 0` los servicios de recopilación de datos, la publicidad, las filas de recomendaciones patrocinadas y las aplicaciones de relleno.
3. **Instalar el launcher limpio:** Instala el lanzador de código abierto y configúralo como pantalla de inicio (HOME) predeterminada.

El [prompt de instrucciones](PROMPT.md) guía al asistente de código para inspeccionar tu televisor real y preguntarte qué aplicaciones y funciones deseas conservar antes de desactivar nada.

## Inicio rápido

Necesitas un ordenador conectado a la misma red Wi-Fi que el televisor y un asistente de programación (Claude Code, Gemini CLI, Codex, Cursor o Copilot) con permisos de ejecución en terminal.

| Paso | Qué hacer |
|---|---|
| 1. En el televisor | Ve a **Ajustes → Sistema (o Preferencias del dispositivo) → Información**, pulsa OK siete veces sobre **Compilación**, vuelve atrás, abre **Opciones de desarrollador** y activa la **Depuración por USB**. Si también hay una opción para depuración inalámbrica o por red, actívala. |
| 2. En tu ordenador | Clona este repositorio y abre esa carpeta en tu asistente (`git clone https://github.com/Gathin23/cleanyourtv`), luego pega el contenido de [`PROMPT.md`](PROMPT.md) e indica el modelo de tu televisor. |
| 3. Sigue las instrucciones | Pulsa **Permitir** cuando el televisor solicite autorizar la depuración, luego responde qué aplicaciones utilizas, cuáles deseas en el dock y si prefieres mantener el asistente de voz, Chromecast y actualizaciones del sistema. |
| 4. Verificación | Sigue el procedimiento de copia de seguridad, limpieza e instalación, y luego prueba a abrir aplicaciones, los botones del mando, el cambio de entradas HDMI y la restauración. |

Por qué clonar: el asistente reutiliza `tools/disable.sh` y `launcher/build-and-install.sh` de este repositorio en lugar de escribir los suyos propios, y ahí es donde residen la copia de seguridad, la protección de la pantalla de inicio y el registro de restauración.

---

## Qué ofrece el launcher

El launcher es deliberadamente ligero (ocupa ~532 KB), no contiene publicidad y funciona con fluidez incluso en dispositivos con tan solo 1 GB de memoria RAM:

* **Sin rastreadores ni anuncios:** Cero conexiones a internet en segundo plano, sin filas patrocinadas.
* **Navegación fluida por D-pad:** Control preciso con el mando a distancia.
* **Menú de Entradas:** Acceso directo a puertos HDMI y sintonizador de TV en vivo.
* **Soporte de Temas:** Personalización de colores, estilos minimalistas y fondos en [`themes/`](themes/).

---

## Qué modelos están soportados

Existen más de 340 modelos testeados por la comunidad de marcas como:
* Sony Bravia, Philips, TCL, Hisense, Xiaomi / Redmi, Chromecast con Google TV, Fire TV, Nvidia Shield, Nokia, Motorola, Noblex, BGH, Challenger, Kalley, entre otras.

La lista detallada de modelos con sus notas de configuración se encuentra en [`examples/README.md`](examples/README.md).

---

## Restaurar el launcher y las aplicaciones originales

Si en algún momento deseas revertir todos los cambios y volver al estado de fábrica sin perder tus datos:

* **En Linux / macOS:**
  ```bash
  TV_HOST=<ip-del-televisor> tools/restore.sh examples/<carpeta-de-tu-modelo>/debloat.txt
  ```

* **En Windows (PowerShell):**
  ```powershell
  tools\restore.ps1 -TvHost <ip-del-televisor> examples\<carpeta-de-tu-modelo>\debloat.txt
  ```

El script restaurará el launcher original predeterminado y volverá a habilitar todos los paquetes desactivados según el registro de recuperación guardado.

---

## Solución de problemas comunes

* **El puerto 5555 se rechaza o el televisor usa Depuración Inalámbrica (Android 11+):**
  En televisores modernos con Google TV, la depuración inalámbrica requiere un emparejamiento previo. Empareja una vez con `adb pair <ip>:<puerto-de-emparejamiento>` y conéctate al puerto principal antes de ejecutar las herramientas (más detalles en [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md)).
* **El televisor no responde a comandos:**
  Verifica que el televisor y el ordenador estén en la misma subred Wi-Fi y que no haya aislamiento de punto de acceso (AP Isolation) activo en tu router.

---

## Contribuir

Las contribuciones son bienvenidas:
* **Nuevos modelos:** Si optimizaste un televisor que no está en `examples/`, añade su lista siguiendo [`CONTRIBUTING.md`](CONTRIBUTING.md).
* **Temas visuales:** Diseña nuevos temas en [`themes/`](themes/README.md).
* **Herramientas:** Mejoras de scripts y conectividad en [`tools/`](tools/).

---

## Licencia

Distribuido bajo la [Licencia MIT](LICENSE). Consulta el archivo de licencia para más detalles.
