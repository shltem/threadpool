// aurter


package il.co.ilrd.threadpool;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class WaitablePQ<E> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Queue<E> priorityQueue;
    private final Condition cond = lock.newCondition();

    public WaitablePQ() {
        this(Integer.MAX_VALUE);
    }

    public WaitablePQ(int capacity) {
        priorityQueue = new PriorityQueue<>(capacity);
    }

    public WaitablePQ(Comparator<? super E> comparator) {
        this(Integer.MAX_VALUE, comparator);
    }

    public WaitablePQ(int capacity, Comparator<? super E> comparator) {
        priorityQueue = new PriorityQueue<>(capacity, comparator);
    }

    public void enqueue(E element) {
        lock.lock();
        priorityQueue.add(element);
        cond.signalAll();
        lock.unlock();
    }

    public E dequeue() {
        lock.lock();
        try {
            while (priorityQueue.isEmpty()) {
                cond.await();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        E toReturn = priorityQueue.poll();
        lock.unlock();
        return toReturn;
    }

    public E dequeue(long timeout, TimeUnit unit) throws TimeoutException {
        E toReturn = null;
        boolean condResult = false;
        try {
            lock.lock();
            if (priorityQueue.isEmpty()) {
                condResult = cond.await(timeout, unit);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (!condResult && priorityQueue.isEmpty()) {
            lock.unlock();
            return null;
        }
        toReturn = priorityQueue.poll();
        lock.unlock();
        return toReturn;
    }


    public E peek() {
        lock.lock();
        E toReturn = priorityQueue.peek();
        lock.unlock();
        return toReturn;
    }

    public int size() {
        lock.lock();
        int size = priorityQueue.size();
        lock.unlock();
        return size;
    }

    public boolean remove(E element) {
        boolean isRemove = false;
        lock.lock();
            if(priorityQueue.remove(element)){
                try {
                 while(priorityQueue.isEmpty()){
                     cond.await();
                 }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                isRemove = true;
            }
        lock.unlock();
        return isRemove;
    }

    public boolean isEmpty() {
        boolean isEmpty = false;
        lock.lock();
        isEmpty = priorityQueue.isEmpty();
        lock.unlock();
        return isEmpty;
        }
    }
