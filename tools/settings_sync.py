#!/usr/bin/env python3
"""Export, import and inspect SmartTube/MeeTube app settings over ADB.

    settings_sync.py export <tv> [--app PACKAGE] [--out FILE]
    settings_sync.py import <tv> --app TARGET --from SOURCE_PACKAGE --zip FILE [--drive]
    settings_sync.py ui <tv> [--tap TEXT]
    settings_sync.py glossary

The TV argument is an IP address or IP:port. The ADB transport is the repository's tools/tv.py;
this file adds no dependency of its own.
"""
import argparse
import json
import logging
import os
from pathlib import Path, PurePosixPath
import re
import shlex
import sys
import tempfile
from typing import Any, Callable, List, Optional, Sequence, Tuple, Union
from zipfile import ZIP_DEFLATED, BadZipFile, ZipFile
import xml.etree.ElementTree as ET

ALLOWED_TARGETS = {"io.yash.meetube.tv", "org.smarttube.beta"}
DEFAULT_APP = "org.smarttube.beta"
PACKAGE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+$")
UI_REMOTE = "/sdcard/window-cleanyourtv.xml"
RESTORE_KEYS = ("DPAD_UP", "DPAD_RIGHT", "DPAD_RIGHT", "DPAD_DOWN", "DPAD_DOWN", "DPAD_CENTER")

UI_MAP = (
    ("Home", "Settings gear", "Impostazioni"),
    ("Settings", "grid row 1", "Account; Controllo remoto; Lingua/Paese; Generali; Interfaccia utente; Lettore"),
    ("Settings", "grid row 2", "Frequenza fotogrammi automatica; Sottotitoli; Cerca; SponsorBlock; DeArrow; Backup e ripristino"),
    ("Settings", "grid row 3", "Informazioni su"),
    ("Lettore", "panel", "Modalità di riproduzione; Preset video; Imposta i pulsanti del lettore; Motore di rete; Buffer video; Zoom video; Velocità video; Lingua audio; Ritardo audio; Volume"),
    ("Backup e ripristino", "panel", "Backup locale; Google Drive; Importa (GrayJay/PocketTube/NewPipe)"),
    ("Backup locale", "panel", "Backup dati app; Ripristino dati app; Backup automatico"),
    ("Backup automatico", "options", "Nessuno; Una volta al giorno; Una volta alla settimana; Una volta al mese"),
    ("Files", "/sdcard/Documents/SmartTubeBackup", "app backup file"),
    ("Files", "/sdcard/Android/media/<pkg>", "backup root"),
    ("Pref", "files/app_prefs/video_player_data", "9 preset; 12 buffer; 16 AFR; 45 AFR pause ms"),
    ("Pref", "files/app_prefs/video_player_tweaks_data", "4 force SW decoder; 18 prefer AVC over VP9; 26 network engine 0/1/2; 12 tunneled"),
)

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)
_handler = logging.StreamHandler(sys.stdout)
_handler.setLevel(logging.INFO)
_handler.setFormatter(logging.Formatter("%(message)s"))
logger.addHandler(_handler)
logger.propagate = False


def _configure_logging() -> None:
    _handler.setStream(sys.stdout)


def backup_root(package: str, base: str = "/sdcard/Android/media") -> str:
    return f"{base}/{package}/data/{package}/Backup"


def _runner_for(tv: str) -> Any:
    """Connect lazily so glossary and argument errors never import or open ADB."""
    os.environ["TV_HOST"] = tv
    tools = os.path.dirname(os.path.abspath(__file__))
    if tools not in sys.path:
        sys.path.insert(0, tools)
    import tv as tv_client
    return tv_client.connect()


def _close(runner: Any) -> None:
    close = getattr(runner, "close", None)
    if close:
        close()


def _quoted(path: str) -> str:
    return shlex.quote(path)


