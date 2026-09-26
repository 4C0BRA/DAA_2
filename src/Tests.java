import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Self-contained correctness suite (no external test framework needed):
 *
 * <pre>
 *   javac -d out src/*.java
 *   java -cp out Tests
 * </pre>
 *
 * Covers, for every structure: empty structure, one element, multiple elements,
 * duplicates, boundary indices, invalid indices and large inputs. The two lists are
 * checked against {@link java.util.ArrayList} / {@link java.util.LinkedList}; the heap is
 * checked against {@link java.util.PriorityQueue}, and its heap property is verified
 * after every insertion and every extraction. Exits with status 1 if any check fails.
 */
public class Tests {

    private static int passed;
    private static int failed;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        long start = System.nanoTime();

        runListSuite("DynamicArray", DynamicArray::new);
        runListSuite("LinkedList", LinkedList::new);
        dynamicArraySpecific();
        linkedListSpecific();
        runHeapSuite();

        double ms = (System.nanoTime() - start) / 1e6;
        System.out.println();
        System.out.printf("Passed: %d   Failed: %d   (%.0f ms)%n", passed, failed, ms);
        if (failed > 0) {
            failures.forEach(f -> System.out.println("  FAILED: " + f));
            System.exit(1);
        }
        System.out.println("ALL TESTS PASSED");
    }

    // ====================================================================== list suite

    private static void runListSuite(String name, Supplier<IndexedList<Integer>> factory) {
        System.out.println("== " + name);

        // ---- empty structure
        IndexedList<Integer> l = factory.get();
        check(l.size() == 0 && l.isEmpty(), name + " empty: size 0 / isEmpty");
        check(!l.contains(1), name + " empty: contains false");
        check(l.indexOf(1) == -1, name + " empty: indexOf -1");
        check(!l.iterator().hasNext(), name + " empty: iterator has no elements");
        final IndexedList<Integer> empty = l;
        expectThrows(IndexOutOfBoundsException.class, () -> empty.get(0), name + " empty: get(0) throws");
        expectThrows(IndexOutOfBoundsException.class, () -> empty.remove(0), name + " empty: remove(0) throws");
        expectThrows(IndexOutOfBoundsException.class, () -> empty.add(1, 5), name + " empty: add(1,x) throws");
        l.add(0, 42);
        check(l.size() == 1 && l.get(0) == 42, name + " empty: add(0,x) is allowed");

        // ---- one element
        l = factory.get();
        l.add(7);
        check(l.size() == 1 && !l.isEmpty(), name + " one: size 1");
        check(l.get(0) == 7, name + " one: get(0)");
        check(l.contains(7) && !l.contains(8), name + " one: contains");
        final IndexedList<Integer> one = l;
        expectThrows(IndexOutOfBoundsException.class, () -> one.get(1), name + " one: get(1) throws");
        check(l.remove(0) == 7 && l.isEmpty(), name + " one: remove(0) empties the list");
        check(!l.contains(7), name + " one: removed value no longer contained");
        l.add(1);
        l.add(0, 0);
        check(sameAs(l, List.of(0, 1)), name + " one: add(0,x) before single element");

        // ---- multiple elements (checked against java.util.ArrayList after every step)
        l = factory.get();
        List<Integer> ref = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            l.add(i);
            ref.add(i);
        }
        check(sameAs(l, ref), name + " multi: 10 appends in order");
        l.add(0, -1);       ref.add(0, -1);
        check(sameAs(l, ref), name + " multi: insert at front");
        l.add(l.size(), 100); ref.add(ref.size(), 100);
        check(sameAs(l, ref), name + " multi: insert at end (index == size)");
        l.add(5, 55);       ref.add(5, 55);
        check(sameAs(l, ref), name + " multi: insert in middle");
        check(l.remove(5).equals(ref.remove(5)) && sameAs(l, ref), name + " multi: remove middle");
        check(l.remove(0).equals(ref.remove(0)) && sameAs(l, ref), name + " multi: remove front");
        check(l.remove(l.size() - 1).equals(ref.remove(ref.size() - 1)) && sameAs(l, ref), name + " multi: remove last");
        boolean getsMatch = true;
        for (int i = 0; i < ref.size(); i++) {
            getsMatch &= l.get(i).equals(ref.get(i));
        }
        check(getsMatch, name + " multi: get(i) matches reference for every i");
        check(l.contains(9) && !l.contains(55) && !l.contains(-1), name + " multi: contains after edits");

        // ---- duplicate values
        l = factory.get();
        for (int v : new int[]{3, 3, 1, 3, 2, 3}) {
            l.add(v);
        }
        check(l.contains(3) && l.indexOf(3) == 0, name + " dup: first occurrence is found");
        check(l.indexOf(2) == 4, name + " dup: indexOf skips earlier different values");
        l.remove(0);
        check(sameAs(l, List.of(3, 1, 3, 2, 3)), name + " dup: removing one copy keeps the others");
        while (l.contains(3)) {
            l.remove(l.indexOf(3));
        }
        check(sameAs(l, List.of(1, 2)) && !l.contains(3), name + " dup: all copies removable");

        // ---- null elements are allowed and searchable
        l = factory.get();
        l.add(1);
        l.add(null);
        l.add(2);
        check(l.contains(null) && l.indexOf(null) == 1 && l.get(1) == null, name + " null element stored and found");

        // ---- boundary indices
        l = factory.get();
        for (int i = 0; i < 5; i++) {
            l.add(i * 10);                           // [0,10,20,30,40]
        }
        check(l.get(0) == 0 && l.get(4) == 40, name + " boundary: get(0), get(size-1)");
        l.add(0, -5);
        l.add(l.size(), 50);
        check(sameAs(l, List.of(-5, 0, 10, 20, 30, 40, 50)), name + " boundary: add(0), add(size)");
        check(l.remove(0) == -5 && l.remove(l.size() - 1) == 50, name + " boundary: remove(0), remove(size-1)");
        check(sameAs(l, List.of(0, 10, 20, 30, 40)), name + " boundary: contents after boundary removals");

        // ---- invalid indices: exception + structure unchanged
        final IndexedList<Integer> b = l;
        expectThrows(IndexOutOfBoundsException.class, () -> b.get(-1), name + " invalid: get(-1)");
        expectThrows(IndexOutOfBoundsException.class, () -> b.get(b.size()), name + " invalid: get(size)");
        expectThrows(IndexOutOfBoundsException.class, () -> b.remove(-1), name + " invalid: remove(-1)");
        expectThrows(IndexOutOfBoundsException.class, () -> b.remove(b.size()), name + " invalid: remove(size)");
        expectThrows(IndexOutOfBoundsException.class, () -> b.add(-1, 9), name + " invalid: add(-1,x)");
        expectThrows(IndexOutOfBoundsException.class, () -> b.add(b.size() + 1, 9), name + " invalid: add(size+1,x)");
        check(sameAs(l, List.of(0, 10, 20, 30, 40)), name + " invalid: failed calls leave the list unchanged");

        // ---- large input: 100 000 appends, then front/middle/back access
        l = factory.get();
        int big = 100_000;
        for (int i = 0; i < big; i++) {
            l.add(i);
        }
        check(l.size() == big, name + " large: size after 100k appends");
        check(l.get(0) == 0 && l.get(big / 2) == big / 2 && l.get(big - 1) == big - 1, name + " large: get front/middle/back");
        check(l.contains(big - 1) && !l.contains(big), name + " large: contains last / absent");
        int expected = 0;
        boolean inOrder = true;
        for (int v : l) {
            inOrder &= (v == expected++);
        }
        check(inOrder && expected == big, name + " large: iteration yields 0..n-1 in order");
        for (int i = 0; i < 1000; i++) {
            l.remove(0);
        }
        check(l.size() == big - 1000 && l.get(0) == 1000, name + " large: 1000 front removals");

        // ---- randomized differential test against the JDK
        differentialListTest(name, factory.get(), name.equals("LinkedList")
                ? new java.util.LinkedList<>() : new ArrayList<>());
    }

    /** 60 000 random operations applied to both our list and a JDK list; results must agree. */
    private static void differentialListTest(String name, IndexedList<Integer> ours, List<Integer> jdk) {
        Random rnd = new Random(42);
        boolean ok = true;
        String firstMismatch = null;
        for (int step = 0; step < 60_000 && ok; step++) {
            int op = rnd.nextInt(10);
            int n = jdk.size();
            if (op < 3 || n == 0) {                          // add(x)
                int v = rnd.nextInt(500);
                ours.add(v);
                jdk.add(v);
            } else if (op < 5) {                             // add(index, x), index in [0, n]
                int idx = rnd.nextInt(n + 1);
                int v = rnd.nextInt(500);
                ours.add(idx, v);
                jdk.add(idx, v);
            } else if (op < 7) {                             // remove(index)
                int idx = rnd.nextInt(n);
                ok = ours.remove(idx).equals(jdk.remove(idx));
            } else if (op < 9) {                             // get(index)
                int idx = rnd.nextInt(n);
                ok = ours.get(idx).equals(jdk.get(idx));
            } else {                                         // contains / indexOf
                int v = rnd.nextInt(600);
                ok = ours.contains(v) == jdk.contains(v) && ours.indexOf(v) == jdk.indexOf(v);
            }
            if (!ok) {
                firstMismatch = "step " + step + ", op " + op;
            }
            if (ok && step % 2_000 == 0) {
                ok = ours.size() == jdk.size() && sameAs(ours, jdk);
                if (!ok) {
                    firstMismatch = "contents differ at step " + step;
                }
                if (ok && ours instanceof LinkedList) {
                    ok = ((LinkedList<Integer>) ours).checkInvariants();
                    if (!ok) {
                        firstMismatch = "link invariants broken at step " + step;
                    }
                }
            }
        }
        ok = ok && sameAs(ours, jdk);
        check(ok, name + " differential: 60k random ops agree with " + jdk.getClass().getSimpleName()
                + (firstMismatch == null ? "" : " (" + firstMismatch + ")"));
    }

    private static void dynamicArraySpecific() {
        System.out.println("== DynamicArray (growth & counters)");
        DynamicArray<Integer> a = new DynamicArray<>(2);
        check(a.capacity() == 2, "DynamicArray: initial capacity respected");
        a.add(1);
        a.add(2);
        a.add(3);
        check(a.capacity() == 4 && a.size() == 3, "DynamicArray: capacity doubles when full");
        for (int i = 0; i < 1000; i++) {
            a.add(i);
        }
        check(a.capacity() >= a.size() && a.capacity() < 2 * a.size() + 2, "DynamicArray: capacity stays within 2x of size");
        expectThrows(IllegalArgumentException.class, () -> new DynamicArray<Integer>(-1), "DynamicArray: negative capacity rejected");

        DynamicArray<Integer> c = new DynamicArray<>(64);
        for (int i = 0; i < 10; i++) {
            c.add(i);
        }
        c.resetCounters();
        c.add(0, 99);
        check(c.getMovements() == 10, "DynamicArray counters: add(0,x) on 10 elements moves 10");
        c.resetCounters();
        c.add(c.size(), 98);
        check(c.getMovements() == 0, "DynamicArray counters: add(size,x) moves nothing");
        c.resetCounters();
        int before = c.size();                               // 12 elements now
        c.remove(0);
        check(c.getMovements() == before - 1, "DynamicArray counters: remove(0) on n elements moves n-1");
        c.resetCounters();
        c.remove(c.size() - 1);
        check(c.getMovements() == 0, "DynamicArray counters: remove(size-1) moves nothing");
        c.resetCounters();
        c.get(5);
        check(c.getAccesses() == 1, "DynamicArray counters: get is one access");
        c.resetCounters();
        c.contains(-7);
        check(c.getComparisons() == c.size(), "DynamicArray counters: unsuccessful search compares all n");
        c.resetCounters();
        c.contains(0);
        check(c.getComparisons() == 1, "DynamicArray counters: hit at index 0 costs one comparison");
    }

    private static void linkedListSpecific() {
        System.out.println("== LinkedList (links & counters)");
        LinkedList<Integer> l = new LinkedList<>();
        check(l.checkInvariants(), "LinkedList: empty list invariants");
        for (int i = 0; i < 10; i++) {
            l.add(i);
        }
        check(l.checkInvariants(), "LinkedList: invariants after appends");
        l.add(0, -1);
        l.add(5, 50);
        l.remove(l.size() - 1);
        l.remove(0);
        check(l.checkInvariants(), "LinkedList: invariants after front/middle/back edits");
        while (!l.isEmpty()) {
            l.remove(l.size() / 2);
        }
        check(l.checkInvariants(), "LinkedList: invariants after draining from the middle");

        for (int i = 0; i < 10; i++) {
            l.add(i);
        }
        l.resetCounters();
        l.get(0);
        check(l.getAccesses() == 1, "LinkedList counters: get(0) visits 1 node");
        l.resetCounters();
        l.get(9);
        check(l.getAccesses() == 1, "LinkedList counters: get(size-1) visits 1 node (from tail)");
        l.resetCounters();
        l.get(4);
        check(l.getAccesses() == 5, "LinkedList counters: get(4) of 10 walks from head, visits 5");
        l.resetCounters();
        l.get(5);
        check(l.getAccesses() == 5, "LinkedList counters: get(5) of 10 walks from tail, visits 5");
        l.resetCounters();
        l.add(0, 100);
        check(l.getAccesses() == 1 && l.getMovements() == 0, "LinkedList counters: add(0,x) touches 1 node, moves nothing");
        l.resetCounters();
        l.contains(-3);
        check(l.getComparisons() == l.size(), "LinkedList counters: unsuccessful search compares all n");
    }

    // ====================================================================== heap suite

    private static void runHeapSuite() {
        System.out.println("== MinHeap");

        // ---- empty
        MinHeap<Integer> h = new MinHeap<>();
        check(h.isEmpty() && h.size() == 0 && h.isValidHeap(), "heap empty: size 0, valid");
        final MinHeap<Integer> e = h;
        expectThrows(NoSuchElementException.class, e::peekMin, "heap empty: peekMin throws");
        expectThrows(NoSuchElementException.class, e::extractMin, "heap empty: extractMin throws");
        expectThrows(NullPointerException.class, () -> e.insert(null), "heap: null rejected");
        expectThrows(IllegalArgumentException.class, () -> new MinHeap<Integer>(-1), "heap: negative capacity rejected");

        // ---- one element
        h.insert(5);
        check(h.size() == 1 && h.peekMin() == 5 && h.size() == 1, "heap one: peekMin does not remove");
        check(h.extractMin() == 5 && h.isEmpty(), "heap one: extractMin empties");
        expectThrows(NoSuchElementException.class, e::extractMin, "heap one: extract after drain throws");

        // ---- multiple elements, property after every insertion and extraction
        h = new MinHeap<>(1);                                     // forces several resizes
        int[] vals = {5, 3, 8, 1, 9, 2, 7, 4, 6, 0};
        boolean validAfterInsert = true;
        for (int v : vals) {
            h.insert(v);
            validAfterInsert &= h.isValidHeap();
        }
        check(validAfterInsert, "heap multi: heap property after every insertion");
        check(h.peekMin() == 0, "heap multi: peekMin is the minimum");
        List<Integer> out = new ArrayList<>();
        boolean validAfterExtract = true;
        while (!h.isEmpty()) {
            out.add(h.extractMin());
            validAfterExtract &= h.isValidHeap();
        }
        check(validAfterExtract, "heap multi: heap property after every extraction");
        check(out.equals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)), "heap multi: extraction is sorted");

        // ---- duplicates, sorted, reverse-sorted and all-equal inputs
        heapOrderCase("duplicates", new int[]{4, 1, 4, 1, 4, 1, 2, 2, 9, 9, 0, 0});
        heapOrderCase("ascending", range(0, 500, 1));
        heapOrderCase("descending (every insert sifts to root)", range(500, 0, -1));
        heapOrderCase("all equal", filled(300, 7));
        heapOrderCase("negative & extreme values",
                new int[]{Integer.MAX_VALUE, -1, Integer.MIN_VALUE, 0, 1, Integer.MIN_VALUE, Integer.MAX_VALUE});

        // ---- property after EVERY operation on a mid-size random input
        Random rnd = new Random(42);
        h = new MinHeap<>();
        boolean everyOp = true;
        for (int i = 0; i < 2_000; i++) {
            h.insert(rnd.nextInt(1000));
            everyOp &= h.isValidHeap();
        }
        int prev = Integer.MIN_VALUE;
        boolean nonDecreasing = true;
        while (!h.isEmpty()) {
            int v = h.extractMin();
            nonDecreasing &= v >= prev;
            prev = v;
            everyOp &= h.isValidHeap();
        }
        check(everyOp, "heap random 2k: property holds after each of 4000 operations");
        check(nonDecreasing, "heap random 2k: extracted values non-decreasing");

        // ---- large input vs PriorityQueue
        int big = 100_000;
        h = new MinHeap<>();
        PriorityQueue<Integer> pq = new PriorityQueue<>();
        rnd = new Random(42);
        for (int i = 0; i < big; i++) {
            int v = rnd.nextInt();
            h.insert(v);
            pq.add(v);
        }
        check(h.size() == big && h.isValidHeap() && h.peekMin().equals(pq.peek()), "heap large: 100k inserts valid, same min as PriorityQueue");
        boolean samePolls = true;
        nonDecreasing = true;
        prev = Integer.MIN_VALUE;
        for (int i = 0; i < big; i++) {
            int v = h.extractMin();
            samePolls &= pq.poll() == v;
            nonDecreasing &= v >= prev;
            prev = v;
        }
        check(samePolls && h.isEmpty(), "heap large: 100k extractions identical to PriorityQueue.poll()");
        check(nonDecreasing, "heap large: 100k extractions non-decreasing");

        // ---- interleaved random ops vs PriorityQueue
        h = new MinHeap<>();
        pq = new PriorityQueue<>();
        rnd = new Random(7);
        boolean agree = true;
        for (int step = 0; step < 100_000 && agree; step++) {
            int op = rnd.nextInt(10);
            if (op < 5 || pq.isEmpty()) {
                int v = rnd.nextInt(10_000) - 5_000;
                h.insert(v);
                pq.add(v);
            } else if (op < 8) {
                agree = h.extractMin().equals(pq.poll());
            } else {
                agree = h.peekMin().equals(pq.peek());
            }
            agree &= h.size() == pq.size();
            if (step % 1_000 == 0) {
                agree &= h.isValidHeap();
            }
        }
        check(agree, "heap differential: 100k interleaved insert/extract/peek agree with PriorityQueue");

        // ---- comparison counters behave as the analysis predicts
        h = new MinHeap<>();
        for (int i = 0; i < 1024; i++) {
            h.insert(i);                                          // ascending: never sifts
        }
        check(h.getComparisons() == 1023 && h.getMovements() == 0, "heap counters: ascending inserts cost exactly 1 comparison each (best case)");
        h = new MinHeap<>();
        long worst = 0;
        for (int i = 1023; i >= 0; i--) {
            h.insert(i);                                          // descending: sifts to root
        }
        for (int k = 2; k <= 1024; k++) {
            worst += 31 - Integer.numberOfLeadingZeros(k);        // depth of node k-1 = floor(log2 k)
        }
        check(h.getComparisons() == worst, "heap counters: descending inserts cost floor(log2 k) comparisons each (worst case)");
        h.resetCounters();
        h.extractMin();
        check(h.getComparisons() <= 2L * 9, "heap counters: one extractMin on 1023 remaining elements uses <= 2*floor(log2 n) = 18 comparisons");
    }

    private static void heapOrderCase(String label, int[] input) {
        MinHeap<Integer> h = new MinHeap<>();
        PriorityQueue<Integer> pq = new PriorityQueue<>();
        boolean valid = true;
        for (int v : input) {
            h.insert(v);
            pq.add(v);
            valid &= h.isValidHeap();
        }
        int[] sorted = input.clone();
        Arrays.sort(sorted);
        boolean matches = true;
        for (int expected : sorted) {
            int got = h.extractMin();
            matches &= got == expected && pq.poll() == got;
            valid &= h.isValidHeap();
        }
        check(valid && matches && h.isEmpty(), "heap " + label + ": valid after each op, output == sorted input");
    }

    // ====================================================================== helpers

    private static boolean sameAs(Iterable<Integer> ours, List<Integer> expected) {
        Iterator<Integer> it = ours.iterator();
        for (Integer e : expected) {
            if (!it.hasNext()) {
                return false;
            }
            Integer v = it.next();
            if (e == null ? v != null : !e.equals(v)) {
                return false;
            }
        }
        return !it.hasNext();
    }

    private static int[] range(int from, int to, int step) {
        int n = Math.abs(to - from) / Math.abs(step);
        int[] r = new int[n];
        for (int i = 0; i < n; i++) {
            r[i] = from + i * step;
        }
        return r;
    }

    private static int[] filled(int n, int v) {
        int[] r = new int[n];
        Arrays.fill(r, v);
        return r;
    }

    private static void check(boolean condition, String name) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            failures.add(name);
            System.out.println("  FAIL  " + name);
        }
    }

    private static void expectThrows(Class<? extends Throwable> type, Runnable action, String name) {
        try {
            action.run();
            check(false, name + " (no exception)");
        } catch (Throwable t) {
            check(type.isInstance(t), name + (type.isInstance(t) ? "" : " (got " + t + ")"));
        }
    }
}
