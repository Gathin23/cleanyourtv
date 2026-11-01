"""Generate the bundled decorative land silhouette from Natural Earth v5.1.2 110m land.

Usage: python3 tools/build_map.py /path/to/ne_110m_land.geojson
Input: https://github.com/nvkelso/natural-earth-vector/blob/v5.1.2/geojson/ne_110m_land.geojson
Public-domain coastline; Equal Earth spherical projection, Savric/Patterson/Jenny (2018).
Equations: https://proj.org/en/stable/operations/projections/eqearth.html
All projection/simplification work happens here, never on the TV or per animation frame.
"""
from pathlib import Path
import hashlib
import json
import math
import sys

SOURCE_SHA256 = '9e0729ee253ca7d7a5c4ae9395fb1902264c5377c52e224d13dd85010e2835d9'

def project(lon, lat):
    theta = math.asin(math.sqrt(3) / 2 * math.sin(math.radians(lat)))
    squared = theta * theta
    sixth = squared ** 3
    x = math.radians(lon) * math.cos(theta) / (math.sqrt(3) / 2 * (
        1.340264 - 3 * 0.081106 * squared + sixth * (7 * 0.000893 + 9 * 0.003796 * squared)))
    y = theta * (1.340264 - 0.081106 * squared + sixth * (0.000893 + 0.003796 * squared))
    return x, y

X_MAX = project(180, 0)[0]
Y_MAX = project(0, 90)[1]
HEIGHT = 360 * Y_MAX / X_MAX

def viewport(lon, lat):
    x, y = project(lon, lat)
    return 180 + 180 * x / X_MAX, 180 * (Y_MAX - y) / X_MAX

def simplify(points, tolerance=0.10):
    if len(points) < 3:
        return points
    a, b = points[0], points[-1]
    dx, dy = b[0] - a[0], b[1] - a[1]
    den = dx * dx + dy * dy
    distances = []
    for x, y in points[1:-1]:
        along = max(0, min(1, ((x-a[0])*dx + (y-a[1])*dy)/den)) if den else 0
        distances.append((x-a[0]-along*dx)**2 + (y-a[1]-along*dy)**2)
    farthest = max(distances)
    if farthest <= tolerance*tolerance:
        return [a, b]
    index = distances.index(farthest) + 1
    return simplify(points[:index+1], tolerance)[:-1] + simplify(points[index:], tolerance)

def encode(ring):
    pairs = [viewport(x, y) for x, y in ring]
    assert all(math.isfinite(x) and math.isfinite(y) and -0.001 <= x <= 360.001
               and -0.001 <= y <= HEIGHT + 0.001 for x, y in pairs)
    return ' '.join(f'{x:.1f},{y:.1f}' for x, y in pairs)

def main(source):
    body = Path(source).read_bytes()
    if hashlib.sha256(body).hexdigest() != SOURCE_SHA256:
        raise ValueError('Expected pinned Natural Earth v5.1.2 110m land data')
    # Known PROJ spherical example and symmetry catch projection sign/scale mistakes.
    assert abs(project(122, 47)[0] - 1.55) < 0.01
    assert abs(project(122, 47)[1] - 0.89) < 0.01
    assert viewport(0, 0) == (180, HEIGHT/2)
    assert abs(viewport(180, 0)[0] - 360) < 1e-8
    assert abs(viewport(-180, 0)[0]) < 1e-8
    outline = [(-180, lat) for lat in range(-90, 91, 2)] + [(180, lat) for lat in range(90, -91, -2)]
    lines = ['o ' + encode(outline)]
    points, rings = 0, 0
    for feature in json.loads(body)['features']:
        shape = feature['geometry']
        polygons = shape['coordinates'] if shape['type'] == 'MultiPolygon' else [shape['coordinates']]
        for polygon in polygons:
            for ring in polygon:
                area = abs(sum(a[0]*b[1] - b[0]*a[1] for a, b in zip(ring, ring[1:])))/2
                if area < 0.15:
                    continue
                ring = simplify(ring)
                if len(ring) < 4:
                    continue
                lines.append(encode(ring))
                points += len(ring)
                rings += 1
    output = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw/world_map.txt'
    output.write_text('\n'.join(lines) + '\n')
    print(json.dumps({'projection':'Equal Earth','viewport':[360,HEIGHT], 'coastline_points':points,
                      'rings':rings,'asset_bytes':output.stat().st_size,'source_sha256':SOURCE_SHA256}))

if __name__ == '__main__':
    main(sys.argv[1])
