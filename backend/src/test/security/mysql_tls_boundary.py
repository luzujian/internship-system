"""Native-client TLS boundary test. Loopback fixture never connects to a database.

Requires Python cryptography and real mysql/mysqldump clients. Authentication uses
a synthetic username/password; the fixture returns an intentional access error
immediately after authentication. A passed TLS case is not a dump/restore test.
"""
import argparse
import datetime
import ipaddress
import json
import pathlib
import socket
import ssl
import struct
import subprocess
import tempfile
import threading

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID


def certificates(directory):
    now = datetime.datetime.now(datetime.timezone.utc)
    ca_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    ca_name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "Disposable TLS test CA")])
    ca = (x509.CertificateBuilder().subject_name(ca_name).issuer_name(ca_name)
          .public_key(ca_key.public_key()).serial_number(x509.random_serial_number())
          .not_valid_before(now - datetime.timedelta(days=1)).not_valid_after(now + datetime.timedelta(days=1))
          .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
          .sign(ca_key, hashes.SHA256()))
    ca_path = directory / "ca.pem"
    ca_path.write_bytes(ca.public_bytes(serialization.Encoding.PEM))
    pairs = {}
    for label, host in [("match", "127.0.0.1"), ("mismatch", "127.0.0.2")]:
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, host)])
        cert = (x509.CertificateBuilder().subject_name(name).issuer_name(ca_name)
                .public_key(key.public_key()).serial_number(x509.random_serial_number())
                .not_valid_before(now - datetime.timedelta(days=1)).not_valid_after(now + datetime.timedelta(days=1))
                .add_extension(x509.SubjectAlternativeName([x509.IPAddress(ipaddress.ip_address(host))]), critical=False)
                .sign(ca_key, hashes.SHA256()))
        cert_path, key_path = directory / f"{label}.pem", directory / f"{label}-key.pem"
        cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
        key_path.write_bytes(key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
        pairs[label] = (cert_path, key_path)
    return ca_path, pairs


def packet(sock):
    def read(count):
        data = b""
        while len(data) < count:
            chunk = sock.recv(count - len(data))
            if not chunk:
                raise EOFError()
            data += chunk
        return data
    header = read(4)
    return read(int.from_bytes(header[:3], "little"))


def send(sock, payload, sequence):
    sock.sendall(len(payload).to_bytes(3, "little") + bytes([sequence]) + payload)


def run_case(binary, ca_path, pair, trusted, tls, expected):
    state = {"authenticated": False, "cipher": None}
    listener = socket.socket()
    listener.bind(("127.0.0.1", 0))
    listener.listen(1)
    listener.settimeout(8)
    port = listener.getsockname()[1]

    def serve():
        try:
            with listener:
                connection, _ = listener.accept()
            with connection:
                connection.settimeout(5)
                flags = 0x00088201 | (0x800 if tls else 0)
                greeting = (b"\x0a8.0.42-tls-fixture\x00" + struct.pack("<I", 1) + b"12345678\x00"
                            + struct.pack("<H", flags & 65535) + b"\x21\x02\x00"
                            + struct.pack("<H", flags >> 16) + b"\x15" + b"\x00" * 10
                            + b"abcdefghijkl\x00mysql_native_password\x00")
                send(connection, greeting, 0)
                request = packet(connection)
                if len(request) == 32 and int.from_bytes(request[:4], "little") & 0x800:
                    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
                    context.load_cert_chain(*pair)
                    with context.wrap_socket(connection, server_side=True) as secure:
                        login = packet(secure)
                        state["authenticated"] = len(login) > 32
                        state["cipher"] = secure.cipher()[0]
                        send(secure, b"\xff\x15\x04#28000fixture completed; no database", 3)
                else:
                    state["authenticated"] = len(request) > 32
        except (OSError, EOFError) as error:
            state["server_error"] = type(error).__name__

    worker = threading.Thread(target=serve, daemon=True)
    worker.start()
    if binary is None:
        print(json.dumps({"port": port, "ca": str(ca_path if trusted else ca_path.parent / "other" / "ca.pem")}), flush=True)
        worker.join(timeout=12)
        if worker.is_alive():
            raise AssertionError("fixture failed to finish")
        print(json.dumps(state), flush=True)
        assert state["authenticated"] == expected, state
        return state
    command = [str(binary), "--no-defaults", "--host=127.0.0.1", f"--port={port}",
               "--user=tls_fixture", "--connect-timeout=3", "--ssl-mode=VERIFY_IDENTITY",
               "--ssl-ca=" + str(ca_path if trusted else ca_path.parent / "other" / "ca.pem")]
    if binary.stem.lower() == "mysql":
        command += ["--execute=SELECT 1"]
    else:
        # mysqldump does not support --connect-timeout.
        command.remove("--connect-timeout=3")
        command += ["fixture_database"]
    result = subprocess.run(command, capture_output=True, timeout=10)
    worker.join(timeout=9)
    if worker.is_alive():
        raise AssertionError("fixture failed to finish")
    assert state["authenticated"] == expected, (binary.name, state, result.stderr.decode(errors="replace"))
    if expected:
        assert state["cipher"], "authentication must follow TLS"
        assert b"fixture completed" in result.stderr, result.stderr
    else:
        assert result.returncode != 0
    return {"client": binary.name, "passed": True, "authentication_reached": state["authenticated"], "cipher": state["cipher"]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--client-bin", type=pathlib.Path)
    parser.add_argument("--work-dir", type=pathlib.Path, required=True)
    parser.add_argument("--serve-case", choices=["match", "mismatch", "untrusted", "no-tls"])
    args = parser.parse_args()
    results = []
    with tempfile.TemporaryDirectory(prefix="tls-fixture-", dir=args.work_dir) as folder:
        ca, pairs = certificates(pathlib.Path(folder))
        other = pathlib.Path(folder) / "other"
        other.mkdir()
        certificates(other)
        if args.serve_case:
            case = args.serve_case
            run_case(None, ca, pairs["mismatch" if case == "mismatch" else "match"],
                     case != "untrusted", case != "no-tls", case == "match")
            return
        if args.client_bin is None:
            parser.error("--client-bin is required unless --serve-case is used")
        for name in ["mysql.exe", "mysqldump.exe"]:
            for label, pair, trusted, tls, expected in [
                ("trusted-matching-host", pairs["match"], True, True, True),
                ("wrong-host", pairs["mismatch"], True, True, False),
                ("untrusted-ca", pairs["match"], False, True, False),
                ("no-tls", pairs["match"], True, False, False),
            ]:
                result = run_case(args.client_bin / name, ca, pair, trusted, tls, expected)
                result["case"] = label
                results.append(result)
    print(json.dumps({"tests": len(results), "results": results}, indent=2))


if __name__ == "__main__":
    main()
