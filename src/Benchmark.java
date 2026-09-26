import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Runs the four fixed workloads of the assignment and writes the results to
 * {@code results/tables/} (one CSV per workload + {@code summary.md} + {@code environment.txt}).
 *
 * <pre>
 *   javac -encoding UTF-8 -d out src/*.java
 *   java -Xms1g -Xmx1g -cp out Benchmark                      # all workloads, default GC (G1)
 *   java -Xms1g -Xmx1g -XX:+UseParallelGC -cp out Benchmark --only w3 --tag parallelgc
 * </pre>
 * Options: {@code --out DIR} (default results/tables), {@code --only w1|w2|w3|w4|jdk}
 * (comma-separated), {@code --tag NAME} (suffix for the CSV file names).
 *
 * <h2>Protocol (identical for every experiment)</h2>
 * <ul>
 *   <li>n ∈ {100, 1 000, 10 000, 100 000}; m is fixed per workload (10 000 gets, 1 000 searches,
 *       1 000 inserts + 1 000 removals, n heap inserts + n extractions).</li>
 *   <li>All input data (values, indices, search keys) is generated from {@code new Random(42)}
 *       <em>before</em> any timed section, so every structure and every repetition sees exactly
 *       the same data. Building the structure is also untimed.</li>
 *   <li>A JIT warm-up pass runs every workload {@value #WARMUP_REPS} times at n = 1 000 and
 *       3 times at n = 10 000 before anything is measured, so the timed loops run as C2-compiled
 *       code; then each experiment runs 1 discarded repetition + {@value #RUNS} measured repetitions. The reported value
 *       is the mean of the {@value #RUNS} measured repetitions (min/max/stddev are in the CSVs).</li>
 *   <li>Timing uses {@link System#nanoTime()} around the operation loop only; no printing or
 *       allocation of inputs happens inside it. Results feed a checksum so the JIT cannot
 *       eliminate the work.</li>
 *   <li>Operation counts come from the counters built into the structures; they are
 *       deterministic, so they are identical in every repetition.</li>
 * </ul>
 */
public class Benchmark {

    static final int[] SIZES = {100, 1_000, 10_000, 100_000};
    static final int RUNS = 5;
    static final long SEED = 42L;
    static final int W1_OPS = 10_000;
    static final int W2_OPS = 1_000;
    static final int W3_OPS = 1_000;
    static final int WARMUP_REPS = 15;

    /** Accumulates results of the timed loops so the JIT cannot treat them as dead code. */
    private static long checksum;
    private static boolean warmup;
    private static int warmupReps;

    private static final StringBuilder w1Csv = new StringBuilder(
            "structure,n,m,avg_ms,min_ms,max_ms,std_ms,ns_per_op,accesses,expected_accesses,ns_per_access,theory_get\n");
    private static final StringBuilder w2Csv = new StringBuilder(
            "structure,n,m,avg_ms,min_ms,max_ms,std_ms,us_per_op,hits,comparisons,expected_comparisons,ns_per_comparison,theory_contains\n");
    private static final StringBuilder w3Csv = new StringBuilder(
            "structure,position,operation,index,n,m,avg_ms,min_ms,max_ms,std_ms,us_per_op,movements,node_visits,predicted_work,ns_per_unit_of_work,restored,theory_per_op\n");
    private static final StringBuilder w4Csv = new StringBuilder(
            "phase,n,m,avg_ms,min_ms,max_ms,std_ms,ns_per_op,comparisons,comparisons_per_op,comparisons_per_log2n,swaps,worst_case_bound,non_decreasing,theory_per_op\n");
    private static final StringBuilder jdkCsv = new StringBuilder(
            "workload,structure,operation,n,m,avg_ms,min_ms,max_ms,std_ms\n");

