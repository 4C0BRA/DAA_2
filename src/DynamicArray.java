import java.util.Iterator;
import java.util.NoSuchElementException;

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

    @Override
    public void add(T x) {
        ensureCapacity(size + 1);
        data[size++] = x;
        accesses++;
    }

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

    @Override
    public T remove(int index) {
        checkElementIndex(index);
        T removed = elementAt(index);
        for (int j = index; j < size - 1; j++) {
            data[j] = data[j + 1];
        }
        movements += size - 1 - index;
        accesses++;
        data[--size] = null;
        return removed;
    }

    @Override
    public T get(int index) {
        checkElementIndex(index);
        accesses++;
        return elementAt(index);
    }

    @Override
    public boolean contains(Object x) {
        return indexOf(x) >= 0;
    }

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

    public int capacity() {
        return data.length;
    }

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
