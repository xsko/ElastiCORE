package io.elasticore.base.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ConsoleLog {

    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";


    private static Map<String, List<String>> consoleLogMap = new HashMap<>();

    // Accumulated counters for validation summary across multiple domains
    private static Map<String, AtomicInteger> accumulatedCounts = new HashMap<>();

    private static boolean validationMode = false;

    public static void setValidationMode(boolean mode) { validationMode = mode; }
    public static boolean isValidationMode() { return validationMode; }


    public static void storeLog(String key, String message) {
        consoleLogMap.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(message);
    }

    public static void clear() {
        consoleLogMap.clear();
        accumulatedCounts.clear();
    }

    /** What is stored now, for a run inside a run to put back as it found it (restore). */
    public static Object snapshot() {
        Map<String, List<String>> logs = new HashMap<>();
        for (Map.Entry<String, List<String>> e : consoleLogMap.entrySet()) logs.put(e.getKey(), new ArrayList<>(e.getValue()));
        Map<String, AtomicInteger> counts = new HashMap<>();
        for (Map.Entry<String, AtomicInteger> e : accumulatedCounts.entrySet()) counts.put(e.getKey(), new AtomicInteger(e.getValue().get()));
        return new Object[]{logs, counts};
    }

    @SuppressWarnings("unchecked")
    public static void restore(Object snapshot) {
        Object[] s = (Object[]) snapshot;
        consoleLogMap = (Map<String, List<String>>) s[0];
        accumulatedCounts = (Map<String, AtomicInteger>) s[1];
    }

    /**
     * Clear specific log key entries after printing per-domain report.
     * Before clearing, accumulates count for final summary.
     */
    public static void clearKey(String key) {
        List<String> logs = consoleLogMap.get(key);
        if (logs != null && !logs.isEmpty()) {
            accumulatedCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).addAndGet(logs.size());
            logs.clear();
        }
    }

    /**
     * Get accumulated count across all domains for final validation summary.
     */
    public static int getAccumulatedCount(String key) {
        AtomicInteger count = accumulatedCounts.get(key);
        return (count != null) ? count.get() : 0;
    }

    public static void printStoredLog(String key, String blankGap) {
        try {
            consoleLogMap.get(key).forEach(message -> {
                print(blankGap + message);
            });
        } catch (NullPointerException npe) {
            // Log map entry not found for key: could add logging if needed
        }
    }

    public static void printStoredLog(String key, String blankGap, int maxCount) {

        try {
            List<String> logs = consoleLogMap.get(key);
            int storedLogCount = logs.size();
            int count = 0;

            for (String message : logs) {
                count++;
                if (count == maxCount) {
                    print(blankGap + " * total count: " + storedLogCount);
                    break;
                }
                print(blankGap + message);
            }

        } catch (NullPointerException npe) {
            // Log map entry not found for key: could add logging if needed
        }
    }

    public static void printStoredInfoLog(String key, String blankGap) {
        try {
            consoleLogMap.get(key).forEach(message -> {
                printInfo(blankGap + message);
            });
        } catch (NullPointerException npe) {
            // Log map entry not found for key: could add logging if needed
        }
    }

    public static void printStoredWarnLog(String key, String blankGap) {
        try {
            consoleLogMap.get(key).forEach(message -> {
                printWarn(blankGap + message);
            });
        } catch (NullPointerException npe) {
            // Log map entry not found for key: could add logging if needed
        }
    }

    /**
     * Count log entries whose message starts with the given prefix.
     */
    public static int countByPrefix(String key, String prefix) {
        List<String> logs = consoleLogMap.get(key);
        if (logs == null || logs.isEmpty()) return 0;
        int count = 0;
        for (String msg : logs) {
            if (msg.startsWith(prefix)) count++;
        }
        return count;
    }

    /**
     * Print only log entries whose message starts with the given prefix, remove them from the list,
     * and accumulate the printed count into accumulatedCounts so the final summary count remains correct.
     * Returns the number of entries printed.
     */
    public static int printAndRemoveByPrefix(String key, String prefix, String blankGap, boolean isWarn) {
        List<String> logs = consoleLogMap.get(key);
        if (logs == null || logs.isEmpty()) return 0;

        List<String> matched = new ArrayList<>();
        List<String> remaining = new ArrayList<>();
        for (String msg : logs) {
            if (msg.startsWith(prefix)) {
                matched.add(msg);
            } else {
                remaining.add(msg);
            }
        }

        for (String msg : matched) {
            if (isWarn) printWarn(blankGap + msg);
            else print(blankGap + msg);
        }

        logs.clear();
        logs.addAll(remaining);

        if (!matched.isEmpty()) {
            accumulatedCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).addAndGet(matched.size());
        }

        return matched.size();
    }

    /**
     * Print all remaining entries for a given key as orphans (entries that did not match any domain prefix).
     * Used after all per-domain processing completes. Accumulates count and clears the list.
     */
    public static int printRemainingAsOrphans(String key, String header, String blankGap, boolean isWarn) {
        List<String> logs = consoleLogMap.get(key);
        if (logs == null || logs.isEmpty()) return 0;

        int count = logs.size();
        if (header != null) {
            print("");
            print(header);
            print("--------------------------------------");
        }
        for (String msg : logs) {
            if (isWarn) printWarn(blankGap + msg);
            else print(blankGap + msg);
        }
        if (header != null) {
            print("");
        }

        accumulatedCounts.computeIfAbsent(key, k -> new AtomicInteger(0)).addAndGet(count);
        logs.clear();
        return count;
    }

    public static int countStoredLogkey(String key) {
        try {
            return consoleLogMap.get(key).size();
        }catch (NullPointerException npe){}

        return 0;
    }


    public static boolean hasStoredLogkey(String key) {
        return consoleLogMap.containsKey(key);
    }


    public static void print(Object message) {
        System.out.println(message);
    }

    public static void printInfo(Object message) {
        System.out.println(GREEN + message + RESET);
    }

    public static void printWarn(Object message) {
        System.out.println(RED + message + RESET);
    }

    public static void printError(Object message) {
        System.err.println(RED + message + RESET);
    }

}