def _preset_from_bytes(payload: bytes) -> Optional[str]:
    """Decode the measured preset record without exposing unrelated preference contents."""
    text = payload.decode("utf-8", errors="replace").strip("\x00\r\n ")
    if not text:
        return None
    try:
        structured = json.loads(text)
    except (json.JSONDecodeError, TypeError):
        structured = None
    if isinstance(structured, list) and len(structured) > 9:
        text = str(structured[9])
    elif isinstance(structured, dict) and "9" in structured:
        text = str(structured["9"])
    fields = [field.strip() for field in re.split(r"[,;\t|]", text)]
    candidate = fields[9] if len(fields) > 9 and fields[9] else text
    lowered = text.lower()
    codec = "vp9" if "vp9" in lowered else "av1" if "av1" in lowered else "avc" if "avc" in lowered else None
    height = re.search(r"(?:^|[,;\s])(?:1920x)?(2160|1440|1080|720|480)(?:[,;\s]|$)", text)
    fps = re.search(r"(?:^|[,;\s])(\d+(?:\.\d+)?)\s*(?:fps)?(?:[,;\s]|$)",
                    text[height.end():] if height else "")
    if codec and height and fps:
        return f"{height.group(1)}p {float(fps.group(1)):g}fps {codec}"
    return candidate


def _backup_dir(pulled: Path) -> Path:
    pulled = Path(pulled)
    if (pulled / "Backup").is_dir():
        return pulled / "Backup"
    return pulled


def _missing_backup() -> int:
    logger.info("No backup found on the TV. In the app: Backup locale > Backup dati app")
    return 1


def export_backup(tv: str, package: str, output: Union[str, Path], runner: Any) -> int:
    _configure_logging()
    remote = backup_root(package)
    if "PRESENT" not in runner.shell(f"if [ -d {_quoted(remote)} ]; then echo PRESENT; fi"):
        return _missing_backup()
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="cleanyourtv-settings-") as directory:
        pulled = Path(directory) / "pulled"
        pulled.mkdir()
        runner.pull(remote, str(pulled))
        source = _backup_dir(pulled)
        files = sorted(path for path in source.rglob("*") if path.is_file())
        if not files:
            return _missing_backup()
        with ZipFile(output, "w", compression=ZIP_DEFLATED) as archive:
            for path in files:
                relative = path.relative_to(source).as_posix()
                archive.write(path, f"data/{package}/Backup/{relative}")
        logger.info(f"files: {len(files)}")
        preset_file = source / "files/app_prefs/video_player_data"
        if preset_file.is_file():
            preset = _preset_from_bytes(preset_file.read_bytes())
            if preset:
                logger.info(f"preset: {preset}")
        logger.info(f"accounts: {'present' if (source / 'files/global_prefs/media_service_account_data').is_file() else 'absent'}")
    return 0


def _archive_files(archive: Union[str, Path], source_package: str, destination: Union[str, Path]) -> List[Tuple[Path, str]]:
    files = []
    prefix = PurePosixPath("data") / source_package / "Backup"
    destination = Path(destination).resolve()
    with ZipFile(archive) as source:
        for info in source.infolist():
            path = PurePosixPath(info.filename)
            if info.is_dir():
                continue
            if path.is_absolute() or ".." in path.parts or path.parts[:3] != prefix.parts:
                raise ValueError("zip entries must be under data/<source>/Backup")
            relative = PurePosixPath(*path.parts[3:])
            target = (destination / Path(*relative.parts)).resolve()
            if destination not in target.parents:
                raise ValueError("zip entry escapes its temporary directory")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read(info))
            files.append((target, relative.as_posix()))
    if not files:
        raise ValueError("zip contains no backup files")
    return files


def _print_restore_steps() -> None:
    logger.info("restore steps:")
    logger.info("1. Home -> Settings gear (Impostazioni).")
    logger.info("2. Select Backup e ripristino.")
    logger.info("3. Select Backup locale.")
    logger.info("4. Select Ripristino dati app.")
    logger.info("5. Select the copied backup and confirm the restore.")
    logger.info("restore key sequence: " + " ".join(RESTORE_KEYS))


def _validate_target(package: str) -> bool:
    if package not in ALLOWED_TARGETS:
        logger.info(f"refusing target package {package!r}; use io.yash.meetube.tv or org.smarttube.beta")
        return False
    return True


def import_backup(tv: str, target: str, source_package: str,
                  archive: Union[str, Path], drive: bool, runner: Any) -> int:
    _configure_logging()
    if not _validate_target(target):
        return 2
    if not PACKAGE.fullmatch(source_package):
        logger.info(f"invalid source package: {source_package!r}")
        return 2
    remote = f"/sdcard/Android/media/{target}/data/{source_package}/Backup"
    with tempfile.TemporaryDirectory(prefix="cleanyourtv-settings-") as directory:
        try:
            files = _archive_files(archive, source_package, directory)
        except (BadZipFile, OSError, ValueError) as error:
            logger.info(f"cannot read backup zip: {error}")
            return 1
        runner.shell(f"mkdir -p {_quoted(remote)}")
        for local, relative in files:
            runner.push(str(local), f"{remote}/{relative}")
    _print_restore_steps()
    if drive:
        for key in RESTORE_KEYS:
            runner.shell(f"input keyevent KEYCODE_{key}")
    return 0


