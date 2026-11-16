#!/usr/bin/env bash
# ==============================================================================
# Pipeline Interativo de Customização para Receptor Vivensis VX Smart (Google TV)
# Baseado no projeto CleanYourTV (https://github.com/Gathin23/cleanyourtv)
# ==============================================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -d "$ROOT_DIR/tools" ] && [ -d "$ROOT_DIR/launcher" ]; then
  REPO_DIR="$ROOT_DIR"
else
  REPO_DIR="$ROOT_DIR/cleanyourtv"
fi
PY="$REPO_DIR/tools/venv/bin/python"

echo "======================================================================"
echo "  Pipeline de Customizacao - Vivensis VX Smart (Android TV / Google TV)"
echo "======================================================================"
echo ""

# ------------------------------------------------------------------------------
# 1. Verificacao do Ambiente Local (Repositorio, Python venv, JDK 17-22, SDK)
# ------------------------------------------------------------------------------
if [ ! -d "$REPO_DIR/tools" ] || [ ! -d "$REPO_DIR/launcher" ]; then
  echo "[!] Pasta cleanyourtv nao encontrada em $REPO_DIR."
  echo "    Clonando repositorio oficial..."
  git clone https://github.com/Gathin23/cleanyourtv "$REPO_DIR"
fi

if [ ! -x "$PY" ]; then
  echo "[*] Criando ambiente virtual Python em cleanyourtv/tools/venv..."
  python3 -m venv "$REPO_DIR/tools/venv"
  "$REPO_DIR/tools/venv/bin/pip" install -q -r "$REPO_DIR/tools/requirements.txt"
fi

# Auto-detecta JDK 17 ou 21 (necessario para Gradle 8.9 quando o sistema usa JDK 23+)
if [ -z "${JAVA_HOME:-}" ]; then
  for jdir in /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-21-openjdk-amd64 /opt/homebrew/opt/openjdk@17; do
    if [ -x "$jdir/bin/java" ] && [ -x "$jdir/bin/jlink" ]; then
      export JAVA_HOME="$jdir"
      break
    fi
  done
fi

if [ -n "${JAVA_HOME:-}" ]; then
  echo "[OK] Usando JDK em: $JAVA_HOME"
fi

# ------------------------------------------------------------------------------
# 2. Conexao ADB com o Receptor Vivensis
# ------------------------------------------------------------------------------
DEFAULT_IP="${TV_HOST:-192.168.1.50}"
read -r -p "Digite o endereco IP do seu receptor Vivensis [$DEFAULT_IP]: " INPUT_IP
export TV_HOST="${INPUT_IP:-$DEFAULT_IP}"

echo ""
echo "[*] Testando conexao com o receptor em $TV_HOST (porta 5555)..."
echo "    (Se aparecer 'Permitir depuracao USB?' na TV, marque 'Sempre permitir' e clique em OK)"
PROBE_OUT=$("$PY" -I "$REPO_DIR/tools/tv.py" --probe) || {
  echo "[ERRO] Nao foi possivel conectar em $TV_HOST:5555."
  echo "       Verifique se a Depuracao USB esta ativada nas Opcoes do Desenvolvedor do receptor."
  exit 1
}
echo "$PROBE_OUT"
echo ""

# ------------------------------------------------------------------------------
# 3. Backup Completo de Seguranca
# ------------------------------------------------------------------------------
BACKUP_DIR="$ROOT_DIR/backup/$TV_HOST"
mkdir -p "$BACKUP_DIR"
echo "[*] Salvando backup completo do estado atual em: backup/$TV_HOST/ ..."
"$PY" -I "$REPO_DIR/tools/tv.py" 'getprop' > "$BACKUP_DIR/getprop.txt"
"$PY" -I "$REPO_DIR/tools/tv.py" 'pm list packages' | tr -d '\r' | sort > "$BACKUP_DIR/packages_all.txt"
"$PY" -I "$REPO_DIR/tools/tv.py" 'pm list packages -d' | tr -d '\r' | sort > "$BACKUP_DIR/packages_disabled.txt"
"$PY" -I "$REPO_DIR/tools/tv.py" 'settings list global' | tr -d '\r' | sort > "$BACKUP_DIR/settings_global.txt"
"$PY" -I "$REPO_DIR/tools/tv.py" 'settings list secure' | tr -d '\r' | sort > "$BACKUP_DIR/settings_secure.txt"
"$PY" -I "$REPO_DIR/tools/tv.py" 'settings list system' | tr -d '\r' | sort > "$BACKUP_DIR/settings_system.txt"

