#!/usr/bin/env python3

import shlex
from pathlib import Path

import grpc
import smarthome_pb2 as pb
import smarthome_pb2_grpc as rpc

PROJECT_ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = PROJECT_ROOT / "client.config"


def read_servers(config_path):
    if not config_path.exists():
        raise RuntimeError(f"Missing config file: {config_path}")
    for raw in config_path.read_text().splitlines():
        line = raw.strip()
        if line.startswith("servers"):
            value = line.split("=", 1)[1]
            return [ep.strip() for ep in value.split(",") if ep.strip()]
    raise RuntimeError("No 'servers' entry in config")


def power_name(value):
    return pb.PowerState.Name(value).removeprefix("POWER_")


class SmartHomeClient:
    def __init__(self, endpoints):
        self.endpoints = endpoints
        self.channels = {}
        self.stubs = {}
        self.registry = {}
        for ep in endpoints:
            channel = grpc.insecure_channel(ep)
            self.channels[ep] = channel
            self.stubs[ep] = {
                "dir": rpc.DirectoryStub(channel),
                "fridge": rpc.FridgeControlStub(channel),
                "camera": rpc.CameraControlStub(channel),
            }
        self.refresh()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
        return False

    def refresh(self):
        self.registry.clear()
        print("Querying directories...")
        for ep in self.endpoints:
            try:
                reply = self.stubs[ep]["dir"].ListDevices(pb.ListDevicesRequest())
            except grpc.RpcError as e:
                print(f"  ! {ep} unreachable: {e.code().name}")
                continue
            print(f"  {reply.server_name} ({ep}): {len(reply.devices)} devices")
            for d in reply.devices:
                self.registry[d.id] = {
                    "type": d.type,
                    "subtype": d.subtype,
                    "location": d.location,
                    "description": d.description,
                    "server": reply.server_name,
                    "endpoint": ep,
                }

    def list_devices(self):
        if not self.registry:
            print("(no devices)")
            return
        by_server = {}
        for did, meta in self.registry.items():
            by_server.setdefault(meta["server"], []).append((did, meta))
        for server, items in by_server.items():
            print(f"[{server}]")
            for did, meta in items:
                dtype = pb.DeviceType.Name(meta["type"])
                print(f"  {did:<10} {dtype:<6} {meta['subtype']:<8} {meta['location']}")

    def info(self, did):
        meta = self._meta(did)
        dtype = pb.DeviceType.Name(meta["type"])
        print(f"{did}: {dtype} / {meta['subtype']}")
        print(f"  server     : {meta['server']} ({meta['endpoint']})")
        print(f"  location   : {meta['location']}")
        print(f"  description: {meta['description']}")

    # helpers
    def _meta(self, did):
        if did not in self.registry:
            raise KeyError(f"unknown device '{did}' (try 'list' or 'refresh')")
        return self.registry[did]

    def _fridge_stub(self, did):
        meta = self._meta(did)
        if meta["type"] != pb.FRIDGE:
            raise ValueError(f"'{did}' is not a fridge")
        return self.stubs[meta["endpoint"]]["fridge"]

    def _camera_stub(self, did):
        meta = self._meta(did)
        if meta["type"] != pb.CAMERA:
            raise ValueError(f"'{did}' is not a camera")
        return self.stubs[meta["endpoint"]]["camera"]

    # state display
    @staticmethod
    def print_fridge(s):
        print(f"Fridge {s.id} [{pb.FridgeSubtype.Name(s.subtype)}]")
        print(
            f"  power={power_name(s.power)}  door={'open' if s.door_open else 'closed'}"
        )
        print(f"  fridge : {s.fridge_temp_c:.1f}C (target {s.fridge_target_c:.1f}C)")
        which = s.WhichOneof("extras")
        if which == "freezer":
            fz = s.freezer
            print(
                f"  freezer: {fz.freezer_temp_c:.1f}C (target {fz.freezer_target_c:.1f}C)"
            )
        elif which == "smart":
            sm = s.smart
            print(
                f"  freezer: {sm.freezer_temp_c:.1f}C (target {sm.freezer_target_c:.1f}C)"
            )
            print(f"  contents: {', '.join(sm.contents) if sm.contents else '(empty)'}")

    @staticmethod
    def print_camera(s):
        is_ptz = s.WhichOneof("extras") == "ptz"
        print(f"Camera {s.id} [{pb.CameraSubtype.Name(s.subtype)}]")
        print(
            f"  power={power_name(s.power)}  recording={'yes' if s.recording else 'no'}"
            f"  ptz={'yes' if is_ptz else 'no'}"
        )
        if is_ptz:
            p = s.ptz.position
            print(f"  position: pan={p.pan:.1f} tilt={p.tilt:.1f} zoom={p.zoom:.1f}")
            if s.ptz.presets:
                names = ", ".join(pr.name for pr in s.ptz.presets)
                print(f"  presets: {names}")

    def _show(self, did, state):
        if self._meta(did)["type"] == pb.FRIDGE:
            self.print_fridge(state)
        else:
            self.print_camera(state)

    # operations
    def get(self, did):
        meta = self._meta(did)
        if meta["type"] == pb.FRIDGE:
            state = self._fridge_stub(did).Get(pb.FridgeId(id=did))
        else:
            state = self._camera_stub(did).Get(pb.CameraId(id=did))
        self._show(did, state)

    def power(self, did, on):
        p = pb.POWER_ON if on else pb.POWER_OFF
        meta = self._meta(did)
        if meta["type"] == pb.FRIDGE:
            state = self._fridge_stub(did).SetPower(
                pb.SetFridgePowerRequest(id=did, power=p)
            )
        else:
            state = self._camera_stub(did).SetPower(
                pb.SetCameraPowerRequest(id=did, power=p)
            )
        self._show(did, state)

    def temp(self, did, c):
        state = self._fridge_stub(did).SetFridgeTarget(
            pb.SetFridgeTempRequest(id=did, target_c=c)
        )
        self.print_fridge(state)

    def freezer(self, did, c):
        state = self._fridge_stub(did).SetFreezerTarget(
            pb.SetFridgeTempRequest(id=did, target_c=c)
        )
        self.print_fridge(state)

    def additem(self, did, item):
        state = self._fridge_stub(did).AddItem(pb.ItemRequest(id=did, item=item))
        self.print_fridge(state)

    def removeitem(self, did, item):
        state = self._fridge_stub(did).RemoveItem(pb.ItemRequest(id=did, item=item))
        self.print_fridge(state)

    def rec(self, did, on):
        state = self._camera_stub(did).SetRecording(
            pb.SetRecordingRequest(id=did, recording=on)
        )
        self.print_camera(state)

    def move(self, did, dp, dt, dz):
        state = self._camera_stub(did).Move(
            pb.MoveRequest(id=did, pan_delta=dp, tilt_delta=dt, zoom_delta=dz)
        )
        self.print_camera(state)

    def savepreset(self, did, name):
        state = self._camera_stub(did).SavePreset(pb.PresetRequest(id=did, name=name))
        self.print_camera(state)

    def gotopreset(self, did, name):
        state = self._camera_stub(did).GoToPreset(pb.PresetRequest(id=did, name=name))
        self.print_camera(state)

    def removepreset(self, did, name):
        state = self._camera_stub(did).RemovePreset(pb.PresetRequest(id=did, name=name))
        self.print_camera(state)

    def close(self):
        for ch in self.channels.values():
            ch.close()


