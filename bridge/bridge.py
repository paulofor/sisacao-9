"""Read-only market bridge. No order execution API is provided."""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import importlib
import json
import logging
import math
import os
import random
import re
import time
from urllib import request
from urllib.parse import urlparse

LOG = logging.getLogger("sisacao.bridge")


class SimulatedMarket:
    def __init__(self):
        self.random = random.Random(9)

    def tick(self, symbol):
        bid = round(1.10 + self.random.uniform(-0.002, 0.002), 5)
        return {"symbol": symbol, "bid": bid, "ask": round(bid + 0.00015, 5),
                "observedAt": datetime.now(timezone.utc).isoformat(), "source": "SIMULATED"}

    def close(self):
        pass


class MetaTraderMarket:
    def __init__(self, module=None, terminal_path=None):
        self.mt5 = module if module is not None else importlib.import_module("MetaTrader5")
        self.terminal_path = terminal_path
        self.connected = False

    def tick(self, symbol):
        try:
            if not self.connected:
                args = [self.terminal_path] if self.terminal_path else []
                if not self.mt5.initialize(*args):
                    raise RuntimeError("MT5 initialize falhou")
                self.connected = True
            info = self.mt5.terminal_info()
            if info is None or not info.connected:
                raise RuntimeError("Terminal MT5 desconectado")
            if not self.mt5.symbol_select(symbol, True):
                raise RuntimeError("Símbolo não disponível no terminal")
            tick = self.mt5.symbol_info_tick(symbol)
            if tick is None or not all(math.isfinite(v) for v in (tick.bid, tick.ask)) \
                    or tick.bid <= 0 or tick.ask < tick.bid or tick.time_msc <= 0:
                raise RuntimeError("Tick MT5 ausente ou inválido")
            return {"symbol": symbol, "bid": tick.bid, "ask": tick.ask,
                    "observedAt": datetime.fromtimestamp(tick.time_msc / 1000, timezone.utc).isoformat(),
                    "source": "MT5"}
        except Exception:
            self.close()
            raise

    def close(self):
        self.mt5.shutdown()
        self.connected = False


def publish(url, token, tick):
    message = request.Request(url.rstrip("/") + "/api/market/ticks",
                              data=json.dumps(tick, allow_nan=False).encode("utf-8"),
                              headers={"Content-Type": "application/json", "X-Bridge-Token": token}, method="POST")
    with request.urlopen(message, timeout=5) as response:
        if response.status != 202:
            raise RuntimeError("Resposta inesperada do backend")


def cycle(market, symbols, url, token, publisher=publish):
    errors = 0
    for symbol in symbols:
        try:
            tick = market.tick(symbol)
            publisher(url, token, tick)
            LOG.info("event=tick_published symbol=%s source=%s", symbol, tick["source"])
        except Exception as error:
            errors += 1
            # Exception bodies can contain URLs/headers; log only the class, never credentials.
            LOG.warning("event=tick_failed symbol=%s errorType=%s", symbol, type(error).__name__)
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--once", action="store_true", help="Publish one cycle and exit")
    args = parser.parse_args()
    mode = os.getenv("BRIDGE_MODE", "simulated")
    token = os.getenv("BRIDGE_TOKEN", "")
    url = os.getenv("BACKEND_URL", "http://localhost:8080")
    symbols = [value.strip() for value in os.getenv("SYMBOLS", "EURUSD,GBPUSD").split(",")]
    interval = float(os.getenv("POLL_SECONDS", "2"))
    parsed = urlparse(url)
    if not token or parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password:
        parser.error("Configure BRIDGE_TOKEN e BACKEND_URL http(s) sem credenciais na URL")
    if not math.isfinite(interval) or interval < 0.1 or not 1 <= len(symbols) <= 50 \
            or any(not re.fullmatch(r"[A-Za-z0-9_.#-]{1,32}", symbol) for symbol in symbols):
        parser.error("SYMBOLS ou POLL_SECONDS inválidos")
    if mode not in ("simulated", "mt5"):
        parser.error("BRIDGE_MODE deve ser simulated ou mt5; não existe fallback automático")
    if mode == "mt5" and os.name != "nt":
        parser.error("O modo mt5 requer o terminal e Python no Windows")
    market = SimulatedMarket() if mode == "simulated" else MetaTraderMarket(terminal_path=os.getenv("MT5_TERMINAL_PATH"))
    failures = 0
    try:
        while True:
            errors = cycle(market, symbols, url, token)
            if args.once:
                return 1 if errors else 0
            failures = min(failures + 1, 5) if errors else 0
            time.sleep(min(interval * 2 ** failures, 30))
    except KeyboardInterrupt:
        return 0
    finally:
        market.close()


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    raise SystemExit(main())
