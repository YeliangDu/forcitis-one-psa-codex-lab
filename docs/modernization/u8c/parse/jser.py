"""Minimal Java serialization stream parser (no class files needed)."""
import struct
class CD:
    def __init__(s,name,flags,fields,sup=None): s.name=name;s.flags=flags;s.fields=fields;s.sup=sup
    def chain(s):
        c=[];x=s
        while x: c.append(x); x=x.sup
        return c[::-1]
class Obj:
    def __init__(s,cd): s.cd=cd; s.f={}; s.ann=[]  # ann: list per class of content
    def __repr__(s): return f"<{s.cd.name} {s.f} ann={len(s.ann)}>"
class Arr:
    def __init__(s,cd,items): s.cd=cd; s.items=items
class Str(str): pass
class Enum:
    def __init__(s,cd,n): s.cd=cd;s.n=n
class Parser:
    def __init__(s,d): s.d=d;s.p=0;s.h=[]
    def r(s,n):
        b=s.d[s.p:s.p+n]; s.p+=n; return b
    def u1(s): return s.r(1)[0]
    def u2(s): return struct.unpack('>H',s.r(2))[0]
    def i4(s): return struct.unpack('>i',s.r(4))[0]
    def utf(s): n=s.u2(); return s.r(n).decode('utf-8','replace')
    def parse(s):
        assert s.r(4)==b'\xac\xed\x00\x05'
        out=[]
        while s.p<len(s.d): out.append(s.content())
        return out
    def content(s):
        t=s.u1()
        if t==0x73: return s.newobj()
        if t==0x74: x=Str(s.utf()); s.h.append(x); return x
        if t==0x7c: n=struct.unpack('>q',s.r(8))[0]; x=Str(s.r(n).decode()); s.h.append(x); return x
        if t==0x71: return s.h[s.i4()-0x7e0000]
        if t==0x70: return None
        if t==0x75: return s.newarr()
        if t==0x72: return s.classdesc(t)
        if t==0x7e: 
            cd=s.classdesc(); n=s.content(); e=Enum(cd,n); s.h.append(e); return e
        if t==0x77: n=s.u1(); return ('BLK',s.r(n))
        if t==0x7a: n=s.i4(); return ('BLK',s.r(n))
        if t==0x78: return ('END',)
        if t==0x76: cd=s.classdesc(); s.h.append(('class',cd)); return ('class',cd)
        raise Exception(f"tc {t:#x} at {s.p-1}")
    def classdesc(s,t=None):
        if t is None: t=s.u1()
        if t==0x70: return None
        if t==0x71: return s.h[s.i4()-0x7e0000]
        assert t==0x72,(t,s.p)
        name=s.utf(); s.r(8); fl=s.u1(); n=s.u2(); fields=[]
        cd=CD(name,fl,fields); s.h.append(cd)
        for _ in range(n):
            tc=chr(s.u1()); fn=s.utf(); cn=None
            if tc in 'L[': cn=s.content()
            fields.append((tc,fn,cn))
        # class annotation
        while True:
            x=s.content()
            if isinstance(x,tuple) and x[0]=='END': break
        cd.sup=s.classdesc()
        return cd
    def newarr(s):
        cd=s.classdesc(); n=s.i4(); a=Arr(cd,[]); s.h.append(a)
        tc=cd.name[1]
        for _ in range(n):
            a.items.append(s.val(tc))
        return a
    def val(s,tc):
        if tc=='B': return struct.unpack('>b',s.r(1))[0]
        if tc=='C': return s.u2()
        if tc=='D': return struct.unpack('>d',s.r(8))[0]
        if tc=='F': return struct.unpack('>f',s.r(4))[0]
        if tc=='I': return s.i4()
        if tc=='J': return struct.unpack('>q',s.r(8))[0]
        if tc=='S': return struct.unpack('>h',s.r(2))[0]
        if tc=='Z': return bool(s.u1())
        return s.content()
    def newobj(s):
        cd=s.classdesc(); o=Obj(cd); s.h.append(o)
        for c in cd.chain():
            if c.flags&0x02:  # SC_SERIALIZABLE
                for tc,fn,_ in c.fields: o.f[c.name+'.'+fn]=s.val(tc)
                if c.flags&0x01:  # write method
                    ann=[]
                    while True:
                        x=s.content()
                        if isinstance(x,tuple) and x[0]=='END': break
                        ann.append(x)
                    o.ann.append((c.name,ann))
            elif c.flags&0x04: # externalizable
                raise Exception('ext '+c.name)
        return o