cat > "$BACKUP_DIR/restore.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
cd "$REPO_DIR"
export TV_HOST="\${TV_HOST:-$TV_HOST}"
./tools/restore.sh examples/vivensis-vx-smart/debloat.txt com.vivensis.launcher
EOF
chmod +x "$BACKUP_DIR/restore.sh"
echo "[OK] Backup concluido! Script de restauracao criado em backup/$TV_HOST/restore.sh"
echo ""

# ------------------------------------------------------------------------------
# 4. Escolha de Limpeza (Debloat) e DNS
# ------------------------------------------------------------------------------
echo "Escolha o perfil de limpeza (Debloat):"
echo "  [1] (Padrao) Limpeza Segura mantendo funcoes do Google TV"
echo "      (Desativa 13 pacotes inuteis/telemetria; mantem canais Vivensis, OTA, Assistente de Voz, Ambient Mode e Recomendacoes)"
echo "  [2] Limpeza Maxima (Desativa tambem Recomendacoes, Ambient Mode e Partner Setup)"
echo "  [3] Pular limpeza de pacotes (Apenas instalar/configurar o Launcher)"
read -r -p "Opcao [1]: " DEBLOAT_OPT
DEBLOAT_OPT="${DEBLOAT_OPT:-1}"

if [ "$DEBLOAT_OPT" = "1" ]; then
  echo "[*] Aplicando lista oficial examples/vivensis-vx-smart/debloat.txt ..."
  (cd "$REPO_DIR" && TV_HOST="$TV_HOST" ./tools/disable.sh examples/vivensis-vx-smart/debloat.txt)
elif [ "$DEBLOAT_OPT" = "2" ]; then
  TMP_LIST="$BACKUP_DIR/debloat-max.txt"
  cat "$REPO_DIR/examples/vivensis-vx-smart/debloat.txt" > "$TMP_LIST"
  printf '\ncom.google.android.backdrop\ncom.google.android.tvrecommendations\ncom.google.android.partnersetup\n' >> "$TMP_LIST"
  echo "[*] Aplicando lista de limpeza maxima..."
  (cd "$REPO_DIR" && TV_HOST="$TV_HOST" ./tools/disable.sh "$TMP_LIST")
else
  echo "[*] Etapa de debloat ignorada."
fi
echo ""

echo "Como deseja configurar o DNS Privado (AdBlock)?"
echo "  [1] (Padrao) Manter desativado (private_dns_mode=off)"
echo "  [2] Ativar AdGuard DNS (dns.adguard-dns.com)"
read -r -p "Opcao [1]: " DNS_OPT
DNS_OPT="${DNS_OPT:-1}"

if [ "$DNS_OPT" = "2" ]; then
  "$PY" -I "$REPO_DIR/tools/tv.py" 'settings put global private_dns_specifier dns.adguard-dns.com; settings put global private_dns_mode hostname'
  echo "[OK] AdGuard DNS ativado."
else
  "$PY" -I "$REPO_DIR/tools/tv.py" 'settings put global private_dns_mode off; settings delete global private_dns_specifier >/dev/null 2>&1 || true'
  echo "[OK] DNS Privado (AdBlock) mantido desativado."
fi
echo ""

