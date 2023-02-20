
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

public class ThreadPool implements Executor {
    private final Semaphore pauseSemaphore = new Semaphore(0);
    private final List<ThreadIMP> threads = new LinkedList<>();
    private final WaitablePQ<TaskIMP<?>> taskQueue = new WaitablePQ<>();
    private volatile boolean isShutDown = false;
    private volatile boolean isPuased = false;

    public ThreadPool() {
        addThreads(4);
    }
    public ThreadPool(int numOfThread) {
        addThreads(numOfThread);
    }

    public <V> Future<V> submit(Callable<V> task, Priority priority) {
        if (!isShutDown) {
            TaskIMP<V> newTask = new TaskIMP<>(task, priority);
            taskQueue.enqueue(newTask);
            return newTask.future;
        } else {
            throw new IllegalStateException("ThreadPool is shut down");
        }
    }
    private void addThreads(int numOfThreads) {
        for (int i = 0; i < numOfThreads; i++) {
            ThreadIMP threadIMP = new ThreadIMP();
            threads.add(threadIMP);
            threadIMP.start();
        }
    }

    public <V> Future<V> submit(Callable<V> task) {
        return submit(task, Priority.NORMAL);
    }
    @Override
    public void execute(Runnable task) {
        submit(Executors.callable(task), Priority.NORMAL);
    }
    public Future<?> submit(Runnable task, Priority priority) {
        return submit(Executors.callable(task), priority);
    }

    public <V> Future<V> submit(Runnable task, V returnValue, Priority priority) {
        return submit(Executors.callable(task, returnValue), priority);
    }

    public void shutdown() {
        isShutDown = true;
        for (int i = 0; i < threads.size(); i++) {
            taskQueue.enqueue(new TaskIMP<>(Executors.callable(() -> {
                ((ThreadIMP) Thread.currentThread()).threadExit = true;
            }), -1));
        }
    }

    public void setNumOfThreads(int numOfThreads) {
        if (numOfThreads > threads.size()) {
            int diff = numOfThreads - threads.size();
            if(isPuased){
                insertPhasePiles(diff);
            }
            addThreads(diff);
        } else {
            for (int i = 0; i < (threads.size() - numOfThreads); i++) {
                taskQueue.enqueue(new TaskIMP<>(Executors.callable(() -> {
                    ((ThreadIMP) Thread.currentThread()).threadExit = true;
                    threads.remove(Thread.currentThread());
                }), 15));
            }
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) {
        LocalDateTime endTime = LocalDateTime.now().plusSeconds(TimeUnit.SECONDS.convert(timeout, unit));
        for (int i = 0; i < threads.size(); i++) {
            try {
                long duration = endTime.minusSeconds(LocalDateTime.now().getSecond()).getSecond();
                unit.timedJoin(threads.get(i), duration);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if(LocalDateTime.now().isAfter(endTime)) {
            return false;
        }
        return true;
    }
    public void awaitTermination() {
        for (int i = 0; i < threads.size(); i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
    private void insertPhasePiles(int num){
        for (int i = 0; i < num; i++) {
            taskQueue.enqueue(new TaskIMP<>(Executors.callable(() -> {
                try {
                    pauseSemaphore.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }), 15));
        }
    }
    public void pause() {
        isPuased = true;
        for (int i = 0; i < threads.size(); i++) {
            taskQueue.enqueue(new TaskIMP<>(Executors.callable(() -> {
                try {
                    pauseSemaphore.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }), 15));
        }

    }

    public void resume() {
        isPuased = false;
        pauseSemaphore.release(threads.size());
    }


    private class TaskIMP<V> implements Comparable<TaskIMP<?>> {

        private final Semaphore futureSemaphore = new Semaphore(0);
        private boolean isTaskDone = false;
        private final int priority;
        private final TaskFutureIMP<V> future = new TaskFutureIMP<>();
        private final Callable<V> task;
        private Thread currentThread = null;

        private TaskIMP(Callable<V> task, Priority priority) {
            this(task, priority.ordinal());
        }

        private TaskIMP(Callable<V> task, int priority) {
            this.priority = priority;
            this.task = task;
        }

        private void run() throws Exception {
            currentThread = Thread.currentThread();
            try {
                future.futureTaskValue = task.call();
            }catch (InterruptedException e){
                future.userInterruptedException = e;
            }
            isTaskDone = true;
            futureSemaphore.release();
        }

        @Override
        public int compareTo(TaskIMP<?> other) {
            return other.priority - this.priority;
        }

        public Future<V> getFuture() {
            return future;
        }

        private class TaskFutureIMP<V> implements Future<V> {
            private V futureTaskValue;
            private InterruptedException userInterruptedException;
            private boolean isCanceled = false;

            @Override
            public boolean cancel(boolean sendInterrupted) {
                if (isTaskDone) {
                    isCanceled = false;
                } else {
                    if (taskQueue.remove(TaskIMP.this)) {
                        isCanceled = true;
                    } else if (sendInterrupted) {
                        isCanceled = true;
                        currentThread.interrupt();
                    }
                }
                return isCanceled;
            }

            @Override
            public boolean isCancelled() {
                return isCanceled;
            }

            @Override
            public boolean isDone() {
                return isTaskDone;
            }

            @Override
            public V get() throws InterruptedException {
                futureSemaphore.acquire();
                return futureTaskValue;
            }

            @Override
            public V get(long l, TimeUnit timeUnit) throws InterruptedException, TimeoutException {
                if (isCanceled) throw new CancellationException();
                boolean isDone = futureSemaphore.tryAcquire(l, timeUnit);
                if (isDone) {
                    if (null != futureTaskValue) {
                        return futureTaskValue;
                    }
                    throw userInterruptedException;
                }
                throw new TimeoutException();
            }
        }
    }

    private class ThreadIMP extends Thread {
        private boolean threadExit = false;

        @Override
        public void run() {
            while (!threadExit) {
                try {
                    taskQueue.dequeue().run();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public enum Priority {
        LOW,
        NORMAL,
        HIGH
    }
}





