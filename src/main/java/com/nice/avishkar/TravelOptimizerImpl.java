package com.nice.avishkar;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TravelOptimizerImpl implements ITravelOptimizer {

    private static class Edge {
        String src, dest, mode;
        int depMin, arrMin;
        long cost;
        String depStr, arrStr;

        Edge(String src, String dest, String mode, String depStr, String arrStr, long cost) {
            this.src = src;
            this.dest = dest;
            this.mode = mode;
            this.depStr = depStr;
            this.arrStr = arrStr;
            this.depMin = toMinutes(depStr);
            this.arrMin = toMinutes(arrStr);
            this.cost = cost;
        }

        private static int toMinutes(String time) {
            LocalTime t = LocalTime.parse(time, DateTimeFormatter.ofPattern("H:mm"));
            return t.getHour() * 60 + t.getMinute();
        }
    }

    private static class State {
        String loc;
        int currArrMin;
        int totalTime;
        long totalCost;
        int hops;
        List<Edge> path;

        State(String loc, int currArrMin, int totalTime, long totalCost, int hops, List<Edge> path) {
            this.loc = loc;
            this.currArrMin = currArrMin;
            this.totalTime = totalTime;
            this.totalCost = totalCost;
            this.hops = hops;
            this.path = path;
        }
    }

    @Override
    public Map<String, OptimalTravelSchedule> getOptimalTravelOptions(ResourceInfo resourceInfo) throws IOException {
        Map<String, List<Edge>> graph = new HashMap<>();

        try (BufferedReader br = Files.newBufferedReader(resourceInfo.getTransportSchedulePath())) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                String src = parts[0].trim();
                String dest = parts[1].trim();
                String mode = parts[2].trim();
                String dep = parts[3].trim();
                String arr = parts[4].trim();
                long cost = Long.parseLong(parts[5].trim());
                Edge e = new Edge(src, dest, mode, dep, arr, cost);
                graph.computeIfAbsent(src, k -> new ArrayList<>()).add(e);
            }
        }

        Map<String, OptimalTravelSchedule> result = new HashMap<>();
        try (BufferedReader br = Files.newBufferedReader(resourceInfo.getCustomerRequestPath())) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                String reqId = parts[0].trim();
                String source = parts[2].trim();
                String dest = parts[3].trim();
                String criterion = parts[4].trim();

                OptimalTravelSchedule sched = findOptimal(source, dest, criterion, graph);
                result.put(reqId, sched);
            }
        }

        return result;
    }

    private OptimalTravelSchedule findOptimal(String src, String dest, String criterion, Map<String, List<Edge>> graph) {
        if (src.equals(dest)) {
            return new OptimalTravelSchedule(Collections.emptyList(), criterion, 0L);
        }

        Comparator<State> comparator = (a, b) -> {
            if (criterion.equalsIgnoreCase("Time")) {
                int cmp = Integer.compare(a.totalTime, b.totalTime);
                if (cmp != 0) return cmp;
                cmp = Long.compare(a.totalCost, b.totalCost);
                if (cmp != 0) return cmp;
                return Integer.compare(a.hops, b.hops);
            } else if (criterion.equalsIgnoreCase("Cost")) {
                int cmp = Long.compare(a.totalCost, b.totalCost);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(a.totalTime, b.totalTime);
                if (cmp != 0) return cmp;
                return Integer.compare(a.hops, b.hops);
            } else {
                int cmp = Integer.compare(a.hops, b.hops);
                if (cmp != 0) return cmp;
                cmp = Integer.compare(a.totalTime, b.totalTime);
                if (cmp != 0) return cmp;
                return Long.compare(a.totalCost, b.totalCost);
            }
        };

        PriorityQueue<State> pq = new PriorityQueue<>(comparator);
        Set<String> visited = new HashSet<>();

        List<Edge> startEdges = graph.getOrDefault(src, Collections.emptyList());
        for (Edge e : startEdges) {
            int travel = e.arrMin - e.depMin;
            List<Edge> path = new ArrayList<>();
            path.add(e);
            pq.add(new State(e.dest, e.arrMin, travel, e.cost, 1, path));
        }

        while (!pq.isEmpty()) {
            State cur = pq.poll();

            if (visited.contains(cur.loc)) continue;
            visited.add(cur.loc);

            if (cur.loc.equals(dest)) {
                List<Route> routes = new ArrayList<>();
                for (Edge e : cur.path) {
                    routes.add(new Route(e.src, e.dest, e.mode, e.depStr, e.arrStr));
                }

                long value = "Time".equalsIgnoreCase(criterion) ? cur.totalTime
                        : "Cost".equalsIgnoreCase(criterion) ? cur.totalCost
                        : cur.hops;

                return new OptimalTravelSchedule(routes, criterion, value);
            }

            for (Edge e : graph.getOrDefault(cur.loc, Collections.emptyList())) {
                if (e.depMin < cur.currArrMin) continue;

                int wait = e.depMin - cur.currArrMin;
                if(wait < 0){
                    wait = 0;
                }
                int travel = e.arrMin - e.depMin;

                if(travel < 0){
                    continue;
                }

                List<Edge> newPath = new ArrayList<>(cur.path);
                newPath.add(e);

                pq.add(new State(
                        e.dest,
                        e.arrMin,
                        cur.totalTime + wait + travel,
                        cur.totalCost + e.cost,
                        cur.hops + 1,
                        newPath
                ));
            }
        }

        return new OptimalTravelSchedule(Collections.emptyList(), criterion, 0L);
    }
}
