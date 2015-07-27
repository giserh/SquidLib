package squidpony.squidai;

import squidpony.squidgrid.Radius;
import squidpony.squidmath.Elias;

import java.awt.Point;
import java.util.*;

/**
 * Line Area of Effect that affects an slightly expanded (Elias) line from a given start Point to a given end Point,
 * plus an optional radius of cells around the path of the line, while respecting obstacles in its path and possibly
 * stopping if obstructed. You can specify the RadiusType to Radius.DIAMOND for Manhattan distance, RADIUS.SQUARE for
 * Chebyshev, or RADIUS.CIRCLE for Euclidean.
 *
 * You may want the BeamAOE class instead of this. LineAOE travels point-to-point and does not restrict length, while
 * BeamAOE travels a specific length (and may have a radius, like LineAOE) but then stops only after the travel down the
 * length and radius has reached its end. This difference is relevant if a game has effects that have a definite
 * area measured in a rectangle or elongated pillbox shape, such as a "20-foot-wide bolt of lightning, 100 feet long."
 * BeamAOE is more suitable for that effect, while LineAOE may be more suitable for things like focused lasers that
 * pass through small (likely fleshy) obstacles but stop after hitting the aimed-at target.
 *
 * This will produce doubles for its getArea() method which are equal to 1.0.
 *
 * This class uses squidpony.squidmath.Elias and squidpony.squidai.DijkstraMap to create its area of effect.
 * Created by Tommy Ettinger on 7/14/2015.
 */
public class LineAOE implements AOE {
    private Point start, end;
    private int radius;
    private char[][] dungeon;
    private DijkstraMap dijkstra;
    private Radius rt;

    public LineAOE(Point start, Point end)
    {
        this.dijkstra = new DijkstraMap();
        this.dijkstra.measurement = DijkstraMap.Measurement.CHEBYSHEV;
        rt = Radius.SQUARE;
        this.start = start;
        this.end = end;
        this.radius = 0;
    }
    public LineAOE(Point start, Point end, int radius)
    {
        this.dijkstra = new DijkstraMap();
        this.dijkstra.measurement = DijkstraMap.Measurement.CHEBYSHEV;
        rt = Radius.SQUARE;
        this.start = start;
        this.end = end;
        this.radius = radius;
    }
    public LineAOE(Point start, Point end, int radius, Radius radiusType)
    {
        this.dijkstra = new DijkstraMap();
        this.rt = radiusType;
        switch (radiusType)
        {
            case OCTAHEDRON:
            case DIAMOND: this.dijkstra.measurement = DijkstraMap.Measurement.MANHATTAN;
                break;
            case CUBE:
            case SQUARE: this.dijkstra.measurement = DijkstraMap.Measurement.CHEBYSHEV;
                break;
            default: this.dijkstra.measurement = DijkstraMap.Measurement.EUCLIDEAN;
                break;
        }
        this.start = start;
        this.end = end;
        this.radius = radius;
    }
    private double[][] initDijkstra()
    {
        List<Point> lit = Elias.line(start, end);

        dijkstra.initialize(dungeon);
        for(Point p : lit)
        {
            dijkstra.setGoal(p);
        }
        if(radius == 0)
            return dijkstra.gradientMap;
        return dijkstra.partialScan(radius, null);
    }

    public Point getStart() {
        return start;
    }

    public void setStart(Point start) {
        this.start = start;
        dijkstra.resetMap();
        dijkstra.clearGoals();
    }

    public Point getEnd() {
        return end;
    }