HELP = """Commands:
  help                          - this help
  list                          - list all known devices
  refresh                       - re-query servers' directories
  info <id>                     - show device metadata
  get <id>                      - read current state
  power <id> on|off             - power a device on/off
  temp <id> <C>                 - set fridge target temp (1..10)
  freezer <id> <C>              - set freezer target temp (-30..-10)
  additem <id> <item...>        - add item to a SMART fridge
  removeitem <id> <item...>     - remove item from a SMART fridge
  rec <id> on|off               - camera recording on/off
  move <id> <pan> <tilt> <zoom> - move PTZ head by deltas
  savepreset <id> <name>        - save current PTZ position as preset
  gotopreset <id> <name>        - move PTZ head to a saved preset
  removepreset <id> <name>      - remove a saved PTZ preset
  quit                          - exit"""


def need(parts, n, usage):
    if len(parts) != n:
        raise ValueError("usage: " + usage)


def dispatch(client, parts):
    cmd = parts[0].lower()
    if cmd == "help":
        print(HELP)
    elif cmd == "list":
        client.list_devices()
    elif cmd == "refresh":
        client.refresh()
    elif cmd == "info":
        need(parts, 2, "info <id>")
        client.info(parts[1])
    elif cmd == "get":
        need(parts, 2, "get <id>")
        client.get(parts[1])
    elif cmd == "power":
        need(parts, 3, "power <id> on|off")
        client.power(parts[1], parts[2].lower() == "on")
    elif cmd == "temp":
        need(parts, 3, "temp <id> <C>")
        client.temp(parts[1], float(parts[2]))
    elif cmd == "freezer":
        need(parts, 3, "freezer <id> <C>")
        client.freezer(parts[1], float(parts[2]))
    elif cmd == "additem":
        if len(parts) < 3:
            raise ValueError("usage: additem <id> <item...>")
        client.additem(parts[1], " ".join(parts[2:]))
    elif cmd == "removeitem":
        if len(parts) < 3:
            raise ValueError("usage: removeitem <id> <item...>")
        client.removeitem(parts[1], " ".join(parts[2:]))
    elif cmd == "rec":
        need(parts, 3, "rec <id> on|off")
        client.rec(parts[1], parts[2].lower() == "on")
    elif cmd == "move":
        need(parts, 5, "move <id> <pan> <tilt> <zoom>")
        client.move(parts[1], float(parts[2]), float(parts[3]), float(parts[4]))
    elif cmd == "savepreset":
        need(parts, 3, "savepreset <id> <name>")
        client.savepreset(parts[1], parts[2])
    elif cmd == "gotopreset":
        need(parts, 3, "gotopreset <id> <name>")
        client.gotopreset(parts[1], parts[2])
    elif cmd == "removepreset":
        need(parts, 3, "removepreset <id> <name>")
        client.removepreset(parts[1], parts[2])
    else:
        print("unknown command; type 'help'")


def main():
    endpoints = read_servers(CONFIG_PATH)
    print(f"Smart home client. Servers: {', '.join(endpoints)}")

    with SmartHomeClient(endpoints) as client:
        client.list_devices()
        print("Type 'help' for commands.")

        while True:
            try:
                raw = input("> ").strip()
            except (EOFError, KeyboardInterrupt):
                print()
                break
            if not raw:
                continue
            try:
                parts = shlex.split(raw)
            except ValueError as ex:
                print(f"parse error: {ex}")
                continue
            if parts[0].lower() in ("quit", "exit"):
                break
            try:
                dispatch(client, parts)
            except grpc.RpcError as ex:
                print(f"server error [{ex.code().name}]: {ex.details()}")
            except (KeyError, ValueError) as ex:
                print(f"error: {ex}")


if __name__ == "__main__":
    main()
