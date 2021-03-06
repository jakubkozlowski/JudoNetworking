package com.github.kubatatami.judonetworking;

import android.os.Build;
import android.os.Process;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Created by Kuba on 19/05/14.
 */
public class JudoExecutor extends ThreadPoolExecutor{

    protected int threadPriority = Process.THREAD_PRIORITY_BACKGROUND;
    protected EndpointImplementation endpoint;
    protected int count;

    protected ThreadFactory threadFactory =  new ThreadFactory() {
        @Override
        public Thread newThread(final Runnable runnable) {
            count++;
            if ((endpoint.getDebugFlags() & Endpoint.THREAD_DEBUG) > 0) {
                LoggerImpl.log("Create thread " + count);
            }

            return new ConnectionThread(runnable,threadPriority,count);
        }
    };

    public JudoExecutor(EndpointImplementation endpoint) {
        super(DefaultConnectionsSizer.DEFAULT_CONNECTIONS, Integer.MAX_VALUE, 30, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());
        this.endpoint=endpoint;
        setThreadFactory(threadFactory);
        prestartAllCoreThreads();
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.GINGERBREAD){
            allowCoreThreadTimeOut(true);
        }
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        if ((endpoint.getDebugFlags() & Endpoint.THREAD_DEBUG) > 0) {
            LoggerImpl.log("Before execute thread " + t.getName()+":" + toString());
        }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);
        if ((endpoint.getDebugFlags() & Endpoint.THREAD_DEBUG) > 0) {
            LoggerImpl.log("After thread execute:" + toString());
        }
    }

    @Override
    public void execute(Runnable command) {
        super.execute(command);
        if ((endpoint.getDebugFlags() & Endpoint.THREAD_DEBUG) > 0) {
            LoggerImpl.log("Execute runnable"+ toString());
        }
    }

    @Override
    public void setCorePoolSize(int corePoolSize) {
        super.setCorePoolSize(corePoolSize);
        if ((endpoint.getDebugFlags() & Endpoint.THREAD_DEBUG) > 0) {
            LoggerImpl.log("Core thread pool size:" + corePoolSize);
        }
    }

    @Override
    public void setMaximumPoolSize(int maximumPoolSize) {
        super.setMaximumPoolSize(maximumPoolSize);
        if ((endpoint.getDebugFlags() & Endpoint.THREAD_DEBUG) > 0) {
            LoggerImpl.log("Maximum thread pool size:" + maximumPoolSize);
        }
    }

    public void setThreadPriority(int threadPriority) {
        this.threadPriority = threadPriority;
    }

    public int getThreadPriority() {
        return threadPriority;
    }



    public static class ConnectionThread extends Thread{

        Runnable runnable;
        int threadPriority;
        Canceller canceller;
        boolean canceled;

        public ConnectionThread(Runnable runnable, int threadPriority, int count) {
            super("JudoNetworking ConnectionPool " + count);
            this.runnable = runnable;
            this.threadPriority = threadPriority;

        }

        @Override
        public void run() {
            android.os.Process.setThreadPriority(threadPriority);
            runnable.run();
        }

        @Override
        public void interrupt() {
            canceled=true;
            super.interrupt();
            if(canceller!=null){
                canceller.cancel();
                canceller=null;
            }
        }

        public void resetCanceled() {
            this.canceled = false;
        }

        public void setCanceller(Canceller canceller) {
            this.canceller = canceller;
        }

        public static interface Canceller{
            public void cancel();
        }

        public boolean isCanceled() {
            return canceled;
        }
    }
}