    public void setEnd(Point end) {
        this.end = end;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public Radius getRadiusType()
    {
        return rt;
    }
    public void setRadiusType(Radius radiusType)
    {
        this.rt = radiusType;
        switch (radiusType)
        {
            case OCTAHEDRON:
            case DIAMOND: this.dijkstra.measurement = DijkstraMap.Measurement.MANHATTAN;
                break;
            case CUBE:
            case SQUARE: this.dijkstra.measurement = DijkstraMap.Measurement.CHEBYSHEV;
                break;
            default: this.dijkstra.measurement = DijkstraMap.Measurement.EUCLIDEAN;
                break;
        }
    }

    @Override
    public void shift(Point aim) {
        setEnd(aim);
    }

    @Override
    public boolean mayContainTarget(Set<Point> targets) {
        for (Point p : targets)
        {
            if(rt.radius(start.x, start.y, p.x, p.y) + rt.radius(end.x, end.y, p.x, p.y) -
                    rt.radius(start.x, start.y, end.x, end.y) <= 3.0 + radius)
                return true;
        }
        return false;
    }

    @Override
    public LinkedHashMap<Point, ArrayList<Point>> idealLocations(Set<Point> targets, Set<Point> requiredExclusions) {
        if(targets == null)
            return new LinkedHashMap<Point, ArrayList<Point>>();
        if(requiredExclusions == null) requiredExclusions = new LinkedHashSet<Point>();

        int totalTargets = targets.size();
        LinkedHashMap<Point, ArrayList<Point>> bestPoints = new LinkedHashMap<Point, ArrayList<Point>>(totalTargets * 8);

        if(totalTargets == 0)
            return bestPoints;

        Point[] ts = targets.toArray(new Point[targets.size()]);
        Point[] exs = requiredExclusions.toArray(new Point[requiredExclusions.size()]);
        Point t = exs[0];

        double[][][] compositeMap = new double[ts.length][dungeon.length][dungeon[0].length];

        char[][] dungeonCopy = new char[dungeon.length][dungeon[0].length];
        for (int i = 0; i < dungeon.length; i++) {
            System.arraycopy(dungeon[i], 0, dungeonCopy[i], 0, dungeon[i].length);
        }
        DijkstraMap dt = new DijkstraMap(dungeon, dijkstra.measurement);
        for (int i = 0; i < exs.length; ++i, t = exs[i]) {
            dt.resetMap();
            dt.clearGoals();
            List<Point> lit = Elias.line(start, t);

            for(Point p : lit)
            {
                dt.setGoal(p);
            }
            if(radius > 0)
                dt.partialScan(radius, null);

            for (int x = 0; x < dungeon.length; x++) {
                for (int y = 0; y < dungeon[x].length; y++) {
                    dungeonCopy[x][y] = (dt.gradientMap[x][y] < DijkstraMap.FLOOR) ? '!' : dungeonCopy[x][y];
                }
            }
        }

        t = ts[0];
        DijkstraMap dm = new DijkstraMap(dungeon, dijkstra.measurement);

        for (int i = 0; i < ts.length; ++i, t = ts[i]) {
            dt.resetMap();
            dt.clearGoals();
            List<Point> lit = Elias.line(start, t);

            for(Point p : lit)
            {
                dt.setGoal(p);
            }
            if(radius > 0)
                dt.partialScan(radius, null);

            for (int x = 0; x < dungeon.length; x++) {
                for (int y = 0; y < dungeon[x].length; y++) {
                    compositeMap[i][x][y] = (dt.gradientMap[x][y] < DijkstraMap.FLOOR) ? dm.physicalMap[x][y] : DijkstraMap.WALL;
                }
            }
            dm.initialize(compositeMap[i]);
            dm.setGoal(t);
            dm.scan(null);
            for (int x = 0; x < dungeon.length; x++) {
                for (int y = 0; y < dungeon[x].length; y++) {
                    compositeMap[i][x][y] = (dm.gradientMap[x][y] < DijkstraMap.FLOOR  && dungeonCopy[x][y] != '!') ? dm.gradientMap[x][y] : 99999.0;
                }
            }
            dm.resetMap();
            dm.clearGoals();
        }
        double bestQuality = 99999 * ts.length;
        double[][] qualityMap = new double[dungeon.length][dungeon[0].length];
        for (int x = 0; x < qualityMap.length; x++) {
            for (int y = 0; y < qualityMap[x].length; y++) {
                qualityMap[x][y] = 0.0;
                long bits = 0;
                for (int i = 0; i < ts.length; ++i) {
                    qualityMap[x][y] += compositeMap[i][x][y];
                    if(compositeMap[i][x][y] < 99999.0 && i < 63)
                        bits |= 1 << i;
                }
                if(qualityMap[x][y] < bestQuality)
                {
                    bestQuality = qualityMap[x][y];
                    bestPoints.clear();
                    ArrayList<Point> ap = new ArrayList<Point>();

                    for (int i = 0; i < ts.length && i < 63; ++i) {
                        if((bits & (1 << i)) != 0)
                            ap.add(ts[i]);
                    }
                    bestPoints.put(new Point(x, y), ap);

                }
                else if(qualityMap[x][y] == bestQuality)
                {
                    ArrayList<Point> ap = new ArrayList<Point>();

                    for (int i = 0; i < ts.length && i < 63; ++i) {
                        if((bits & (1 << i)) != 0)
                            ap.add(ts[i]);
                    }
                    bestPoints.put(new Point(x, y), ap);
                }
            }
        }

        return bestPoints;
    }

    @Override
    public LinkedHashMap<Point, ArrayList<Point>> idealLocations(Set<Point> priorityTargets, Set<Point> lesserTargets, Set<Point> requiredExclusions) {
        if(priorityTargets == null)
            return idealLocations(lesserTargets, requiredExclusions);
        if(requiredExclusions == null) requiredExclusions = new LinkedHashSet<Point>();

        int totalTargets = priorityTargets.size() + lesserTargets.size();
        LinkedHashMap<Point, ArrayList<Point>> bestPoints = new LinkedHashMap<Point, ArrayList<Point>>(totalTargets * 8);

        if(totalTargets == 0)
            return bestPoints;

        Point[] pts = priorityTargets.toArray(new Point[priorityTargets.size()]);
        Point[] lts = lesserTargets.toArray(new Point[lesserTargets.size()]);
        Point[] exs = requiredExclusions.toArray(new Point[requiredExclusions.size()]);
        Point t = exs[0];

        double[][][] compositeMap = new double[totalTargets][dungeon.length][dungeon[0].length];

        char[][] dungeonCopy = new char[dungeon.length][dungeon[0].length],
                dungeonPriorities = new char[dungeon.length][dungeon[0].length];
        for (int i = 0; i < dungeon.length; i++) {
            System.arraycopy(dungeon[i], 0, dungeonCopy[i], 0, dungeon[i].length);
            Arrays.fill(dungeonPriorities[i], '#');
        }
        DijkstraMap dt = new DijkstraMap(dungeon, dijkstra.measurement);
        for (int i = 0; i < exs.length; ++i, t = exs[i]) {
            dt.resetMap();
            dt.clearGoals();
            List<Point> lit = Elias.line(start, t);

            for(Point p : lit)
            {
                dt.setGoal(p);
            }
            if(radius > 0)
                dt.partialScan(radius, null);

            for (int x = 0; x < dungeon.length; x++) {
                for (int y = 0; y < dungeon[x].length; y++) {
                    dungeonCopy[x][y] = (dt.gradientMap[x][y] < DijkstraMap.FLOOR) ? '!' : dungeonCopy[x][y];
                }
            }
        }

        t = pts[0];

        DijkstraMap dm = new DijkstraMap(dungeon, dijkstra.measurement);

        for (int i = 0; i < pts.length; ++i, t = pts[i]) {
            dt.resetMap();
            dt.clearGoals();
            List<Point> lit = Elias.line(start, t);

            for(Point p : lit)
            {
                dt.setGoal(p);
            }
            if(radius > 0)
                dt.partialScan(radius, null);

            for (int x = 0; x < dungeon.length; x++) {
                for (int y = 0; y < dungeon[x].length; y++) {
                    compositeMap[i][x][y] = (dt.gradientMap[x][y] < DijkstraMap.FLOOR) ? dm.physicalMap[x][y] : DijkstraMap.WALL;
                    if(dt.gradientMap[x][y] < DijkstraMap.FLOOR)
                        dungeonPriorities[x][y] = dungeon[x][y];
                }
            }
            dm.initialize(compositeMap[i]);
            dm.setGoal(t);
            dm.scan(null);
            for (int x = 0; x < dungeon.length; x++) {
                for (int y = 0; y < dungeon[x].length; y++) {
                    compositeMap[i][x][y] = (dm.gradientMap[x][y] < DijkstraMap.FLOOR  && dungeonCopy[x][y] != '!') ? dm.gradientMap[x][y] : 399999.0;
                }
            }
            dm.resetMap();
            dm.clearGoals();
        }

        t = lts[0];

        for (int i = pts.length; i < totalTargets; ++i, t = lts[i - pts.length]) {
            dt.resetMap();
            dt.clearGoals();
            List<Point> lit = Elias.line(start, t);

            for(Point p : lit)
            {
                dt.setGoal(p);
            }
            if(radius > 0)
                dt.partialScan(radius, null);

            for (int x = 0; x < dungeon.length; x++) {
                for (int y = 0; y < dungeon[x].length; y++) {
                    compositeMap[i][x][y] = (dt.gradientMap[x][y] < DijkstraMap.FLOOR) ? dm.physicalMap[x][y] : DijkstraMap.WALL;
                }
            }
            dm.initialize(compositeMap[i]);
            dm.setGoal(t);
            dm.scan(null);
            for (int x = 0; x < dungeon.length; x++) {
                for (int y = 0; y < dungeon[x].length; y++) {
                    compositeMap[i][x][y] = (dm.gradientMap[x][y] < DijkstraMap.FLOOR  && dungeonCopy[x][y] != '!' && dungeonPriorities[x][y] != '#') ? dm.gradientMap[x][y] : 99999.0;
                }
            }
            dm.resetMap();
            dm.clearGoals();
        }
        double bestQuality = 99999 * lts.length + 399999 * pts.length;
        double[][] qualityMap = new double[dungeon.length][dungeon[0].length];
        for (int x = 0; x < qualityMap.length; x++) {
            for (int y = 0; y < qualityMap[x].length; y++) {
                qualityMap[x][y] = 0.0;
                long pbits = 0, lbits = 0;
                for (int i = 0; i < pts.length; ++i) {
                    qualityMap[x][y] += compositeMap[i][x][y];
                    if(compositeMap[i][x][y] < 399999.0 && i < 63)
                        pbits |= 1 << i;
                }
                for (int i = pts.length; i < totalTargets; ++i) {
                    qualityMap[x][y] += compositeMap[i][x][y];
                    if(compositeMap[i][x][y] < 99999.0 && i < 63)
                        lbits |= 1 << i;
                }
                if(qualityMap[x][y] < bestQuality)
                {
                    bestQuality = qualityMap[x][y];
                    bestPoints.clear();
                    ArrayList<Point> ap = new ArrayList<Point>();

                    for (int i = 0; i < pts.length && i < 63; ++i) {
                        if((pbits & (1 << i)) != 0)
                            ap.add(pts[i]);
                    }
                    for (int i = pts.length; i < totalTargets && i < 63; ++i) {
                        if((lbits & (1 << i)) != 0)
                            ap.add(lts[i - pts.length]);
                    }
                    bestPoints.put(new Point(x, y), ap);

                }
                else if(qualityMap[x][y] == bestQuality)
                {
                    ArrayList<Point> ap = new ArrayList<Point>();

                    for (int i = 0; i < pts.length && i < 63; ++i) {
                        if((pbits & (1 << i)) != 0)
                            ap.add(pts[i]);
                    }
                    for (int i = pts.length; i < totalTargets && i < 63; ++i) {
                        if((lbits & (1 << i)) != 0)
                            ap.add(lts[i - pts.length]);
                    }
                    bestPoints.put(new Point(x, y), ap);
                }
            }
        }

        return bestPoints;
    }

/*
    @Override
    public ArrayList<ArrayList<Point>> idealLocations(Set<Point> targets, Set<Point> requiredExclusions) {
        int totalTargets = targets.size() + 1;
        int volume = (int)(rt.radius(1, 1, dungeon.length - 2, dungeon[0].length - 2) * radius * 2.1);
        ArrayList<ArrayList<Point>> locs = new ArrayList<ArrayList<Point>>(totalTargets);
        for(int i = 0; i < totalTargets; i++)
        {
            locs.add(new ArrayList<Point>(volume));
        }
        if(totalTargets == 1)
            return locs;

        int ctr = 0;

        boolean[][] tested = new boolean[dungeon.length][dungeon[0].length];
        for (int x = 1; x < dungeon.length - 1; x += radius) {
            for (int y = 1; y < dungeon[x].length - 1; y += radius) {

                if(mayContainTarget(requiredExclusions, x, y))
                    continue;
                ctr = 0;
                for(Point tgt : targets)
                {
                    if(rt.radius(start.x, start.y, tgt.x, tgt.y) + rt.radius(end.x, end.y, tgt.x, tgt.y) -
                        rt.radius(start.x, start.y, end.x, end.y) <= 3.0 + radius)
                        ctr++;
                }
                if(ctr > 0)
                    locs.get(totalTargets - ctr).add(new Point(x, y));
            }
        }
        Point it;
        for(int t = 0; t < totalTargets - 1; t++)
        {
            if(locs.get(t).size() > 0) {
                int numPoints = locs.get(t).size();
                for (int i = 0; i < numPoints; i++) {
                    it = locs.get(t).get(i);
                    for (int x = Math.max(1, it.x - radius / 2); x < it.x + (radius + 1) / 2 && x < dungeon.length - 1; x++) {
                        for (int y = Math.max(1, it.y - radius / 2); y <= it.y + (radius - 1) / 2 && y < dungeon[0].length - 1; y++)
                        {
                            if(tested[x][y])
                                continue;
                            tested[x][y] = true;

                            if(mayContainTarget(requiredExclusions, x, y))
                                continue;

                            ctr = 0;
                            for(Point tgt : targets)
                            {
                                if(rt.radius(start.x, start.y, tgt.x, tgt.y) + rt.radius(end.x, end.y, tgt.x, tgt.y) -
                                        rt.radius(start.x, start.y, end.x, end.y) <= 3.0 + radius)
                                    ctr++;
                            }
                            if(ctr > 0)
                                locs.get(totalTargets - ctr).add(new Point(x, y));
                        }
                    }
                }
            }
        }
        return locs;
    }
*/
    @Override
    public void setMap(char[][] map) {
        this.dungeon = map;
        dijkstra.resetMap();
        dijkstra.clearGoals();
    }

    @Override
    public LinkedHashMap<Point, Double> findArea() {
        double[][] dmap = initDijkstra();
        dmap[start.x][start.y] = DijkstraMap.DARK;
        dijkstra.resetMap();
        dijkstra.clearGoals();
        return AreaUtils.dijkstraToHashMap(dmap);
    }
}