    private static final StringBuilder w1Md = new StringBuilder();
    private static final StringBuilder w2Md = new StringBuilder();
    private static final StringBuilder w3Md = new StringBuilder();
    private static final StringBuilder w4Md = new StringBuilder();
    private static final StringBuilder jdkMd = new StringBuilder();

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ROOT);
        Path outDir = Paths.get("results/tables");
        List<String> only = List.of("w1", "w2", "w3", "w4", "jdk");
        String tag = "";
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> outDir = Paths.get(args[++i]);
                case "--only" -> only = Arrays.asList(args[++i].toLowerCase(Locale.ROOT).split(","));
                case "--tag" -> tag = "_" + args[++i];
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        Files.createDirectories(outDir);

        long start = System.nanoTime();
        System.out.println("GC: " + gcNames() + "   workloads: " + only);
        System.out.println("JIT warm-up pass (results discarded)...");
        warmup = true;
        for (int n : new int[]{1_000, 10_000}) {
            warmupReps = n == 1_000 ? WARMUP_REPS : 3;
            runSelected(only, n);
        }
        warmup = false;

        for (int n : SIZES) {
            System.out.println("\n=== n = " + n);
            runSelected(only, n);
        }

        if (only.contains("w1")) {
            write(outDir.resolve("w1_random_access" + tag + ".csv"), w1Csv.toString());
        }
        if (only.contains("w2")) {
            write(outDir.resolve("w2_search" + tag + ".csv"), w2Csv.toString());
        }
        if (only.contains("w3")) {
            write(outDir.resolve("w3_insert_remove" + tag + ".csv"), w3Csv.toString());
        }
        if (only.contains("w4")) {
            write(outDir.resolve("w4_heap" + tag + ".csv"), w4Csv.toString());
        }
        if (only.contains("jdk")) {
            write(outDir.resolve("jdk_reference" + tag + ".csv"), jdkCsv.toString());
        }
        write(outDir.resolve("summary" + tag + ".md"), summaryMarkdown(only));
        write(outDir.resolve("environment" + tag + ".txt"), environment());

        System.out.printf("%nDone in %.1f s. Tables written to %s%n", (System.nanoTime() - start) / 1e9, outDir);
        System.out.println("(checksum, ignore: " + checksum + ")");
    }

    private static void runSelected(List<String> only, int n) {
        if (only.contains("w1")) {
            workload1(n);
        }
        if (only.contains("w2")) {
            workload2(n);
        }
        if (only.contains("w3")) {
            workload3(n);
        }
        if (only.contains("w4")) {
            workload4(n);
        }
        if (only.contains("jdk")) {
            jdkReference(n);
        }
    }

    // ============================================================ Workload 1 — random access

    private static void workload1(int n) {
        Random rnd = new Random(SEED);
        Integer[] values = randomValues(rnd, n);
        int[] idx = new int[W1_OPS];
        for (int i = 0; i < W1_OPS; i++) {
            idx[i] = rnd.nextInt(n);
        }

        long[] accesses = new long[1];
        double[] arr = timeRuns(() -> {
            DynamicArray<Integer> a = buildArray(values, n);
            a.resetCounters();
            prepare();
            long acc = 0;
            long t0 = System.nanoTime();
            for (int i = 0; i < W1_OPS; i++) {
                acc += a.get(idx[i]);
            }
            long t = System.nanoTime() - t0;
            checksum += acc;
            accesses[0] = a.getAccesses();
            return new long[]{t};
        })[0];
        recordW1("DynamicArray", n, arr, accesses[0], W1_OPS, "Θ(1)");

        double[] lst = timeRuns(() -> {
            LinkedList<Integer> l = buildList(values);
            l.resetCounters();
            prepare();
            long acc = 0;
            long t0 = System.nanoTime();
            for (int i = 0; i < W1_OPS; i++) {
                acc += l.get(idx[i]);
            }
            long t = System.nanoTime() - t0;
            checksum += acc;
            accesses[0] = l.getAccesses();
            return new long[]{t};
        })[0];
        // Expected nodes visited for a uniform index: E[min(i, n-1-i)] + 1
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += Math.min(i, n - 1 - i) + 1;
        }
        long expected = Math.round(W1_OPS * sum / n);
        recordW1("LinkedList", n, lst, accesses[0], expected, "Θ(min(i, n−i)) ⇒ Θ(n)");
    }

    private static void recordW1(String s, int n, double[] st, long accesses, long expected, String theory) {
        if (warmup) {
            return;
        }
        double nsPerOp = st[0] * 1e6 / W1_OPS;
        double nsPerAccess = st[0] * 1e6 / accesses;
        w1Csv.append(csv(s, n, W1_OPS, st[0], st[1], st[2], st[3], fmt(nsPerOp), accesses, expected, fmt(nsPerAccess), theory));
        w1Md.append(md(fmtN(n), s, ms(st[0]), fmt(nsPerOp), fmtN(accesses), fmtN(expected), fmt(nsPerAccess), theory));
        System.out.printf("  W1 %-12s avg %10.3f ms  %9.1f ns/get  accesses %,d%n", s, st[0], nsPerOp, accesses);
    }

    // ============================================================ Workload 2 — search

    private static void workload2(int n) {
        Random rnd = new Random(SEED);
        Integer[] values = randomValues(rnd, n);
        // Half of the keys are present (a copy of a random stored element), half are absent
        // (negative, while every stored value is non-negative). Keys are interleaved hit/miss.
        Integer[] keys = new Integer[W2_OPS];
        for (int i = 0; i < W2_OPS; i++) {
            keys[i] = (i % 2 == 0)
                    ? Integer.valueOf(values[rnd.nextInt(n)].intValue())   // new object, equal value
                    : Integer.valueOf(-1 - rnd.nextInt(Integer.MAX_VALUE));
        }
        int hitsExpected = (W2_OPS + 1) / 2;
        int misses = W2_OPS - hitsExpected;
        long expected = Math.round(hitsExpected * (n + 1) / 2.0 + (double) misses * n);

        long[] m = new long[2];
        double[] arr = timeRuns(() -> {
            DynamicArray<Integer> a = buildArray(values, n);
            a.resetCounters();
            prepare();
            int hits = 0;
            long t0 = System.nanoTime();
            for (int i = 0; i < W2_OPS; i++) {
                if (a.contains(keys[i])) {
                    hits++;
                }
            }
            long t = System.nanoTime() - t0;
            checksum += hits;
            m[0] = a.getComparisons();
            m[1] = hits;
            return new long[]{t};
        })[0];
        recordW2("DynamicArray", n, arr, m[1], m[0], expected);

        double[] lst = timeRuns(() -> {
            LinkedList<Integer> l = buildList(values);
            l.resetCounters();
            prepare();
            int hits = 0;
            long t0 = System.nanoTime();
            for (int i = 0; i < W2_OPS; i++) {
                if (l.contains(keys[i])) {
                    hits++;
                }
            }
            long t = System.nanoTime() - t0;
            checksum += hits;
            m[0] = l.getComparisons();
            m[1] = hits;
            return new long[]{t};
        })[0];
        recordW2("LinkedList", n, lst, m[1], m[0], expected);
    }

    private static void recordW2(String s, int n, double[] st, long hits, long comparisons, long expected) {
        if (warmup) {
            return;
        }
        double usPerOp = st[0] * 1e3 / W2_OPS;
        String theory = "Θ(n) avg/worst, Θ(1) best";
        double nsPerCmp = st[0] * 1e6 / comparisons;
        w2Csv.append(csv(s, n, W2_OPS, st[0], st[1], st[2], st[3], fmt(usPerOp), hits, comparisons, expected, fmt(nsPerCmp), theory));
        w2Md.append(md(fmtN(n), s, ms(st[0]), fmt(usPerOp), hits + "/" + W2_OPS, fmtN(comparisons), fmtN(expected), fmt(nsPerCmp), theory));
        System.out.printf("  W2 %-12s avg %10.3f ms  %9.2f us/search  comparisons %,d (hits %d)%n", s, st[0], usPerOp, comparisons, hits);
    }

    // ============================================================ Workload 3 — insertion & removal

    private static void workload3(int n) {
        Random rnd = new Random(SEED);
        Integer[] values = randomValues(rnd, n);
        Integer[] extra = randomValues(rnd, W3_OPS);           // the values that get inserted

        for (String position : new String[]{"front", "middle"}) {
            final int index = position.equals("front") ? 0 : n / 2;

            // ---- Dynamic Array. Capacity is reserved for n + m elements (untimed), so the
            // timed section measures positional shifting only, not resizing.
            long[] m = new long[4];
            boolean[] restored = new boolean[1];
            double[][] arr = timeRuns(() -> {
                DynamicArray<Integer> a = buildArray(values, n + W3_OPS);
                a.resetCounters();
                prepare();
                long t0 = System.nanoTime();
                for (int i = 0; i < W3_OPS; i++) {
                    a.add(index, extra[i]);
                }
                long tIns = System.nanoTime() - t0;
                m[0] = a.getMovements();
                m[1] = a.getAccesses();
                a.resetCounters();
                // Phase B starts from the state left by phase A and removes at the same index:
                // this deletes exactly the 1 000 inserted elements and restores the original.
                long acc = 0;
                long t1 = System.nanoTime();
                for (int i = 0; i < W3_OPS; i++) {
                    acc += a.remove(index);
                }
                long tRem = System.nanoTime() - t1;
                checksum += acc;
                m[2] = a.getMovements();
                m[3] = a.getAccesses();
                restored[0] = sameContents(a, values);
                return new long[]{tIns, tRem};
            });
            long shifts = (long) W3_OPS * (n - index) + (long) W3_OPS * (W3_OPS - 1) / 2;
            recordW3("DynamicArray", position, "insert", index, n, arr[0], m[0], 0, shifts, restored[0], "Θ(n − index)");
            recordW3("DynamicArray", position, "remove", index, n, arr[1], m[2], 0, shifts, restored[0], "Θ(n − index)");

            // ---- Linked List
            double[][] lst = timeRuns(() -> {
                LinkedList<Integer> l = buildList(values);
                l.resetCounters();
                prepare();
                long t0 = System.nanoTime();
                for (int i = 0; i < W3_OPS; i++) {
                    l.add(index, extra[i]);
                }
                long tIns = System.nanoTime() - t0;
                m[0] = l.getMovements();
                m[1] = l.getAccesses();
                l.resetCounters();
                long acc = 0;
                long t1 = System.nanoTime();
                for (int i = 0; i < W3_OPS; i++) {
                    acc += l.remove(index);
                }
                long tRem = System.nanoTime() - t1;
                checksum += acc;
                m[2] = l.getMovements();
                m[3] = l.getAccesses();
                restored[0] = sameContents(l, values);
                return new long[]{tIns, tRem};
            });
            String theory = index == 0 ? "Θ(1)" : "Θ(min(index, n−index))";
            recordW3("LinkedList", position, "insert", index, n, lst[0], m[0], m[1], predictedVisits(n, index, true), restored[0], theory);
            recordW3("LinkedList", position, "remove", index, n, lst[1], m[2], m[3], predictedVisits(n, index, false), restored[0], theory);
        }
    }

    /**
     * Nodes the list must visit for m operations at a fixed {@code index}, following the
     * nearest-end rule: reaching position p in a list of size s visits min(p, s−1−p)+1 nodes.
     * Inserts see sizes n..n+m−1, removals see sizes n+m..n+1.
     */
    private static long predictedVisits(int n, int index, boolean insert) {
        long total = 0;
        for (int k = 0; k < W3_OPS; k++) {
            int s = insert ? n + k : n + W3_OPS - k;
            if (insert && index == s) {
                total += 1;                                   // append via tail
            } else {
                total += (index < (s >> 1)) ? index + 1 : s - index;
            }
        }
        return total;
    }

    private static void recordW3(String s, String position, String op, int index, int n, double[] st,
                                 long movements, long visits, long predicted, boolean restored, String theory) {
        if (warmup) {
            return;
        }
        double usPerOp = st[0] * 1e3 / W3_OPS;
        long units = s.equals("DynamicArray") ? movements : visits;
        double nsPerUnit = st[0] * 1e6 / units;
        w3Csv.append(csv(s, position, op, index, n, W3_OPS, st[0], st[1], st[2], st[3], fmt(usPerOp),
                movements, visits, predicted, fmt(nsPerUnit), restored, theory));
        String work = s.equals("DynamicArray") ? fmtN(movements) + " shifts" : fmtN(visits) + " node visits";
        w3Md.append(md(fmtN(n), s, op + " @ " + position + " (i=" + fmtN(index) + ")", ms(st[0]), fmt(usPerOp),
                work, fmtN(predicted), fmt(nsPerUnit), restored ? "yes" : "NO", theory));
        System.out.printf("  W3 %-12s %-6s %-6s avg %10.3f ms  %9.3f us/op  %s%n", s, op, position, st[0], usPerOp, work);
    }

    // ============================================================ Workload 4 — priority processing

    private static void workload4(int n) {
        Random rnd = new Random(SEED);
        Integer[] values = randomValues(rnd, n);

        long[] m = new long[5];
        boolean[] ordered = new boolean[1];
        double[][] st = timeRuns(() -> {
            MinHeap<Integer> h = new MinHeap<>();              // 1. empty heap (default capacity 16)
            prepare();
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {                      // 2. n insertions (incl. amortized growth)
                h.insert(values[i]);
            }
            long tIns = System.nanoTime() - t0;                // 3. total insertion time
            m[0] = h.getComparisons();
            m[1] = h.getMovements();
            h.resetCounters();

            long acc = h.peekMin();                            // peekMin = heap[0], Θ(1) (not timed)
            int[] out = new int[n];
            long t2 = System.nanoTime();
            for (int i = 0; i < n; i++) {                      // 4. n extractions
                out[i] = h.extractMin();
            }
            long tExt = System.nanoTime() - t2;                // 5. total extraction time
            m[2] = h.getComparisons();                         // 6. comparisons
            m[3] = h.getMovements();
            boolean ok = h.isEmpty();                          // 7. non-decreasing order
            for (int i = 1; i < n; i++) {
                ok &= out[i - 1] <= out[i];
                acc += out[i];
            }
            ordered[0] = ok;
            checksum += acc;
            return new long[]{tIns, tExt};
        });

        long insertBound = 0;                                  // Σ floor(log2 k): every insert sifts to the root
        long extractBound = 0;                                 // Σ 2·floor(log2 s): every extract sifts to a leaf
        for (int k = 1; k <= n; k++) {
            insertBound += floorLog2(k);
        }
        for (int s = 1; s < n; s++) {
            extractBound += 2L * floorLog2(s);
        }
        recordW4("insert", n, n, st[0], m[0], m[1], insertBound, ordered[0], "O(log n) worst, Θ(1) expected");
        recordW4("extractMin", n, n, st[1], m[2], m[3], extractBound, ordered[0], "Θ(log n)");
    }

    private static void recordW4(String phase, int n, int ops, double[] st, long comps, long swaps,
                                 long bound, boolean ordered, String theory) {
        if (warmup) {
            return;
        }
        double nsPerOp = st[0] * 1e6 / ops;
        double compsPerOp = (double) comps / ops;
        double perLog = compsPerOp / (Math.log(n) / Math.log(2));
        w4Csv.append(csv(phase, n, ops, st[0], st[1], st[2], st[3], fmt(nsPerOp), comps, fmt(compsPerOp),
                fmt(perLog), swaps, bound, ordered, theory));
        w4Md.append(md(fmtN(n), phase, fmtN(ops), ms(st[0]), fmt(nsPerOp), fmtN(comps), fmt(compsPerOp),
                fmt(perLog), fmtN(bound), ordered ? "yes" : "NO", theory));
        System.out.printf("  W4 %-10s avg %10.3f ms  %8.1f ns/op  comparisons %,d (%.2f/op)  ordered=%s%n",
                phase, st[0], nsPerOp, comps, compsPerOp, ordered);
    }

    // ============================================================ JDK reference (context only)

    /**
     * Same workloads on java.util.ArrayList / LinkedList / PriorityQueue. Not part of the
     * required analysis: it only shows how our constant factors compare with tuned library
     * code (e.g. ArrayList shifts with System.arraycopy instead of an element-by-element loop).
     */
    private static void jdkReference(int n) {
        Random rnd = new Random(SEED);
        Integer[] values = randomValues(rnd, n);
        int[] idx = new int[W1_OPS];
        for (int i = 0; i < W1_OPS; i++) {
            idx[i] = rnd.nextInt(n);
        }
        Random rnd2 = new Random(SEED);
        randomValues(rnd2, n);
        Integer[] keys = new Integer[W2_OPS];
        for (int i = 0; i < W2_OPS; i++) {
            keys[i] = (i % 2 == 0) ? Integer.valueOf(values[rnd2.nextInt(n)].intValue())
                    : Integer.valueOf(-1 - rnd2.nextInt(Integer.MAX_VALUE));
        }
        Random rnd3 = new Random(SEED);                        // same inserted values as workload 3
        randomValues(rnd3, n);
        Integer[] extra = randomValues(rnd3, W3_OPS);

        for (String name : new String[]{"ArrayList", "LinkedList"}) {
            java.util.function.Supplier<List<Integer>> make = () -> {
                List<Integer> l = name.equals("ArrayList") ? new ArrayList<>(n + W3_OPS) : new java.util.LinkedList<>();
                for (Integer v : values) {
                    l.add(v);
                }
                return l;
            };
            double[] g = timeRuns(() -> {
                List<Integer> l = make.get();
                prepare();
                long acc = 0;
                long t0 = System.nanoTime();
                for (int i = 0; i < W1_OPS; i++) {
                    acc += l.get(idx[i]);
                }
                long t = System.nanoTime() - t0;
                checksum += acc;
                return new long[]{t};
            })[0];
            recordJdk("W1", "java.util." + name, "get(i) x10000", n, W1_OPS, g);

            double[] c = timeRuns(() -> {
                List<Integer> l = make.get();
                prepare();
                int hits = 0;
                long t0 = System.nanoTime();
                for (int i = 0; i < W2_OPS; i++) {
                    if (l.contains(keys[i])) {
                        hits++;
                    }
                }
                long t = System.nanoTime() - t0;
                checksum += hits;
                return new long[]{t};
            })[0];
            recordJdk("W2", "java.util." + name, "contains x1000", n, W2_OPS, c);

            for (String position : new String[]{"front", "middle"}) {
                final int index = position.equals("front") ? 0 : n / 2;
                double[][] ir = timeRuns(() -> {
                    List<Integer> l = make.get();
                    prepare();
                    long t0 = System.nanoTime();
                    for (int i = 0; i < W3_OPS; i++) {
                        l.add(index, extra[i]);
                    }
                    long tIns = System.nanoTime() - t0;
                    long acc = 0;
                    long t1 = System.nanoTime();
                    for (int i = 0; i < W3_OPS; i++) {
                        acc += l.remove(index);
                    }
                    long tRem = System.nanoTime() - t1;
                    checksum += acc;
                    return new long[]{tIns, tRem};
                });
                recordJdk("W3", "java.util." + name, "insert @ " + position + " x1000", n, W3_OPS, ir[0]);
                recordJdk("W3", "java.util." + name, "remove @ " + position + " x1000", n, W3_OPS, ir[1]);
            }
        }

        double[][] pq = timeRuns(() -> {
            PriorityQueue<Integer> q = new PriorityQueue<>();
            prepare();
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                q.add(values[i]);
            }
            long tIns = System.nanoTime() - t0;
            long acc = 0;
            long t1 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                acc += q.poll();
            }
            long tExt = System.nanoTime() - t1;
            checksum += acc;
            return new long[]{tIns, tExt};
        });
        recordJdk("W4", "java.util.PriorityQueue", "add x n", n, n, pq[0]);
        recordJdk("W4", "java.util.PriorityQueue", "poll x n", n, n, pq[1]);
    }

    private static void recordJdk(String w, String s, String op, int n, int m, double[] st) {
        if (warmup) {
            return;
        }
        jdkCsv.append(csv(w, s, op, n, m, st[0], st[1], st[2], st[3]));
        jdkMd.append(md(w, fmtN(n), s, op, ms(st[0])));
    }

    // ============================================================ timing machinery

    /** One repetition: performs its own untimed setup and returns the elapsed ns of each timed phase. */
    @FunctionalInterface
    private interface Trial {
        long[] run();
    }

    /**
     * Runs 1 discarded repetition + RUNS measured ones and returns, for every timed phase,
     * {avg, min, max, stddev} in milliseconds.
     */
    private static double[][] timeRuns(Trial trial) {
        trial.run();                                           // discarded (JIT / cache warm-up)
        int reps = warmup ? warmupReps : RUNS;
        long[][] samples = null;
        for (int r = 0; r < reps; r++) {
            long[] t = trial.run();
            if (samples == null) {
                samples = new long[t.length][reps];
            }
            for (int p = 0; p < t.length; p++) {
                samples[p][r] = t[p];
            }
        }
        double[][] out = new double[samples.length][];
        for (int p = 0; p < samples.length; p++) {
            out[p] = stats(samples[p]);
        }
        return out;
    }

    private static double[] stats(long[] ns) {
        double sum = 0;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        for (long v : ns) {
            sum += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double mean = sum / ns.length;
        double sq = 0;
        for (long v : ns) {
            sq += (v - mean) * (v - mean);
        }
        double sd = ns.length > 1 ? Math.sqrt(sq / (ns.length - 1)) : 0;
        return new double[]{mean / 1e6, min / 1e6, max / 1e6, sd / 1e6};
    }

    /** Untimed: collect garbage left by the previous repetition so it is not paid for inside the timer. */
    private static void prepare() {
        System.gc();
    }

    // ============================================================ data helpers

    /** n non-negative random ints (boxed once, before timing). */
    private static Integer[] randomValues(Random rnd, int n) {
        Integer[] v = new Integer[n];
        for (int i = 0; i < n; i++) {
            v[i] = rnd.nextInt(Integer.MAX_VALUE);
        }
        return v;
    }

    private static DynamicArray<Integer> buildArray(Integer[] values, int capacity) {
        DynamicArray<Integer> a = new DynamicArray<>(capacity);
        for (Integer v : values) {
            a.add(v);
        }
        return a;
    }

    private static LinkedList<Integer> buildList(Integer[] values) {
        LinkedList<Integer> l = new LinkedList<>();
        for (Integer v : values) {
            l.add(v);
        }
        return l;
    }

    private static boolean sameContents(Iterable<Integer> list, Integer[] values) {
        int i = 0;
        for (Integer v : list) {
            if (i >= values.length || !v.equals(values[i++])) {
                return false;
            }
        }
        return i == values.length;
    }

    private static int floorLog2(int x) {
        return 31 - Integer.numberOfLeadingZeros(x);
    }

    // ============================================================ output helpers

    private static String summaryMarkdown(List<String> only) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Benchmark summary\n\n");
        sb.append("Generated by `Benchmark.java` (GC: ").append(gcNames()).append("). Times are the mean of ").append(RUNS)
          .append(" measured repetitions (after a JIT warm-up pass and 1 discarded repetition); ")
          .append("seed `Random(42)`; timing with `System.nanoTime()`.\n\n");

        if (only.contains("w1")) {
            sb.append("## Workload 1 — Random access (m = 10,000 get(i))\n\n");
            sb.append("| n | Structure | Avg time (ms) | ns / get | Element accesses | Expected accesses | ns / access | Theory get(i) |\n");
            sb.append("|---:|---|---:|---:|---:|---:|---:|---|\n").append(w1Md).append('\n');
        }
        if (only.contains("w2")) {
            sb.append("## Workload 2 — Search (m = 1,000 contains(x); 500 hits, 500 misses)\n\n");
            sb.append("| n | Structure | Avg time (ms) | µs / search | Hits | Comparisons | Expected comparisons | ns / comparison | Theory contains(x) |\n");
            sb.append("|---:|---|---:|---:|---:|---:|---:|---:|---|\n").append(w2Md).append('\n');
        }
        if (only.contains("w3")) {
            sb.append("## Workload 3 — Insertion and removal (m = 1,000 each, fixed index)\n\n");
            sb.append("| n | Structure | Operation | Avg time (ms) | µs / op | Measured work | Predicted work | ns / unit of work | Restored | Theory per op |\n");
            sb.append("|---:|---|---|---:|---:|---:|---:|---:|---|---|\n").append(w3Md).append('\n');
        }
        if (only.contains("w4")) {
            sb.append("## Workload 4 — Priority processing (Min-Heap)\n\n");
            sb.append("| n | Phase | Ops | Avg time (ms) | ns / op | Comparisons | Comparisons / op | (Comparisons / op) / log2 n | Worst-case bound | Non-decreasing | Theory per op |\n");
            sb.append("|---:|---|---:|---:|---:|---:|---:|---:|---:|---|---|\n").append(w4Md).append('\n');
        }
        if (only.contains("jdk")) {
            sb.append("## JDK reference (context only, same data and protocol)\n\n");
            sb.append("| Workload | n | Structure | Operation | Avg time (ms) |\n");
            sb.append("|---|---:|---|---|---:|\n").append(jdkMd).append('\n');
        }
        return sb.toString();
    }

    private static String gcNames() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getName).collect(Collectors.joining(", "));
    }

    private static String jvmFlags() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                .filter(a -> a.startsWith("-X")).collect(Collectors.joining(" "));
    }

    private static String environment() {
        return "date: " + ZonedDateTime.now() + "\n"
                + "java.version: " + System.getProperty("java.version") + "\n"
                + "java.vm.name: " + System.getProperty("java.vm.name") + "\n"
                + "os: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")\n"
                + "available processors: " + Runtime.getRuntime().availableProcessors() + "\n"
                + "max heap (MB): " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + "\n"
                + "garbage collector: " + gcNames() + "\n"
                + "jvm flags: " + jvmFlags() + "\n"
                + "sizes: 100, 1000, 10000, 100000\n"
                + "runs: " + RUNS + " measured (+1 discarded; warm-up pass " + WARMUP_REPS + " reps at n=1000, 3 at n=10000)\n"
                + "seed: " + SEED + "\n";
    }

    private static String csv(Object... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            Object c = cells[i];
            String s = c instanceof Double ? String.format("%.6f", (Double) c) : String.valueOf(c);
            sb.append(s.contains(",") ? '"' + s + '"' : s);
        }
        return sb.append('\n').toString();
    }

    private static String md(String... cells) {
        return "| " + String.join(" | ", cells) + " |\n";
    }

    private static String ms(double v) {
        if (v >= 100) {
            return String.format("%.1f", v);
        }
        if (v >= 1) {
            return String.format("%.2f", v);
        }
        return String.format("%.4f", v);
    }

    private static String fmt(double v) {
        if (v >= 100) {
            return String.format("%.0f", v);
        }
        if (v >= 10) {
            return String.format("%.1f", v);
        }
        return String.format("%.2f", v);
    }

    private static String fmtN(long v) {
        return String.format("%,d", v);
    }

    private static void write(Path p, String content) throws IOException {
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }
}