def _bounds(value: Optional[str]) -> Optional[Tuple[int, int, int, int]]:
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", value or "")
    if not match:
        return None
    left, top, right, bottom = (int(part) for part in match.groups())
    return left, top, right, bottom


def _node_text(node: ET.Element) -> Tuple[str, str, str]:
    return node.attrib.get("text", ""), node.attrib.get("content-desc", ""), node.attrib.get("bounds", "")


def ui(tv: str, tap: Optional[str], runner: Any) -> int:
    _configure_logging()
    with tempfile.TemporaryDirectory(prefix="cleanyourtv-ui-") as directory:
        local = Path(directory) / "window.xml"
        runner.shell(f"uiautomator dump {UI_REMOTE}")
        runner.pull(UI_REMOTE, str(local))
        root = ET.parse(local).getroot()
    nodes = [node for node in root.iter("node") if node.attrib.get("text") or node.attrib.get("content-desc")]
    focused = next((node for node in root.iter("node") if node.attrib.get("focused") == "true"), None)
    if focused is None:
        logger.info("focused: none")
    else:
        text, desc, bounds = _node_text(focused)
        logger.info(f'focused: text="{text}" content-desc="{desc}" bounds="{bounds}"')
    for node in nodes:
        text, desc, bounds = _node_text(node)
        logger.info(f'text="{text}" content-desc="{desc}" bounds="{bounds}"')
    if tap is None:
        return 0
    match = next((node for node in nodes if tap.casefold() in node.attrib.get("text", "").casefold()), None)
    if match is None or _bounds(match.attrib.get("bounds")) is None:
        logger.info(f"no text node contains {tap!r}")
        return 1
    left, top, right, bottom = _bounds(match.attrib["bounds"])
    x, y = (left + right) // 2, (top + bottom) // 2
    runner.shell(f"input tap {x} {y}")
    logger.info(f"tap: {x},{y}")
    return 0


def glossary() -> int:
    _configure_logging()
    logger.info("section | item | measured UI map / index")
    logger.info("--- | --- | ---")
    for section, item, detail in UI_MAP:
        logger.info(f"{section} | {item} | {detail}")
    return 0


def parser() -> argparse.ArgumentParser:
    command = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    subcommands = command.add_subparsers(dest="command", required=True)
    export = subcommands.add_parser("export")
    export.add_argument("tv")
    export.add_argument("--app", default=DEFAULT_APP, metavar="PACKAGE")
    export.add_argument("--out", default="settings-backup.zip", metavar="FILE")
    importing = subcommands.add_parser("import")
    importing.add_argument("tv")
    importing.add_argument("--app", required=True, metavar="TARGET")
    importing.add_argument("--from", dest="source_package", required=True, metavar="SOURCE_PACKAGE")
    importing.add_argument("--zip", required=True, metavar="FILE")
    importing.add_argument("--drive", action="store_true")
    ui_command = subcommands.add_parser("ui")
    ui_command.add_argument("tv")
    ui_command.add_argument("--tap", metavar="TEXT")
    subcommands.add_parser("glossary")
    return command


def main(argv: Optional[Sequence[str]] = None,
         runner_factory: Optional[Callable[[str], Any]] = None) -> int:
    _configure_logging()
    args = parser().parse_args(argv)
    if args.command == "glossary":
        return glossary()
    if args.command == "export" and not PACKAGE.fullmatch(args.app):
        logger.info(f"invalid app package: {args.app!r}")
        return 2
    if args.command == "import" and not _validate_target(args.app):
        return 2
    factory = runner_factory or _runner_for
    runner = factory(args.tv)
    try:
        if args.command == "export":
            return export_backup(args.tv, args.app, args.out, runner)
        if args.command == "import":
            return import_backup(args.tv, args.app, args.source_package, args.zip, args.drive, runner)
        return ui(args.tv, args.tap, runner)
    finally:
        _close(runner)


if __name__ == "__main__":
    raise SystemExit(main())
