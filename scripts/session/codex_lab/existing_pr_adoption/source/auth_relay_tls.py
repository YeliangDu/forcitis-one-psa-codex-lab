"""Only the fixed official OAuth CONNECT target. Never logs request or TLS data."""
from contextlib import contextmanager
import ipaddress
import selectors
import socket
import socketserver
import threading

HOST='auth.openai.com'
PORT=39423
TARGET=b'auth.openai.com:443'


def upstream(emit):
    try:addresses=socket.getaddrinfo(HOST,443,type=socket.SOCK_STREAM)
    except OSError as err:
        emit('dns',error=type(err).__name__,errno=err.errno)
        raise
    emit('dns',result='ok')
    # Alternate families without retrying HTTP. A failed v6 path should not
    # consume the whole connection budget before the first v4 attempt.
    groups={f:[x for x in addresses if x[0]==f] for f in (socket.AF_INET,socket.AF_INET6)}
    choices=[item for n in range(2) for f in (socket.AF_INET,socket.AF_INET6) for item in groups[f][n:n+1]]
    for family,kind,proto,_,address in choices:
        label='IPv4' if family==socket.AF_INET else 'IPv6'
        if not ipaddress.ip_address(address[0]).is_global:
            emit('upstream-connect',family=label,result='non-global-address-rejected')
            continue
        peer=socket.socket(family,kind,proto)
        peer.settimeout(2.5)
        try:peer.connect(address)
        except OSError as err:
            peer.close()
            emit('upstream-connect',family=label,error=type(err).__name__,errno=err.errno)
            continue
        peer.settimeout(15)
        emit('upstream-connect',family=label,result='ok')
        return peer
    raise OSError('authorized upstream unavailable')


class Handler(socketserver.BaseRequestHandler):
    def handle(self):
        client=self.request
        client.settimeout(8)
        emit=self.server.emit
        stage='connect-header'
        try:
            raw=bytearray()
            while b'\r\n\r\n' not in raw and len(raw)<8192:
                part=client.recv(1024)
                if not part:
                    emit(stage,result='client-closed')
                    return
                raw.extend(part)
            header,separator,tail=bytes(raw).partition(b'\r\n\r\n')
            first=header.split(b'\r\n',1)[0].split()
            if not separator or len(header)>=8192 or len(first)!=3 or first[0]!=b'CONNECT' or first[1]!=TARGET or first[2] not in (b'HTTP/1.0',b'HTTP/1.1'):
                client.sendall(b'HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n')
                emit(stage,result='denied')
                return
            stage='upstream'
            try:peer=upstream(emit)
            except OSError:
                client.sendall(b'HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n')
                emit('connect-response',result='502')
                return
            with peer:
                client.settimeout(15)
                client.sendall(b'HTTP/1.1 200 Connection Established\r\n\r\n')
                emit('connect-response',result='200')
                stage='opaque-tunnel'
                if tail:peer.sendall(tail)
                with selectors.DefaultSelector() as sel:
                    sel.register(client,selectors.EVENT_READ,peer)
                    sel.register(peer,selectors.EVENT_READ,client)
                    while sel.get_map():
                        ready=sel.select(90)
                        if not ready:
                            emit(stage,result='idle-timeout')
                            return
                        for key,_ in ready:
                            side='upstream' if key.fileobj is peer else 'client'
                            try:data=key.fileobj.recv(65536)
                            except OSError as err:
                                emit('tunnel-read',side=side,error=type(err).__name__,errno=err.errno)
                                return
                            if data:
                                side='upstream' if key.data is peer else 'client'
                                try:key.data.sendall(data)
                                except OSError as err:
                                    emit('tunnel-write',side=side,error=type(err).__name__,errno=err.errno)
                                    return
                            else:
                                sel.unregister(key.fileobj)
                                try:key.data.shutdown(socket.SHUT_WR)
                                except OSError:pass
        except (OSError,ValueError) as err:
            emit(stage,error=type(err).__name__,errno=getattr(err,'errno',None))


class Relay(socketserver.ThreadingTCPServer):
    allow_reuse_address=False
    daemon_threads=True

    def handle_error(self,request,address):
        import sys
        err=sys.exc_info()[1]
        self.emit('handler',error=type(err).__name__,errno=getattr(err,'errno',None))


@contextmanager
def relay(emit):
    with Relay(('127.0.0.1',PORT),Handler) as server:
        server.emit=emit
        thread=threading.Thread(target=server.serve_forever,daemon=True)
        thread.start()
        try:yield
        finally:
            server.shutdown()
            thread.join(timeout=5)
