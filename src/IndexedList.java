public interface IndexedList<T> extends Iterable<T> {
    void add(T x);

    void add(int index, T x);

    T remove(int index);

    T get(int index);

    boolean contains(Object x);

    int indexOf(Object x);

    int size();

    boolean isEmpty();

    long getAccesses();

    long getComparisons();

    long getMovements();

    void resetCounters();
}
