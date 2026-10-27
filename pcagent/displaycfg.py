"""Telas do Windows pela API SetDisplayConfig: acender só a TV, sem depender do que o Windows guardou.

O DisplaySwitch /external usa a configuração que o Windows salvou para "só a segunda tela"; se ela ficou
errada (ex.: apontando para o monitor), ele repete o erro. Aqui o caminho da TV é escolhido pelo código do
monitor (EDID), ativado sozinho, e a escolha é gravada no banco de configurações do Windows.
"""
import ctypes
import ctypes.wintypes as wt

user32 = ctypes.windll.user32

QDC_ALL_PATHS = 0x1
QDC_ONLY_ACTIVE_PATHS = 0x2
SDC_APPLY = 0x80
SDC_USE_SUPPLIED_DISPLAY_CONFIG = 0x20
SDC_SAVE_TO_DATABASE = 0x200
SDC_ALLOW_CHANGES = 0x400
SDC_TOPOLOGY_INTERNAL = 0x1
SDC_TOPOLOGY_EXTEND = 0x4
PATH_ACTIVE = 0x1
MODE_IDX_INVALID = 0xFFFFFFFF
GET_TARGET_NAME = 2


class LUID(ctypes.Structure):
    _fields_ = [("LowPart", wt.DWORD), ("HighPart", wt.LONG)]


class RATIONAL(ctypes.Structure):
    _fields_ = [("Numerator", ctypes.c_uint32), ("Denominator", ctypes.c_uint32)]


class PATH_SOURCE(ctypes.Structure):
    _fields_ = [("adapterId", LUID), ("id", ctypes.c_uint32), ("modeInfoIdx", ctypes.c_uint32),
                ("statusFlags", ctypes.c_uint32)]


class PATH_TARGET(ctypes.Structure):
    _fields_ = [("adapterId", LUID), ("id", ctypes.c_uint32), ("modeInfoIdx", ctypes.c_uint32),
                ("outputTechnology", ctypes.c_uint32), ("rotation", ctypes.c_uint32), ("scaling", ctypes.c_uint32),
                ("refreshRate", RATIONAL), ("scanLineOrdering", ctypes.c_uint32), ("targetAvailable", wt.BOOL),
                ("statusFlags", ctypes.c_uint32)]


class PATH_INFO(ctypes.Structure):
    _fields_ = [("sourceInfo", PATH_SOURCE), ("targetInfo", PATH_TARGET), ("flags", ctypes.c_uint32)]


class MODE_INFO(ctypes.Structure):
    _fields_ = [("infoType", ctypes.c_uint32), ("id", ctypes.c_uint32), ("adapterId", LUID),
                ("data", ctypes.c_byte * 48)]


class HEADER(ctypes.Structure):
    _fields_ = [("type", ctypes.c_uint32), ("size", ctypes.c_uint32), ("adapterId", LUID), ("id", ctypes.c_uint32)]


class TARGET_NAME(ctypes.Structure):
    _fields_ = [("header", HEADER), ("flags", ctypes.c_uint32), ("outputTechnology", ctypes.c_uint32),
                ("edidManufactureId", ctypes.c_uint16), ("edidProductCodeId", ctypes.c_uint16),
                ("connectorInstance", ctypes.c_uint32), ("monitorFriendlyDeviceName", wt.WCHAR * 64),
                ("monitorDevicePath", wt.WCHAR * 128)]


def _query(flags):
    npaths, nmodes = ctypes.c_uint32(), ctypes.c_uint32()
    r = user32.GetDisplayConfigBufferSizes(flags, ctypes.byref(npaths), ctypes.byref(nmodes))
    if r:
        raise OSError("GetDisplayConfigBufferSizes %d" % r)
    paths = (PATH_INFO * npaths.value)()
    modes = (MODE_INFO * nmodes.value)()
    r = user32.QueryDisplayConfig(flags, ctypes.byref(npaths), paths, ctypes.byref(nmodes), modes, None)
    if r:
        raise OSError("QueryDisplayConfig %d" % r)
    return list(paths[:npaths.value]), list(modes[:nmodes.value])


def target_path(t):
    """Device path of the monitor on a path target (contains the EDID code, e.g. XXX8000)."""
    n = TARGET_NAME()
    n.header.type = GET_TARGET_NAME
    n.header.size = ctypes.sizeof(n)
    n.header.adapterId = t.adapterId
    n.header.id = t.id
    if user32.DisplayConfigGetDeviceInfo(ctypes.byref(n)):
        return ""
    return n.monitorDevicePath + " " + n.monitorFriendlyDeviceName


def monitors():
    """[(nome do monitor, ativo)] de tudo que está ligado nas saídas."""
    paths, _ = _query(QDC_ALL_PATHS)
    seen = {}
    for p in paths:
        if not p.targetInfo.targetAvailable:
            continue
        key = (p.targetInfo.adapterId.LowPart, p.targetInfo.id)
        name = target_path(p.targetInfo)
        seen[key] = (name, seen.get(key, (name, False))[1] or bool(p.flags & PATH_ACTIVE))
    return list(seen.values())


def only(monitor_code):
    """Acende só o monitor cujo código (EDID) contém monitor_code. True se deu certo."""
    paths, _ = _query(QDC_ALL_PATHS)
    for p in paths:
        t = p.targetInfo
        if not t.targetAvailable or monitor_code.lower() not in target_path(t).lower():
            continue
        p.flags = PATH_ACTIVE
        p.sourceInfo.modeInfoIdx = MODE_IDX_INVALID
        p.targetInfo.modeInfoIdx = MODE_IDX_INVALID
        arr = (PATH_INFO * 1)(p)
        r = user32.SetDisplayConfig(1, arr, 0, None,
                                    SDC_APPLY | SDC_USE_SUPPLIED_DISPLAY_CONFIG | SDC_ALLOW_CHANGES | SDC_SAVE_TO_DATABASE)
        if r == 0:
            return True
    return False


def topology(kind):
    """'internal' (só o monitor principal) ou 'extend', pelo que o Windows tem guardado."""
    return user32.SetDisplayConfig(0, None, 0, None,
                                   SDC_APPLY | (SDC_TOPOLOGY_EXTEND if kind == "extend" else SDC_TOPOLOGY_INTERNAL)) == 0
