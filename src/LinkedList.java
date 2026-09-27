import java.util.Iterator;
import java.util.NoSuchElementException;

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

    @Override
    public void add(T x) {
        linkLast(x);
        accesses++;
    }

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

    @Override
    public T remove(int index) {
        checkElementIndex(index);
        Node<T> target = node(index);
        unlink(target);
        return target.value;
    }

    @Override
    public T get(int index) {
        checkElementIndex(index);
        return node(index).value;
    }

    @Override
    public boolean contains(Object x) {
        return indexOf(x) >= 0;
    }

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
                return false;
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
