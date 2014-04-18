package com.github.kubatatami.judonetworking;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.github.kubatatami.judonetworking.exceptions.JudoException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class EndpointImplementation implements Endpoint, EndpointClassic {

    private int maxMobileConnections = 1;
    private int maxWifiConnections = 2;
    private RequestConnector requestConnector;
    private TransportLayer transportLayer;
    private Handler handler = new Handler();
    private Context context;
    private boolean cacheEnabled = false;
    private CacheMode cacheMode = CacheMode.NORMAL;
    private boolean timeProfiler = false;
    private BatchTimeoutMode timeoutMode = BatchTimeoutMode.TIMEOUTS_SUM;
    private MemoryCache memoryCache;
    private DiskCache diskCache;
    private int debugFlags = 0;
    private Map<String, MethodStat> stats;
    private File statFile;
    private float percentLoss;
    private int maxStatFileSize = 50; //KB
    private ErrorLogger errorLogger;
    private Clonner clonner = new ClonnerImplementation();
    private boolean test = false;
    private String testName = null;
    private int testRevision = 0;
    private int delay = 0;
    private String url;
    private ProtocolController protocolController;
    private HashMap<Class, VirtualServerInfo> virtualServers = new HashMap<Class, VirtualServerInfo>();
    private boolean verifyResultModel = false;
    private boolean processingMethod = false;
    private long tokenExpireTimestamp = -1;
    private List<Method> singleCallMethods = new ArrayList<Method>();
    private int id = 0;
    private int threadPriority=Thread.NORM_PRIORITY - 1;

    private ThreadPoolExecutor executorService =
            new ThreadPoolExecutor(2, 30, 30, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setPriority(threadPriority);
                    return thread;
                }
            });


    public EndpointImplementation(Context context, ProtocolController protocolController, TransportLayer transportLayer, String url) {
        init(context, protocolController, transportLayer, url);
    }


    private void init(Context context, ProtocolController protocolController, TransportLayer transportLayer, String url) {
        this.transportLayer = transportLayer;
        this.requestConnector = new RequestConnector(url, this, transportLayer);
        this.context = context;
        this.protocolController = protocolController;
        this.url = url;
        this.statFile = new File(context.getCacheDir(), "stats");
        this.memoryCache = new MemoryCacheImplementation(context);
        this.diskCache = new DiskCacheImplementation(context);
    }

    public HashMap<Class, VirtualServerInfo> getVirtualServers() {
        return virtualServers;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    @Override
    public <T> void registerVirtualServer(Class<T> type, T virtualServer, int delay) {
        virtualServers.put(type, new VirtualServerInfo(virtualServer, delay, delay));
    }

    @Override
    public <T> void registerVirtualServer(Class<T> type, T virtualServer) {
        virtualServers.put(type, new VirtualServerInfo(virtualServer, 0, 0));
    }

    @Override
    public <T> void registerVirtualServer(Class<T> type, T virtualServer, int minDelay, int maxDelay) {
        virtualServers.put(type, new VirtualServerInfo(virtualServer, minDelay, maxDelay));
    }


    @Override
    public <T> void unregisterVirtualServer(Class<T> type) {
        virtualServers.remove(type);
    }

    @Override
    public void setTimeouts(int connectionTimeout, int methodTimeout, int reconnectionAttempts) {
        requestConnector.setConnectTimeout(connectionTimeout);
        requestConnector.setMethodTimeout(methodTimeout);
        requestConnector.setReconnections(reconnectionAttempts);
    }

    @Override
    public void setCallbackThread(boolean alwaysMainThread) {
        if (alwaysMainThread) {
            handler = new Handler(Looper.getMainLooper());
        } else {
            handler = new Handler();
        }
    }


    @Override
    public void setMultiBatchConnections(int maxMobileConnections, int maxWifiConnections) {
        this.maxMobileConnections = maxMobileConnections;
        this.maxWifiConnections = maxWifiConnections;
        int max = Math.max(maxMobileConnections, maxWifiConnections);
        transportLayer.setMaxConnections(max);
        setThreadPoolSize(Math.max(maxMobileConnections, maxWifiConnections));
    }

    protected void setThreadPoolSize(int size) {
        executorService.setCorePoolSize(Math.max(2, size));
    }

    @Override
    public boolean isProcessingMethod() {
        return processingMethod;
    }

    @Override
    public void setProcessingMethod(boolean enabled) {
        this.processingMethod = enabled;
    }

    @SuppressWarnings("unchecked")
    public <T> T getService(Class<T> obj) {
        return getService(obj, new RequestProxy(this, obj, protocolController.getAutoBatchTime() > 0 ? BatchMode.AUTO : BatchMode.NONE, null));
    }

    @SuppressWarnings("unchecked")
    private <T> T getService(Class<T> obj, RequestProxy proxy) {
        return (T) Proxy.newProxyInstance(obj.getClassLoader(), new Class<?>[]{obj}, proxy);
    }

    @SuppressWarnings("unchecked")
    public <T> AsyncResult sendAsyncRequest(String url, String name, CallbackInterface<T> callback, Object... args) {
        return sendAsyncRequest(url, name, new RequestMethodOption(), callback, args);
    }

    @SuppressWarnings("unchecked")
    public <T> AsyncResult sendAsyncRequest(String url, String name, RequestMethodOption requestMethodOption, CallbackInterface<T> callback, Object... args) {

    }

    @Override
    public <T> T sendRequest(String url, RequestMethodOption requestMethodOption, Type returnType, Object... args) throws JudoException {
        Request request = new Request(++id, this, null, name, requestMethodOption, args,
                returnType,
                getRequestConnector().getMethodTimeout(),
                null, getProtocolController().getAdditionalRequestData());
        request.setCustomUrl(url);
        request.setApiKeyRequired(requestMethodOption.apiKeyRequired());
        return (T) getRequestConnector().call(request);
    }

    @Override
    public <T> AsyncResult sendAsyncRequest(String url, RequestMethodOption requestMethodOption, CallbackInterface<T> callback, Object... args) {
        Request request = new Request(
                ++id, this, null,
                name, requestMethodOption, args,
                ((ParameterizedType)callback.getClass().getGenericSuperclass()).getActualTypeArguments()[0], getRequestConnector().getMethodTimeout(),
                (CallbackInterface<Object>) callback, getProtocolController().getAdditionalRequestData());
        request.setCustomUrl(url);
        request.setApiKeyRequired(requestMethodOption.apiKeyRequired());
        try {
            getExecutorService().execute(request);
        } catch (RejectedExecutionException ex) {
            new AsyncResultSender(request, new JudoException("Request queue is full.", ex)).run();
        }
        return request;
    }

        @Override
    @SuppressWarnings("unchecked")
    public <T> AsyncResult callInBatch(Class<T> obj, final Batch<T> batch) {

        if ((getDebugFlags() & REQUEST_LINE_DEBUG) > 0) {
            try {
                StackTraceElement stackTraceElement = RequestProxy.getExternalStacktrace(Thread.currentThread().getStackTrace());
                LoggerImpl.log("Batch starts from " +
                        stackTraceElement.getClassName() +
                        "(" + stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber() + ")");
            } catch (Exception ex) {
                LoggerImpl.log("Can't log stacktrace");
            }
        }

        final RequestProxy pr = new RequestProxy(this, obj, BatchMode.MANUAL, batch);
        T proxy = getService(obj, pr);
        pr.setBatchFatal(true);
        batch.run(proxy);
        pr.setBatchFatal(false);
        batch.runNonFatal(proxy);
        try {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    pr.callBatch();
                }
            });
        } catch (RejectedExecutionException ex) {
            new AsyncResultSender(this, pr, new JudoException("Request queue is full.", ex)).run();
        }
        return pr;
    }

    public TokenCaller getTokenCaller() {
        return protocolController.getTokenCaller();
    }

    public Handler getHandler() {
        return handler;
    }

    public RequestConnector getRequestConnector() {
        return requestConnector;
    }


    public int getMaxMobileConnections() {
        return maxMobileConnections;
    }

    public int getMaxWifiConnections() {
        return maxWifiConnections;
    }

    public int getMaxConnections() {
        return NetworkUtils.isWifi(context) ? getMaxWifiConnections() : getMaxMobileConnections();
    }

    @Override
    public void setBatchTimeoutMode(BatchTimeoutMode mode) {
        this.timeoutMode = mode;
    }

    @Override
    public void setCacheEnabled(boolean enabled) {
        this.cacheEnabled = enabled;
    }

    @Override
    public void setErrorLogger(ErrorLogger logger) {
        this.errorLogger = logger;
    }

    @Override
    public void setCacheMode(CacheMode mode) {
        this.cacheMode = mode;
    }

    @Override
    public void setTimeProfilerEnabled(boolean enabled) {
        this.timeProfiler = enabled;
    }

    public int getMaxStatFileSize() {
        return maxStatFileSize;
    }

    public void setMaxStatFileSize(int maxStatFileSize) {
        this.maxStatFileSize = maxStatFileSize;
    }

    @Override
    public void showTimeProfilerInfo() {
        if (stats != null) {
            for (Map.Entry<String, MethodStat> entry : stats.entrySet()) {
                LoggerImpl.log(entry.getKey() + ":" + entry.getValue());
            }
        }
    }

    @Override
    public void clearTimeProfilerStat() {
        boolean result = statFile.delete();
        stats = Collections.synchronizedMap(new HashMap<String, MethodStat>());
    }


    @Override
    public void startTest(boolean onlyInDebugMode, String name, int revision) {

        String className = context.getApplicationContext().getPackageName() + ".BuildConfig";
        try {
            Class<?> clazz = Class.forName(className);

            Field field = clazz.getDeclaredField("DEBUG");

            Boolean debug = (Boolean) field.get(null);

            if (!onlyInDebugMode || debug) {
                this.test = true;
                this.testName = name;
                this.testRevision = revision;
            }


        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }

    @Override
    public void setVerifyResultModel(boolean enabled) {
        verifyResultModel = enabled;
    }

    boolean isVerifyResultModel() {
        return verifyResultModel;
    }

    String getTestName() {
        return testName;
    }

    int getTestRevision() {
        return testRevision;
    }

    @Override
    public void stopTest() {
        this.test = false;
    }

    public BatchTimeoutMode getTimeoutMode() {
        return timeoutMode;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }


    void setMemoryCache(MemoryCache memoryCache) {
        this.memoryCache = memoryCache;
    }


    public DiskCache getDiskCache() {
        return diskCache;
    }


    public MemoryCache getMemoryCache() {
        return memoryCache;
    }

    @Override
    public void setPercentLoss(float percentLoss) {
        this.percentLoss = percentLoss;
    }

    @Override
    public void setDebugFlags(int flags) {
        this.debugFlags = flags;
        if (memoryCache != null) {
            memoryCache.setDebugFlags(flags);
        }

        if (diskCache != null) {
            diskCache.setDebugFlags(flags);
        }
    }

    @Override
    public void setDelay(int delay) {
        this.delay = delay;
    }

    public int getDelay() {
        return delay;
    }

    public int getDebugFlags() {
        return debugFlags;
    }

    public boolean isTimeProfiler() {
        return timeProfiler;
    }


    @SuppressWarnings("unchecked")
    public Map<String, MethodStat> getStats() {
        if (stats == null) {
            if (statFile.exists() && statFile.length() < maxStatFileSize * 1024) {

                try {
                    FileInputStream fileStream = new FileInputStream(statFile);
                    ObjectInputStream os = new ObjectInputStream(fileStream);
                    stats = (Map<String, MethodStat>) os.readObject();
                    os.close();
                } catch (Exception e) {
                    LoggerImpl.log(e);
                    stats = Collections.synchronizedMap(new HashMap<String, MethodStat>());
                }
            } else {
                stats = Collections.synchronizedMap(new HashMap<String, MethodStat>());
            }
        }
        return stats;
    }

    public void saveStat() {
        try {
            ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(statFile));
            os.writeObject(stats);
            os.flush();
            os.close();
        } catch (IOException e) {
            LoggerImpl.log(e);
        }

    }

    public long getTokenExpireTimestamp() {
        return tokenExpireTimestamp;
    }

    public void setTokenExpireTimestamp(long tokenExpireTimestamp) {
        this.tokenExpireTimestamp = tokenExpireTimestamp;
    }

    CacheMode getCacheMode() {
        return cacheMode;
    }

    public Context getContext() {
        return context;
    }

    public ErrorLogger getErrorLogger() {
        return errorLogger;
    }

    public Clonner getClonner() {
        return clonner;
    }

    public void setClonner(Clonner clonner) {
        this.clonner = clonner;
    }

    public String getUrl() {
        return url;
    }

    boolean isTest() {
        return test;
    }

    public ProtocolController getProtocolController() {
        return protocolController;
    }

    public float getPercentLoss() {
        return percentLoss;
    }

    public List<Method> getSingleCallMethods() {
        return singleCallMethods;
    }

    public int getThreadPriority() {
        return threadPriority;
    }

    public void setThreadPriority(int threadPriority) {
        this.threadPriority = threadPriority;
    }


}
