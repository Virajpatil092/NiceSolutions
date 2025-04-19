package com.nice.avishkar;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        // Point at the actual resources folder
        File baseDir = new File("src/main/resources");

        File[] testCaseDirs = baseDir.listFiles((dir, name) -> name.startsWith("TestCase-"));
        if (testCaseDirs == null || testCaseDirs.length == 0) {
            System.err.println("No test case directories found in: " + baseDir.getAbsolutePath());
            return;
        }

        ITravelOptimizer optimizer = new TravelOptimizerImpl();

        for (File testDir : testCaseDirs) {
            String testName = testDir.getName();
            File schedulesFile = new File(testDir, "Schedules.csv");
            File customerFile  = new File(testDir, "CustomerRequests.csv");

            if (!schedulesFile.exists() || !customerFile.exists()) {
                System.err.println("Missing CSV files in " + testName);
                continue;
            }

            ResourceInfo info = new ResourceInfo(
                    schedulesFile.toPath(),
                    customerFile.toPath()
            );

            Map<String, OptimalTravelSchedule> results =
                    optimizer.getOptimalTravelOptions(info);

            // Write under src/main/resources/TestCase-3_output.json, etc.
            File outputFile = new File(baseDir, testName + "_output.json");
            // Make sure the resources folder (and any subfolders) exist
            File parent = outputFile.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                throw new RuntimeException("Could not create output directory: " + parent);
            }

            writeResultsToJson(outputFile, results);
            System.out.println("Generated: " + outputFile.getPath());
        }
    }

    private static void writeResultsToJson(File file, Map<String, OptimalTravelSchedule> data)
            throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("{\n");
            int count = 0, total = data.size();
            for (Map.Entry<String, OptimalTravelSchedule> entry : data.entrySet()) {
                String reqId = entry.getKey();
                OptimalTravelSchedule sched = entry.getValue();

                writer.write("  \"" + reqId + "\": {\n");
                writer.write("    \"criteria\": \"" + sched.getCriteria() + "\",\n");
                writer.write("    \"value\": " + sched.getValue() + ",\n");
                writer.write("    \"routes\": [\n");

                List<Route> routes = sched.getRoutes();
                for (int i = 0; i < routes.size(); i++) {
                    Route r = routes.get(i);
                    writer.write("      {\n");
                    writer.write("        \"source\":      \"" + r.getSource()      + "\",\n");
                    writer.write("        \"destination\": \"" + r.getDestination() + "\",\n");
                    writer.write("        \"mode\":        \"" + r.getMode()        + "\",\n");
                    writer.write("        \"departureTime\": \"" + r.getDepartureTime() + "\",\n");
                    writer.write("        \"arrivalTime\":   \"" + r.getArrivalTime()   + "\"\n");
                    writer.write("      }" + (i < routes.size() - 1 ? "," : "") + "\n");
                }

                writer.write("    ]\n");
                writer.write("  }" + (++count < total ? "," : "") + "\n");
            }
            writer.write("}\n");
        }
    }
}
