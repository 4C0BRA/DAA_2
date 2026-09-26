import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A resizable array-backed list, written from scratch (no java.util collection is used
 * for storage). Elements live in one contiguous {@code Object[]}; when it is full the
 * capacity is doubled, which gives amortized Θ(1) appends.
 *
 * <p>The class is instrumented with three counters that the benchmark reads:
 * <ul>
 *   <li>{@code accesses}    – element slots read/written directly by an operation
 *                             (e.g. one per {@code get}), excluding shifts;</li>
 *   <li>{@code comparisons} – equality tests performed by {@code contains};</li>
 *   <li>{@code movements}   – elements copied from one slot to another, i.e. the shifts
 *                             done by {@code add(index,x)} / {@code remove(index)} plus the
 *                             copies done while resizing.</li>
 * </ul>
 * Counters are updated arithmetically (once per operation, not once per loop iteration)
 * so they add almost nothing to the measured time.
 *
 * @param <T> element type ({@code null} elements are allowed)
 */
public class DynamicArray<T> implements IndexedList<T> {

    private static final int DEFAULT_CAPACITY = 8;

    private Object[] data;
    private int size;

    private long accesses;
    private long comparisons;
    private long movements;

    public DynamicArray() {
        this(DEFAULT_CAPACITY);
    }

    public DynamicArray(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Negative capacity: " + initialCapacity);
        }
        data = new Object[Math.max(1, initialCapacity)];
    }

    // ------------------------------------------------------------------ operations

    /** Appends {@code x} at the end. Amortized Θ(1); Θ(n) when a resize happens. */
    @Override
    public void add(T x) {
        ensureCapacity(size + 1);
        data[size++] = x;
        accesses++;
    }

    /**
     * Inserts {@code x} at position {@code index} (0 ≤ index ≤ size), shifting the
     * elements at positions index..size-1 one slot to the right. Θ(size − index).
     *
     * <p>Loop invariant of the shift loop (proved in README §3.1): at the start of every
     * iteration, {@code data[0..j-1]} holds the original elements A[0..j-1] and
     * {@code data[j+1..size]} holds the original elements A[j..size-1].
     */
    @Override
    public void add(int index, T x) {
        checkPositionIndex(index);
        ensureCapacity(size + 1);
        for (int j = size; j > index; j--) {
            data[j] = data[j - 1];
        }
        data[index] = x;
        movements += size - index;
        accesses++;
        size++;
    }

    /**
     * Removes and returns the element at {@code index} (0 ≤ index < size), shifting the
     * elements at positions index+1..size-1 one slot to the left. Θ(size − index).
     */
    @Override
    public T remove(int index) {
        checkElementIndex(index);
        T removed = elementAt(index);
        for (int j = index; j < size - 1; j++) {
            data[j] = data[j + 1];
        }
        movements += size - 1 - index;
        accesses++;
        data[--size] = null;          // drop the stale reference so it can be GC'd
        return removed;
    }

    /** Returns the element at {@code index}. Θ(1): a single address computation. */
    @Override
    public T get(int index) {
        checkElementIndex(index);
        accesses++;
        return elementAt(index);
    }

    /** Linear search. Θ(1) best (first slot), Θ(n) average and worst (absent). */
    @Override
    public boolean contains(Object x) {
        return indexOf(x) >= 0;
    }

    /** Index of the first occurrence of {@code x}, or −1. Counts one comparison per slot inspected. */
    @Override
    public int indexOf(Object x) {
        final Object[] a = data;
        final int n = size;
        if (x == null) {
            for (int i = 0; i < n; i++) {
                if (a[i] == null) {
                    comparisons += i + 1;
                    accesses += i + 1;
                    return i;
                }
            }
        } else {
            for (int i = 0; i < n; i++) {
                if (x.equals(a[i])) {
                    comparisons += i + 1;
                    accesses += i + 1;
                    return i;
                }
            }
        }
        comparisons += n;
        accesses += n;
        return -1;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    /** Current length of the backing array (exposed for tests). */
    public int capacity() {
        return data.length;
    }

    // ------------------------------------------------------------------ instrumentation

    @Override
    public long getAccesses() {
        return accesses;
    }

    @Override
    public long getComparisons() {
        return comparisons;
    }

    @Override
    public long getMovements() {
        return movements;
    }

    @Override
    public void resetCounters() {
        accesses = 0;
        comparisons = 0;
        movements = 0;
    }

    // ------------------------------------------------------------------ helpers

    /** Doubles the backing array until it can hold {@code minCapacity} elements. */
    private void ensureCapacity(int minCapacity) {
        if (minCapacity <= data.length) {
            return;
        }
        int newCapacity = Math.max(data.length * 2, minCapacity);
        Object[] bigger = new Object[newCapacity];
        for (int i = 0; i < size; i++) {
            bigger[i] = data[i];
        }
        movements += size;
        data = bigger;
    }

    @SuppressWarnings("unchecked")
    private T elementAt(int index) {
        return (T) data[index];
    }

    private void checkElementIndex(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    private void checkPositionIndex(int index) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private int cursor = 0;

            @Override
            public boolean hasNext() {
                return cursor < size;
            }

            @Override
            public T next() {
                if (cursor >= size) {
                    throw new NoSuchElementException();
                }
                return elementAt(cursor++);
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(data[i]);
        }
        return sb.append(']').toString();
    }
}
