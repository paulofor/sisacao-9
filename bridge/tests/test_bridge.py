import importlib.util
import pathlib
from types import SimpleNamespace
import unittest
from unittest.mock import Mock

spec = importlib.util.spec_from_file_location("bridge", pathlib.Path(__file__).parents[1] / "bridge.py")
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


class BridgeTests(unittest.TestCase):
    def terminal(self):
        terminal = Mock()
        terminal.initialize.return_value = True
        terminal.terminal_info.return_value = SimpleNamespace(connected=True)
        terminal.symbol_select.return_value = True
        terminal.symbol_info_tick.return_value = SimpleNamespace(bid=1.1, ask=1.2, time_msc=1700000000123)
        return terminal

    def test_simulated_source_is_explicit(self):
        tick = bridge.SimulatedMarket().tick("EURUSD")
        self.assertEqual(tick["source"], "SIMULATED")
        self.assertGreaterEqual(tick["ask"], tick["bid"])

    def test_mt5_initializes_selects_and_preserves_market_timestamp(self):
        terminal = self.terminal()
        market = bridge.MetaTraderMarket(terminal, "terminal.exe")
        tick = market.tick("EURUSD")
        self.assertEqual(tick["source"], "MT5")
        self.assertEqual(tick["observedAt"], "2023-11-14T22:13:20.123000+00:00")
        terminal.initialize.assert_called_once_with("terminal.exe")
        terminal.symbol_select.assert_called_once_with("EURUSD", True)
        market.close()
        terminal.shutdown.assert_called_once()

    def test_terminal_failures_close_and_reconnect_without_simulation(self):
        for failure in ("initialize", "terminal_info", "symbol_select", "symbol_info_tick"):
            with self.subTest(failure=failure):
                terminal = self.terminal()
                getattr(terminal, failure).return_value = None
                market = bridge.MetaTraderMarket(terminal)
                with self.assertRaises(RuntimeError):
                    market.tick("EURUSD")
                self.assertFalse(market.connected)
                terminal.shutdown.assert_called_once()
                replacement = self.terminal()
                getattr(terminal, failure).return_value = getattr(replacement, failure).return_value
                self.assertEqual(market.tick("EURUSD")["source"], "MT5")
                self.assertEqual(terminal.initialize.call_count, 2)

    def test_missing_or_invalid_tick_is_never_published(self):
        terminal = self.terminal()
        for bad in (None, SimpleNamespace(bid=float("nan"), ask=1, time_msc=1), SimpleNamespace(bid=2, ask=1, time_msc=1)):
            terminal.symbol_info_tick.return_value = bad
            publisher = Mock()
            self.assertEqual(bridge.cycle(bridge.MetaTraderMarket(terminal), ["EURUSD"], "http://local", "secret", publisher), 1)
            publisher.assert_not_called()

    def test_http_failure_does_not_stop_next_symbol_or_cycle(self):
        publisher = Mock(side_effect=[OSError("unavailable"), None, None])
        market = bridge.SimulatedMarket()
        self.assertEqual(bridge.cycle(market, ["EURUSD", "GBPUSD"], "http://local", "secret", publisher), 1)
        self.assertEqual(bridge.cycle(market, ["EURUSD"], "http://local", "secret", publisher), 0)
        self.assertEqual(publisher.call_count, 3)


if __name__ == "__main__":
    unittest.main()
