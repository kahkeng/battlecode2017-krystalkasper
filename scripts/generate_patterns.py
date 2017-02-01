import math
SENSE_RADIUS = 7.0
TREE_RADIUS = 1.0
TRI_DX = 4.2
TRI_DY = TRI_DX / 2 * math.sqrt(3)
RANGE = 3

def triangle_clusters(mx, my):
    xi = int(2 * mx / TRI_DX)
    yi = int(2 * my / TRI_DY)
    coords = []
    cands = []
    for i in range(-RANGE, RANGE + 1):
        for j in range(-RANGE, RANGE + 1):
            cands.append((i, j))

            x = xi + i
            y = yi + j
            ok = False
            if x % 2 == 0 and y % 2 == 0:
                ok = True
            elif y % 4 == 1 and x % 3 < 2:
                ok = True
            elif y % 4 == 0 and x % 6 == 1:
                ok = True
            elif y % 4 == 2 and x % 6 == 3:
                ok = True

            if not ok:
                continue

            xf = x * TRI_DX / 2 + (y % 4) * TRI_DX / 4
            yf = y * TRI_DY / 2
            #dx = mx - xf
            #dy = my - yf
            #dist = math.sqrt(dx * dx + dy * dy)
            #if dist > SENSE_RADIUS - TREE_RADIUS:
            #    continue
            coords.append((i, j))

    #print len(cands)
    #print len(coords)
    #print xi, yi
    #print coords
    return xi, yi, coords

def search_triangle_clusters():
    mx = 400.5 + TRI_DX * 6
    my = 134.1 + TRI_DY * 4
    d = {}
    c = set()
    for i in range(0, 100):
        for j in range(0, 100):
            xi, yi, coords = triangle_clusters(mx + i, my + j)
            c.add(str(coords))
            key = (xi % 6, yi % 4)
            if key in d:
                assert coords == d[key], (coords, d[key])
            else:
                d[key] = coords
    print len(d)
    for k, v in d.items():
        print k, len(v)
    print len(c)
    #for k in c: print k

    print "public static final int[][][][] DATA = {"
    for x in range(0, 6):
        print "{"
        for y in range(0, 4):
            print "{"
            v = d[(x, y)]
            for point in v:
                print "{ %s, %s }, " % point,
            print "},"
        print "},"
    print "};"



def main():
    search_triangle_clusters()

if __name__ == "__main__":
    main()
