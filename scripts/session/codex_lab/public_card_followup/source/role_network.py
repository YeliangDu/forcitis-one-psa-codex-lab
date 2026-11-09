"""Owned, fixed-host TLS CONNECT transport. No TLS termination or payload logs."""
from contextlib import contextmanager
import ipaddress
import selectors
import socket
import socketserver
import threading

PORT=39423
AUTH=frozenset({'auth.openai.com'})
MODEL=frozenset({'auth.openai.com','chatgpt.com'})
PROXY=f'http://127.0.0.1:{PORT}'

def upstream(host):
    for family,kind,proto,_,address in socket.getaddrinfo(host,443,type=socket.SOCK_STREAM):
        if not ipaddress.ip_address(address[0]).is_global:continue
        peer=socket.socket(family,kind,proto)
        peer.settimeout(5)
        try:
            peer.connect(address)
            return peer
        except OSError:
            peer.close()
    raise OSError('approved TLS host unavailable')

class Handler(socketserver.BaseRequestHandler):
    def handle(self):
        client=self.request
        client.settimeout(5)
        try:
            raw=bytearray()
            while b'\r\n\r\n' not in raw and len(raw)<8192:
                part=client.recv(1024)
                if not part:return
                raw.extend(part)
            header,separator,tail=bytes(raw).partition(b'\r\n\r\n')
            first=header.split(b'\r\n',1)[0].split()
            hosts={host.encode()+b':443':host for host in self.server.allowed}
            if not separator or len(first)!=3 or first[0]!=b'CONNECT' or first[1] not in hosts or first[2] not in (b'HTTP/1.0',b'HTTP/1.1'):
                client.sendall(b'HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n')
                return
            with upstream(hosts[first[1]]) as peer:
                client.sendall(b'HTTP/1.1 200 Connection Established\r\n\r\n')
                if tail:peer.sendall(tail)
                with selectors.DefaultSelector() as sel:
                    sel.register(client,selectors.EVENT_READ,peer)
                    sel.register(peer,selectors.EVENT_READ,client)
                    while sel.get_map():
                        ready=sel.select(40)
                        if not ready:return
                        for key,_ in ready:
                            data=key.fileobj.recv(65536)
                            if data:key.data.sendall(data)
                            else:
                                sel.unregister(key.fileobj)
                                try:key.data.shutdown(socket.SHUT_WR)
                                except OSError:pass
        except (OSError,ValueError):return

class Proxy(socketserver.ThreadingTCPServer):
    daemon_threads=True
    allow_reuse_address=False
    def handle_error(self,request,client_address):pass

@contextmanager
def relay(allowed):
    if allowed not in (AUTH,MODEL):raise ValueError('unapproved network phase')
    with Proxy(('127.0.0.1',PORT),Handler) as server:
        server.allowed=allowed
        t=threading.Thread(target=server.serve_forever,daemon=True)
        t.start()
        try:yield
        finally:
            server.shutdown()
            t.join(timeout=5)
