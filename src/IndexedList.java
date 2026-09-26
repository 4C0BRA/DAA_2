/**
 * The list ADT shared by {@link DynamicArray} and {@link LinkedList}: the five operations
 * required by the assignment plus size queries. Both implementations expose exactly the
 * same behaviour (including exceptions), so the test-suite runs one set of checks
 * against each of them; only the cost of the operations differs.
 *
 * @param <T> element type
 */
public interface IndexedList<T> extends Iterable<T> {

    /** Appends {@code x} at the end. */
    void add(T x);

    /**
     * Inserts {@code x} so that it ends up at position {@code index}.
     *
     * @throws IndexOutOfBoundsException unless 0 ≤ index ≤ size()
     */
    void add(int index, T x);

    /**
     * Removes and returns the element at {@code index}.
     *
     * @throws IndexOutOfBoundsException unless 0 ≤ index < size()
     */
    T remove(int index);

    /**
     * Returns the element at {@code index}.
     *
     * @throws IndexOutOfBoundsException unless 0 ≤ index < size()
     */
    T get(int index);

    /** {@code true} iff some element equals {@code x} (null-safe). */
    boolean contains(Object x);

    /** Index of the first element equal to {@code x}, or −1. */
    int indexOf(Object x);

    int size();

    boolean isEmpty();

    long getAccesses();

    long getComparisons();

    long getMovements();

    void resetCounters();
}
