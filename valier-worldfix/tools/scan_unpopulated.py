"""List overworld chunks that were generated but never populated (Level.TerrainPopulated == 0).

Reads the live region files read-only. Writes unpopulated.txt (one 'cx cz' per line) next to this script and prints
where they are. Usage: python -I scan_unpopulated.py <world folder>
"""
import os, struct, sys, zlib, gzip, collections, math

world = sys.argv[1]
region_dir = os.path.join(world, 'region')
needle = b'\x01\x00\x10TerrainPopulated'  # TAG_Byte named TerrainPopulated; the byte value follows
out, total, rings = [], 0, collections.Counter()
for name in sorted(os.listdir(region_dir)):
    if not name.endswith('.mca'): continue
    _, rx, rz, _ = name.split('.')
    rx, rz = int(rx), int(rz)
    with open(os.path.join(region_dir, name), 'rb') as f:
        data = f.read()
    if len(data) < 8192: continue
    for i in range(1024):
        loc = struct.unpack('>I', data[i * 4:i * 4 + 4])[0]
        if not loc: continue
        off = (loc >> 8) * 4096
        try:
            length, ctype = struct.unpack('>IB', data[off:off + 5])
            raw = data[off + 5:off + 4 + length]
            nbt = zlib.decompress(raw) if ctype == 2 else gzip.decompress(raw)
        except Exception:
            continue
        total += 1
        k = nbt.find(needle)
        if k < 0 or nbt[k + len(needle)] != 0: continue
        cx, cz = rx * 32 + (i & 31), rz * 32 + (i >> 5)
        out.append((cx, cz))
        rings[int(math.hypot(cx, cz) // 50) * 50] += 1
out.sort(key=lambda c: c[0] * c[0] + c[1] * c[1])
with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'unpopulated.txt'), 'w') as f:
    for cx, cz in out: f.write(f'{cx} {cz}\n')
print(f'{len(out)} of {total} chunks unpopulated')
for r in sorted(rings): print(f'  {r:4d}-{r+49:4d} chunks from 0,0: {rings[r]}')
