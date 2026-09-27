import java.util.NoSuchElementException;

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

    public void insert(T x) {
        if (x == null) {
            throw new NullPointerException("MinHeap does not accept null");
        }
        ensureCapacity(size + 1);
        heap[size] = x;
        siftUp(size);
        size++;
    }

    public T peekMin() {
        if (size == 0) {
            throw new NoSuchElementException("peekMin() on empty heap");
        }
        return elementAt(0);
    }

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

    public boolean isValidHeap() {
        for (int c = 1; c < size; c++) {
            if (elementAt((c - 1) >>> 1).compareTo(elementAt(c)) > 0) {
                return false;
            }
        }
        return true;
    }

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

    private void siftDown(int i) {
        while (true) {
            int left = 2 * i + 1;
            if (left >= size) {
                break;
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
                break;
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