# ------------------------------------------------------------------------------
# 5. Configuracao do Dock e Customizacoes do Launcher para o Vivensis
# ------------------------------------------------------------------------------
DEFAULT_DOCK="com.google.android.youtube.tv,com.amazon.amazonvideo.livingroom,com.apple.atve.androidtv.appletv,com.wbd.stream,com.netflix.ninja,com.disney.disneyplus"
echo "Aplicativos padrao do Dock (6 espacos):"
echo "  YouTube, Prime Video, Apple TV, HBO Max, Netflix, Disney+"
read -r -p "Pressione ENTER para manter essa ordem ou digite os pacotes separados por virgula: " INPUT_DOCK
DOCK_CSV="${INPUT_DOCK:-$DEFAULT_DOCK}"

# Atualiza o config.json do perfil vivensis-vx-smart com os apps escolhidos
"$PY" - "$REPO_DIR/examples/vivensis-vx-smart/config.json" "$DOCK_CSV" <<'PYEOF'
import json, sys
path, csv_str = sys.argv[1], sys.argv[2]
apps = [x.strip() for x in csv_str.split(",") if x.strip()]
with open(path, "r", encoding="utf-8") as f:
    cfg = json.load(f)
cfg["dock_apps"] = apps
cfg["columns"] = len(apps) if 4 <= len(apps) <= 7 else 6
cfg["live_tv_package"] = "com.vivensis.launcher"
cfg["live_tv_action"] = "android.intent.action.VIEW"
cfg["stock_home_redirect"] = "com.vivensis.launcher"
cfg["home_long_press_home"] = True
cfg["restore_key_services"] = True
with open(path, "w", encoding="utf-8") as f:
    json.dump(cfg, f, indent=2)
    f.write("\n")
PYEOF

echo ""
echo "[*] Compilando e instalando o Launcher CleanYourTV (preservando com.vivensis.launcher)..."
(
  cd "$REPO_DIR"
  TV_HOST="$TV_HOST" \
  STOCK_LAUNCHER=none \
  HOME_KEY_SERVICE=1 \
  RESTORE_KEY_SERVICES=1 \
  ./launcher/build-and-install.sh
)

# ------------------------------------------------------------------------------
# 6. Verificacao Critica de Armazenamento USB (/mnt/expand -> Memória Interna)
# ------------------------------------------------------------------------------
echo ""
echo "[*] Verificando se o launcher foi instalado em pendrive USB (/mnt/expand)..."
CODE_PATH=$("$PY" -I "$REPO_DIR/tools/tv.py" 'dumpsys package com.gathin.tvhome | grep codePath=' | tr -d '\r' || true)
case "$CODE_PATH" in
  */mnt/expand/*)
    echo "[!] Detectado que o Android instalou o launcher no pendrive USB ($CODE_PATH)."
    echo "    Movendo automaticamente para a memoria flash interna (internal) para não perder o Home no reboot..."
    "$PY" -I "$REPO_DIR/tools/tv.py" 'pm move-package com.gathin.tvhome internal; sleep 3'
    ;;
  *)
    echo "[OK] Launcher instalado diretamente na memoria interna ($CODE_PATH)."
    ;;
esac

# Aplica o config.json do Vivensis e garante que HomeKeyService + set-home-activity fiquem gravados
(cd "$REPO_DIR" && TV_HOST="$TV_HOST" ./tools/theme.sh examples/vivensis-vx-smart)

"$PY" -I "$REPO_DIR/tools/tv.py" 'am start -W -n com.gathin.tvhome/.MainActivity >/dev/null 2>&1; pm grant com.gathin.tvhome android.permission.WRITE_SECURE_SETTINGS; cmd appops set com.gathin.tvhome AUTO_START allow 2>/dev/null; cmd appops set com.gathin.tvhome APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; settings put secure enabled_accessibility_services ""; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 1; cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity; sync'

echo ""
echo "======================================================================"
echo "  Customizacao Concluida com Sucesso no Vivensis VX Smart ($TV_HOST)!"
echo "======================================================================"
echo "  - Botao Home e reinicializacao configurados para abrir o 'Inicio'"
echo "  - Botao 'TV' no topo configurado para abrir os canais via satelite"
echo "  - Para desfazer tudo no futuro, execute: backup/$TV_HOST/restore.sh"
echo "======================================================================"
