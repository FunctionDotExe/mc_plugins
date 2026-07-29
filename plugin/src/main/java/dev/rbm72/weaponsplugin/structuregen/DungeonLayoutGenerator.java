package dev.rbm72.weaponsplugin.structuregen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Lays out a dungeon as a graph on a square grid: a random walk from the origin cell places one room
 * per visited cell (retrying a few times per step so one dead end doesn't kill the whole walk before
 * the room budget is spent), then a breadth-first search from the origin finds the cell farthest away
 * by path length — that cell becomes the treasure room, so the crystal is never one door from the
 * entrance. Cell adjacency in the walk is exactly the corridor graph {@link DungeonBuilder} carves.
 */
final class DungeonLayoutGenerator {

    record Cell(int gx, int gz) {
    }

    record Layout(List<Cell> cells, List<Cell[]> edges, Cell start, Cell treasure) {
    }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private DungeonLayoutGenerator() {
    }

    static Layout generate(int roomCount, Random rng) {
        List<Cell> cells = new ArrayList<>();
        List<Cell[]> edges = new ArrayList<>();
        Set<Cell> occupied = new HashSet<>();
        Map<Cell, List<Cell>> adjacency = new HashMap<>();

        Cell start = new Cell(0, 0);
        cells.add(start);
        occupied.add(start);
        Cell current = start;

        int attemptsRemaining = roomCount * 20;
        while (cells.size() < roomCount && attemptsRemaining-- > 0) {
            int[] dir = DIRECTIONS[rng.nextInt(DIRECTIONS.length)];
            Cell next = new Cell(current.gx() + dir[0], current.gz() + dir[1]);
            if (occupied.contains(next)) {
                // Backtrack to a random already-placed room so a boxed-in dead end doesn't stall the walk.
                current = cells.get(rng.nextInt(cells.size()));
                continue;
            }
            cells.add(next);
            occupied.add(next);
            edges.add(new Cell[]{current, next});
            adjacency.computeIfAbsent(current, c -> new ArrayList<>()).add(next);
            adjacency.computeIfAbsent(next, c -> new ArrayList<>()).add(current);
            current = next;
        }

        Cell treasure = farthestFrom(start, adjacency);
        return new Layout(cells, edges, start, treasure);
    }

    private static Cell farthestFrom(Cell start, Map<Cell, List<Cell>> adjacency) {
        Map<Cell, Integer> distance = new HashMap<>();
        distance.put(start, 0);
        Deque<Cell> queue = new ArrayDeque<>();
        queue.add(start);
        Cell farthest = start;
        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            for (Cell neighbor : adjacency.getOrDefault(cell, List.of())) {
                if (!distance.containsKey(neighbor)) {
                    distance.put(neighbor, distance.get(cell) + 1);
                    queue.add(neighbor);
                    if (distance.get(neighbor) > distance.get(farthest)) {
                        farthest = neighbor;
                    }
                }
            }
        }
        return farthest;
    }
}
