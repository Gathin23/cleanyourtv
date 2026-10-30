# TD Systems CH17F40S (Changhong, redwood, DVB92TD)

- **Modelo:** TD Systems CH17F40S (ID Producto 10215098)
- **Fabricante / Placa:** Changhong / `redwood` (DVB92TD)
- **Sistema operativo:** Android TV 14 (SDK 34), build `UKNV.260514.001`
- **Launcher de fábrica:** `com.google.android.tvlauncher`
- **Sintonizador TDT / Entradas:** `com.mediatek.wwtv.tvcenter` y `com.mediatek.tvinput`

---

## 1. Restaurar el televisor al estado original

Para restaurar todos los paquetes desactivados y volver a poner el launcher de fábrica como pantalla de inicio:

```powershell
tools\restore.ps1 -TvHost <tv ip> examples\tdsystems-ch17f40s-redwood\debloat.txt com.google.android.tvlauncher
```

O para restaurar una aplicación individual por comando:
```powershell
tools\venv\Scripts\python.exe tools\tv.py "pm enable --user 0 <nombre_del_paquete>"
```
Por ejemplo, para reactivar el limpiador de Homwee:
```powershell
tools\venv\Scripts\python.exe tools\tv.py "pm enable --user 0 com.homwee.chtvmanager"
```

---

## 2. Cambiar las aplicaciones del Dock o su orden

El orden actual fijado en el Dock es:
1. **TDT** (`com.mediatek.wwtv.tvcenter`)
2. **Netflix** (`com.netflix.ninja`)
3. **YouTube** (`com.google.android.youtube.tv`)
4. **Google Play Store** (`com.android.vending`)

Para modificar las aplicaciones del Dock:
1. Edita `launcher\app\src\main\res\values\config.xml` en la sección `<string-array name="dock_apps">`.
2. Vuelve a compilar e instalar el launcher con:
   ```powershell
   cd launcher
   powershell -ExecutionPolicy Bypass -File .\build-and-install.ps1 -TvHost <tv ip> -StockLauncher none -HomeKeyService
   ```

---

## 3. Cambiar el Fondo de Pantalla (Wallpaper)

Puedes enviar cualquier imagen JPEG o PNG como fondo de pantalla del televisor utilizando el script `tools\theme.ps1`:
```powershell
tools\theme.ps1 -TvHost <tv ip> -Wallpaper ruta\a\tu\imagen.jpg
```
O aplicar alguno de los temas incluidos en la carpeta `themes/`:
```powershell
tools\theme.ps1 -TvHost <tv ip> themes\apple-tv
```

---

## 4. Desactivar el DNS Privado AdGuard (Undo)

Si en algún momento deseas desactivar el bloqueador de anuncios AdGuard DNS y volver al DNS predeterminado:
```powershell
tools\venv\Scripts\python.exe tools\tv.py "settings put global private_dns_mode opportunistic"
```

---

## 5. Panel de Control y Automatización

Puedes gestionar y volver a aplicar todas las configuraciones fácilmente ejecutando:

- **`reaplicar-tdsystems.bat`** (doble clic en la raíz de la carpeta).
  - Te pregunta la IP de la TV por si hubiera cambiado.
  - **Opción 1 (Hacer todo a la vez):** Quita telemetría y bloatware, activa AdGuard DNS, asegura el launcher Apple TV con tu dock fijo (TDT, Netflix, YouTube, Play Store) y te permite elegir el tema o fondo de pantalla que quieras (o una foto propia).
  - **Opción 2 (Cambiar tema / fondo):** Cambia el tema visual o pon cualquier foto propia como wallpaper conservando tu orden de aplicaciones.
  - **Opción 3 (Revertir todo a estado de fábrica):** Reactiva todas las aplicaciones originales, restaura el launcher de fábrica y el DNS original.
  - **Ventana de confirmación:** Al terminar cada proceso, muestra un diálogo gráfico interactivo con botones **Aceptar**, **Reintentar** o **Cancelar**.

---

## 6. Cierre seguro de ADB al terminar

Por seguridad, una vez concluida la configuración en el televisor:
1. En el televisor ve a: **Ajustes** → **Preferencias del dispositivo** → **Opciones de desarrollador**.
2. Desactiva **Depuración por USB**.
3. Pulsa en **Revocar autorizaciones de depuración por USB**.
4. Reinicia el televisor.
5. Comprueba que el puerto ADB 5555 quede cerrado:
   ```powershell
   tools\venv\Scripts\python.exe tools\adb-closed.py <tv ip>
   ```
