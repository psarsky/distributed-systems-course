#!/usr/bin/env python3
import shlex
import sys
from pathlib import Path

import Ice

import ServantMgmt

DEFAULT_TARGET = "dedicated/ded-01"
PROJECT_ROOT = Path(__file__).resolve().parents[1]
ICE_CONFIG_PATH = PROJECT_ROOT / "client.config"


class ClientSession:
    def __init__(self, communicator: Ice.Communicator):
        self.communicator = communicator
        properties = communicator.getProperties()
        self.host = properties.getPropertyWithDefault("Client.Host", "server")
        self.port = int(properties.getPropertyWithDefault("Client.Port", "10000"))
        self.current_target = DEFAULT_TARGET
        self.current_base = self._new_base_proxy(self.current_target)
        self.current_typed = None

    def _new_base_proxy(self, target: str):
        proxy_str = f"{target}:tcp -h {self.host} -p {self.port}"
        return self.communicator.stringToProxy(proxy_str)

    def _ensure_typed(self):
        if self.current_typed is None:
            raise RuntimeError("No typed proxy yet. Use: checked OR unchecked")
        return self.current_typed

    def print_target(self):
        print(f"Target = {self.current_target}")

    def set_target(self, target: str):
        if target.count("/") != 1:
            raise ValueError("Target must be in form <category>/<name>")

        self.current_target = target
        self.current_base = self._new_base_proxy(target)
        self.current_typed = None
        self.print_target()
        print("Typed proxy reset. Use checked/unchecked again for this target.")

    def checked_cast(self):
        self.current_typed = ServantMgmt.CounterPrx.checkedCast(self.current_base)
        if not self.current_typed:
            raise RuntimeError("checkedCast failed (object/type not available)")
        print("checkedCast OK")

    def unchecked_cast(self):
        self.current_typed = ServantMgmt.CounterPrx.uncheckedCast(self.current_base)
        print("uncheckedCast OK")

    def get_value(self):
        value = self._ensure_typed().getValue()
        print(f"value={value}")

    def set_value(self, value: int):
        self._ensure_typed().setValue(value)
        print("setValue OK")

    def add(self, delta: int):
        value = self._ensure_typed().add(delta)
        print(f"add OK, new value={value}")


def print_help():
    print("Commands:")
    print("  help")
    print("  target <category/name>")
    print("  cast <checked|unchecked>")
    print("  get")
    print("  set <int>")
    print("  add <int>")
    print("  quit")


def cli(session: ClientSession):
    print("Simple client CLI. Type 'help' for commands.")
    session.print_target()

    while True:
        try:
            raw = input("> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return

        if not raw:
            continue

        try:
            parts = shlex.split(raw)
        except ValueError as ex:
            print(f"parse error: {ex}")
            continue

        cmd = parts[0].lower()

        try:
            if cmd == "help":
                print_help()
            elif cmd == "target":
                if len(parts) != 2:
                    print("usage: target <category/name>")
                    continue
                session.set_target(parts[1])
            elif cmd == "cast":
                if len(parts) != 2:
                    print("usage: cast <checked|unchecked>")
                    continue
                mode = parts[1].lower()
                if mode == "checked":
                    session.checked_cast()
                elif mode == "unchecked":
                    session.unchecked_cast()
                else:
                    print("usage: cast <checked|unchecked>")
            elif cmd == "get":
                session.get_value()
            elif cmd == "set":
                if len(parts) != 2:
                    print("usage: set <int>")
                    continue
                session.set_value(int(parts[1]))
            elif cmd == "add":
                if len(parts) != 2:
                    print("usage: add <int>")
                    continue
                session.add(int(parts[1]))
            elif cmd in ("quit", "exit"):
                return
            else:
                print("unknown command; type 'help'")
        except Ice.LocalException as ex:
            print(f"Ice error: {ex}")
        except Exception as ex:
            print(f"error: {ex}")


def main(argv):
    if not ICE_CONFIG_PATH.exists():
        raise RuntimeError(f"Missing Ice config file: {ICE_CONFIG_PATH}")

    ice_init_args = [argv[0], f"--Ice.Config={ICE_CONFIG_PATH}"]
    communicator = None
    try:
        communicator = Ice.initialize(ice_init_args)
        session = ClientSession(communicator)
        cli(session)
    finally:
        if communicator is not None:
            communicator.destroy()


if __name__ == "__main__":
    main(sys.argv)
