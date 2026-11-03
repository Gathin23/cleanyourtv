"""Shared adb test double for the settings_sync tests, so no real TV is opened."""
from pathlib import Path
from typing import Dict, List, Optional, Tuple, Union


class FakeAdb:
    """Stands in for the repository adb wrapper.

    ``files`` answers a pull with a fake backup tree, ``ui_xml`` answers it with a
    uiautomator dump, and the two flags make a test prove a subcommand does not
    touch what it must not touch.
    """

    def __init__(
        self,
        files: Optional[Dict[str, bytes]] = None,
        ui_xml: Optional[str] = None,
        forbid_push: bool = True,
        forbid_pull: bool = False,
    ) -> None:
        self.files = files or {}
        self.ui_xml = ui_xml
        self.forbid_push = forbid_push
        self.forbid_pull = forbid_pull
        self.commands: List[str] = []
        self.pushed: List[Tuple[bytes, str]] = []

    def shell(self, command: str) -> str:
        self.commands.append(command)
        if self.files and command.startswith("if [ -d "):
            return "PRESENT\n"
        return ""

    def pull(self, remote: str, local: Union[str, Path]) -> None:
        if self.forbid_pull:
            raise AssertionError("this subcommand must not pull")
        if self.ui_xml is not None:
            Path(local).write_text(self.ui_xml, encoding="utf-8")
            return
        root = Path(local) / "Backup"
        for path, payload in self.files.items():
            relative = path.split("/Backup/", 1)[1]
            destination = root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(payload)

    def push(self, local: Union[str, Path], remote: str) -> None:
        if self.forbid_push:
            raise AssertionError("this subcommand must not push")
        self.pushed.append((Path(local).read_bytes(), remote))

    def close(self) -> None:
        pass
