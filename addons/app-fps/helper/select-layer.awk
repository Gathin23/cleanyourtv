# First input: live SurfaceFlinger layer list. Second input: legacy TimeStats dump.
# Only one unambiguous live layer for the resumed activity can be selected.
FILENAME == ARGV[1] {
    live[$0] = 1
    canonical = $0
    sub(/^[0-9a-f]+ /, "", canonical)
    live[canonical] = 1
    if (index(canonical, "SurfaceView[" component "]") == 1) hasSurface = 1
    next
}
/^layerName = / { layer = substr($0, 13); next }
/^totalFrames = [0-9]+$/ {
    if (!live[layer]) next
    if (index(layer, "SurfaceView[" component "]") == 1) {
        surfaces++; surfaceName = layer; surfaceFrames = $3
    } else if (index(layer, component "#") == 1) {
        mains++; mainName = layer; mainFrames = $3
    }
}
END {
    if (surfaces == 1) print surfaceName "\t" surfaceFrames
    else if (!hasSurface && mains == 1) print mainName "\t" mainFrames
}
