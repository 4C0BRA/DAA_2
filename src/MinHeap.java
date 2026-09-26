import java.util.NoSuchElementException;

/**
 * Binary min-heap stored implicitly in a growable array, written from scratch.
 *
 * <p>Node {@code i} has children {@code 2i+1} and {@code 2i+2} and parent
 * {@code (i-1)/2}. The <b>heap property</b> — {@code heap[parent(c)] <= heap[c]} for every
 * node {@code c > 0} — holds after every public operation, so the minimum is always at
 * index 0.
 *
 * <p>Instrumentation (read by the benchmark):
 * <ul>
 *   <li>{@code comparisons} – calls to {@code compareTo} made by sift-up / sift-down;</li>
 *   <li>{@code movements}   – swaps performed while sifting.</li>
 * </ul>
 *
 * @param <T> element type; must be {@link Comparable}; {@code null} is rejected
 */
public class MinHeap<T extends Comparable<? super T>> {

    private static final int DEFAULT_CAPACITY = 16;

    private Object[] heap;
    private int size;

    private long comparisons;
    private long movements;

    public MinHeap() {
        this(DEFAULT_CAPACITY);
    }

    public MinHeap(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Negative capacity: " + initialCapacity);
        }
        heap = new Object[Math.max(1, initialCapacity)];
    }

    // ------------------------------------------------------------------ operations

    /**
     * Adds {@code x}: place it in the first free leaf, then sift it up.
     * Θ(1) best (x ≥ its parent), Θ(log n) worst (x is a new minimum); amortized over
     * the occasional array doubling.
     */
    public void insert(T x) {
        if (x == null) {
            throw new NullPointerException("MinHeap does not accept null");
        }
        ensureCapacity(size + 1);
        heap[size] = x;
        siftUp(size);
        size++;
    }

    /** Returns (without removing) the minimum element. Θ(1). */
    public T peekMin() {
        if (size == 0) {
            throw new NoSuchElementException("peekMin() on empty heap");
        }
        return elementAt(0);
    }

    /**
     * Removes and returns the minimum: move the last leaf to the root, then sift it down.
     * Θ(log n) worst and average; Θ(1) best (e.g. many equal keys).
     * Correctness of the sift-down loop is proved in README §3.2.
     */
    public T extractMin() {
        if (size == 0) {
            throw new NoSuchElementException("extractMin() on empty heap");
        }
        T min = elementAt(0);
        size--;
        heap[0] = heap[size];
        heap[size] = null;
        if (size > 0) {
            siftDown(0);
        }
        return min;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    // ------------------------------------------------------------------ instrumentation

    public long getComparisons() {
        return comparisons;
    }

    public long getMovements() {
        return movements;
    }

    public void resetCounters() {
        comparisons = 0;
        movements = 0;
    }

    /** Checks the heap property on every parent/child edge. Θ(n); used by tests only. */
    public boolean isValidHeap() {
        for (int c = 1; c < size; c++) {
            if (elementAt((c - 1) >>> 1).compareTo(elementAt(c)) > 0) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    /** Moves heap[i] up while it is smaller than its parent. At most ⌊log2 i⌋ + 1 comparisons. */
    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) >>> 1;
            comparisons++;
            if (elementAt(i).compareTo(elementAt(parent)) >= 0) {
                break;
            }
            swap(i, parent);
            i = parent;
        }
    }

    /**
     * Moves heap[i] down, each time swapping it with its smaller child, until it is no
     * larger than both children or it reaches a leaf. Two comparisons per level.
     */
    private void siftDown(int i) {
        while (true) {
            int left = 2 * i + 1;
            if (left >= size) {
                break;                                   // i is a leaf
            }
            int right = left + 1;
            int smaller = left;
            if (right < size) {
                comparisons++;
                if (elementAt(right).compareTo(elementAt(left)) < 0) {
                    smaller = right;
                }
            }
            comparisons++;
            if (elementAt(smaller).compareTo(elementAt(i)) >= 0) {
                break;                                   // heap[i] <= both children
            }
            swap(i, smaller);
            i = smaller;
        }
    }

    private void swap(int a, int b) {
        Object tmp = heap[a];
        heap[a] = heap[b];
        heap[b] = tmp;
        movements++;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= heap.length) {
            return;
        }
        Object[] bigger = new Object[Math.max(heap.length * 2, minCapacity)];
        for (int i = 0; i < size; i++) {
            bigger[i] = heap[i];
        }
        heap = bigger;
    }

    @SuppressWarnings("unchecked")
    private T elementAt(int i) {
        return (T) heap[i];
    }
}
