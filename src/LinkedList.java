import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A doubly linked list with head and tail pointers, written from scratch.
 *
 * <p>Every element lives in its own heap-allocated {@link Node}; nodes are connected by
 * {@code prev}/{@code next} references, so reaching position {@code i} means following
 * pointers. The lookup {@link #node(int)} walks from whichever end is closer, so it
 * visits at most ⌈n/2⌉ nodes — the middle of the list is the most expensive position.
 *
 * <p>Instrumentation (read by the benchmark):
 * <ul>
 *   <li>{@code accesses}    – nodes visited (one per node touched while walking, plus the
 *                             end node used by an O(1) append);</li>
 *   <li>{@code comparisons} – equality tests performed by {@code contains};</li>
 *   <li>{@code movements}   – always 0: a linked list never moves elements, it only
 *                             rewires a constant number of pointers per insert/remove.</li>
 * </ul>
 *
 * @param <T> element type ({@code null} elements are allowed)
 */
public class LinkedList<T> implements IndexedList<T> {

    private static final class Node<T> {
        T value;
        Node<T> prev;
        Node<T> next;

        Node(T value) {
            this.value = value;
        }
    }

    private Node<T> head;
    private Node<T> tail;
    private int size;

    private long accesses;
    private long comparisons;
    private final long movements = 0;

    // ------------------------------------------------------------------ operations

    /** Appends {@code x} at the end using the tail pointer. Θ(1). */
    @Override
    public void add(T x) {
        linkLast(x);
        accesses++;
    }

    /**
     * Inserts {@code x} at position {@code index} (0 ≤ index ≤ size).
     * Locating the position costs Θ(min(index, size − index)); the splice itself is Θ(1).
     */
    @Override
    public void add(int index, T x) {
        checkPositionIndex(index);
        if (index == size) {
            linkLast(x);
            accesses++;
        } else {
            linkBefore(x, node(index));
        }
    }

    /** Removes and returns the element at {@code index}. Θ(min(index, size − 1 − index)). */
    @Override
    public T remove(int index) {
        checkElementIndex(index);
        Node<T> target = node(index);
        unlink(target);
        return target.value;
    }

    /** Returns the element at {@code index}. Θ(min(index, size − 1 − index)): pointer chasing. */
    @Override
    public T get(int index) {
        checkElementIndex(index);
        return node(index).value;
    }

    /** Linear search from the head. Θ(1) best, Θ(n) average and worst (absent). */
    @Override
    public boolean contains(Object x) {
        return indexOf(x) >= 0;
    }

    /** Index of the first occurrence of {@code x}, or −1. Counts one comparison per node inspected. */
    @Override
    public int indexOf(Object x) {
        int i = 0;
        if (x == null) {
            for (Node<T> cur = head; cur != null; cur = cur.next, i++) {
                if (cur.value == null) {
                    comparisons += i + 1;
                    accesses += i + 1;
                    return i;
                }
            }
        } else {
            for (Node<T> cur = head; cur != null; cur = cur.next, i++) {
                if (x.equals(cur.value)) {
                    comparisons += i + 1;
                    accesses += i + 1;
                    return i;
                }
            }
        }
        comparisons += size;
        accesses += size;
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
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Returns the node at {@code index}, walking from the nearer end.
     * Visits {@code min(index, size-1-index) + 1} nodes.
     */
    private Node<T> node(int index) {
        Node<T> cur;
        int steps;
        if (index < (size >> 1)) {
            cur = head;
            for (int i = 0; i < index; i++) {
                cur = cur.next;
            }
            steps = index;
        } else {
            cur = tail;
            for (int i = size - 1; i > index; i--) {
                cur = cur.prev;
            }
            steps = size - 1 - index;
        }
        accesses += steps + 1;
        return cur;
    }

    private void linkLast(T x) {
        Node<T> n = new Node<>(x);
        if (tail == null) {
            head = n;
        } else {
            n.prev = tail;
            tail.next = n;
        }
        tail = n;
        size++;
    }

    /** Splices a new node holding {@code x} immediately before {@code succ}. Θ(1). */
    private void linkBefore(T x, Node<T> succ) {
        Node<T> n = new Node<>(x);
        Node<T> pred = succ.prev;
        n.next = succ;
        n.prev = pred;
        succ.prev = n;
        if (pred == null) {
            head = n;
        } else {
            pred.next = n;
        }
        size++;
    }

    /** Detaches {@code n} from the chain. Θ(1). */
    private void unlink(Node<T> n) {
        Node<T> pred = n.prev;
        Node<T> succ = n.next;
        if (pred == null) {
            head = succ;
        } else {
            pred.next = succ;
        }
        if (succ == null) {
            tail = pred;
        } else {
            succ.prev = pred;
        }
        n.prev = null;
        n.next = null;
        size--;
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

    /**
     * Structural self-check used by the tests: head/tail consistency, prev/next symmetry,
     * and that walking forward and backward both see exactly {@code size} nodes.
     */
    boolean checkInvariants() {
        if (size == 0) {
            return head == null && tail == null;
        }
        if (head == null || tail == null || head.prev != null || tail.next != null) {
            return false;
        }
        int forward = 0;
        Node<T> last = null;
        for (Node<T> cur = head; cur != null; cur = cur.next) {
            if (cur.prev != last) {
                return false;
            }
            last = cur;
            forward++;
            if (forward > size) {
                return false;              // cycle guard
            }
        }
        if (last != tail || forward != size) {
            return false;
        }
        int backward = 0;
        for (Node<T> cur = tail; cur != null; cur = cur.prev) {
            backward++;
            if (backward > size) {
                return false;
            }
        }
        return backward == size;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private Node<T> cursor = head;

            @Override
            public boolean hasNext() {
                return cursor != null;
            }

            @Override
            public T next() {
                if (cursor == null) {
                    throw new NoSuchElementException();
                }
                T v = cursor.value;
                cursor = cursor.next;
                return v;
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (Node<T> cur = head; cur != null; cur = cur.next) {
            if (cur != head) {
                sb.append(", ");
            }
            sb.append(cur.value);
        }
        return sb.append(']').toString();
    }
}
