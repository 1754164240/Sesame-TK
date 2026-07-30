import socket
import unittest
from unittest.mock import MagicMock, patch

import main


class GetLanIpTest(unittest.TestCase):
    def test_prefers_default_route_address_over_adapter_order(self):
        route_socket = MagicMock()
        route_socket.__enter__.return_value = route_socket
        route_socket.getsockname.return_value = ("192.168.1.23", 49152)

        with (
            patch("socket.socket", return_value=route_socket),
            patch(
                "socket.getaddrinfo",
                return_value=[
                    (
                        socket.AF_INET,
                        socket.SOCK_STREAM,
                        socket.IPPROTO_TCP,
                        "",
                        ("192.168.99.1", 0),
                    )
                ],
            ) as getaddrinfo,
        ):
            self.assertEqual("192.168.1.23", main.get_lan_ip())

        route_socket.connect.assert_called_once_with(("8.8.8.8", 80))
        getaddrinfo.assert_not_called()

    def test_falls_back_to_private_adapter_when_route_lookup_fails(self):
        route_socket = MagicMock()
        route_socket.__enter__.return_value = route_socket
        route_socket.connect.side_effect = OSError("route unavailable")

        with (
            patch("socket.socket", return_value=route_socket),
            patch(
                "socket.getaddrinfo",
                return_value=[
                    (
                        socket.AF_INET,
                        socket.SOCK_STREAM,
                        socket.IPPROTO_TCP,
                        "",
                        ("192.168.1.24", 0),
                    )
                ],
            ),
        ):
            self.assertEqual("192.168.1.24", main.get_lan_ip())


if __name__ == "__main__":
    unittest.main()
