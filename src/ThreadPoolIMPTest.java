package il.co.ilrd.threadpool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class ThreadPoolIMPTest {

    public static void main(String[] args)throws InterruptedException, ExecutionException {

        ThreadPool threadPool = new ThreadPool(10);
        List<Future<Integer>> future = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            int finalI = i;
            future.add(threadPool.submit(()-> {
            System.out.println("Work" + finalI);
                return finalI+ 15;
                    }));
        }

        System.out.println(future.get(50).cancel(true));
        System.out.println("isCancelled " + future.get(50).isCancelled());

        threadPool.setNumOfThreads(8);

        threadPool.pause();
        Thread.sleep(2000);
        threadPool.resume();

        for (int i = 0; i < 50 ; i++) {
            int finalI = i;
            threadPool.submit(()-> {
                System.out.println("Work after" + finalI);
                Thread.sleep(1000);
                return finalI+10;
            });
        }

        threadPool.shutdown();
        System.out.println("awaitTermination " + threadPool.awaitTermination(3, TimeUnit.SECONDS));
        System.out.println("future " + future.get(25).get());
        System.out.println("finish test");
    }
}