package com.github.kubatatami.judonetworking;

import android.util.Pair;

import com.github.kubatatami.judonetworking.exceptions.CancelException;
import com.github.kubatatami.judonetworking.exceptions.ConnectionException;
import com.github.kubatatami.judonetworking.exceptions.JudoException;
import com.github.kubatatami.judonetworking.exceptions.ParseException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

class RequestProxy implements InvocationHandler, AsyncResult {

    protected final EndpointImplementation rpc;
    protected int id = 0;
    protected boolean batchEnabled = false;
    protected boolean batchFatal = true;
    protected final List<Request> batchRequests = new ArrayList<Request>();
    protected BatchMode mode = BatchMode.NONE;
    protected boolean cancelled, done, running;
    protected BatchInterface<?> batchCallback;

    public RequestProxy(EndpointImplementation rpc, BatchMode mode, BatchInterface<?> batchCallback) {
        this.rpc = rpc;
        this.mode = mode;
        this.batchCallback = batchCallback;
        batchEnabled = (mode == BatchMode.MANUAL);
    }

    protected final Runnable batchRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                Thread.sleep(rpc.getProtocolController().getAutoBatchTime());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (batchRequests.size() == 1) {
                Request request;
                synchronized (batchRequests) {
                    request = batchRequests.get(0);
                    batchRequests.clear();
                    batchEnabled = false;
                }
                Future<?> future = rpc.getExecutorService().submit(request);
                request.setFuture(future);
            } else {
                callBatch();
            }
        }
    };

    public void setBatchFatal(boolean batchFatal) {
        this.batchFatal = batchFatal;
    }


    public static String createMethodName(Method method, RequestMethod ann) {
        NamePrefix namePrefix = ReflectionCache.getAnnotation(method.getDeclaringClass(), NamePrefix.class);
        NameSuffix nameSuffix = ReflectionCache.getAnnotation(method.getDeclaringClass(), NameSuffix.class);
        String name;
        if (ann != null && !("".equals(ann.name()))) {
            name = ann.name();
        } else {
            name = method.getName();
        }
        if (namePrefix != null) {
            name = namePrefix.value() + name;
        }
        if (nameSuffix != null) {
            name += nameSuffix.value();
        }
        return name;
    }

    public static StackTraceElement getExternalStacktrace(StackTraceElement[] stackTrace) {
        String packageName = RequestProxy.class.getPackage().getName();
        boolean current = false;
        for (StackTraceElement element : stackTrace) {
            if (!current && element.getClassName().contains(packageName)) {
                current = true;
            } else if (current && !element.getClassName().contains(packageName) && !element.getClassName().contains("$Proxy")) {
                return element;
            }
        }
        return stackTrace[0];
    }

    protected AsyncResult performAsyncRequest(Request request) throws Exception {

        synchronized (batchRequests) {
            if (batchEnabled) {
                request.setBatchFatal(batchFatal);
                batchRequests.add(request);
                batchEnabled = true;
                return null;
            } else {
                if (mode == BatchMode.AUTO) {
                    batchRequests.add(request);
                    batchEnabled = true;
                    rpc.getExecutorService().execute(batchRunnable);
                } else {
                    Future<?> future = rpc.getExecutorService().submit(request);
                    request.setFuture(future);
                }
                return request;
            }

        }
    }


    @Override
    public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
        Request request = null;
        try {
            RequestMethod ann = ReflectionCache.getAnnotation(m, RequestMethod.class);
            if (ann != null) {
                String name = createMethodName(m, ann);
                int timeout = rpc.getRequestConnector().getMethodTimeout();


                if ((rpc.getDebugFlags() & Endpoint.REQUEST_LINE_DEBUG) > 0) {
                    try {
                        StackTraceElement stackTraceElement = getExternalStacktrace(Thread.currentThread().getStackTrace());
                        if (batchEnabled && mode == BatchMode.MANUAL) {
                            LoggerImpl.log("In batch request " + name + " from " +
                                    stackTraceElement.getClassName() +
                                    "(" + stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber() + ")");
                        } else {
                            LoggerImpl.log("Request " + name + " from " +
                                    stackTraceElement.getClassName() +
                                    "(" + stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber() + ")");
                        }
                    } catch (Exception ex) {
                        LoggerImpl.log("Can't log stacktrace");
                    }
                }

                if (ann.timeout() != 0) {
                    timeout = ann.timeout();
                }


                if (!ann.async()) {
                    request = new Request(getNextId(), rpc, m, name, ann, args, m.getReturnType(),
                            timeout, null, rpc.getProtocolController().getAdditionalRequestData());
                    ann.modifier().newInstance().modify(request);
                    rpc.filterNullArgs(request);
                    if (request.getSingleCall() != null) {
                        throw new JudoException("SingleCall is not supported on no async method.");
                    }
                    return rpc.getRequestConnector().call(request);
                } else {
                    request = callAsync(getNextId(), m, name, args, ReflectionCache.getGenericParameterTypes(m), timeout, ann);
                    rpc.filterNullArgs(request);
                    if (request.getSingleCall() != null) {
                        if (rpc.getSingleCallMethods().containsKey(CacheMethod.getMethodId(m))) {
                            SingleMode mode = request.getSingleCall().mode();

                            if (mode == SingleMode.CANCEL_NEW) {
                                if ((rpc.getDebugFlags() & Endpoint.REQUEST_LINE_DEBUG) > 0) {
                                    LoggerImpl.log("Request " + name + " rejected - SingleCall.");
                                }
                                request.cancel();
                                return request;
                            }
                            if (mode == SingleMode.CANCEL_OLD) {
                                Request oldRequest = rpc.getSingleCallMethods().get(CacheMethod.getMethodId(m));
                                if ((rpc.getDebugFlags() & Endpoint.REQUEST_LINE_DEBUG) > 0) {
                                    LoggerImpl.log("Request " + oldRequest.getName() + " rejected - SingleCall.");
                                }
                                oldRequest.cancel();
                                synchronized (rpc.getSingleCallMethods()) {
                                    rpc.getSingleCallMethods().put(request.getMethodId(), request);
                                }
                            }
                        } else {
                            synchronized (rpc.getSingleCallMethods()) {
                                rpc.getSingleCallMethods().put(request.getMethodId(), request);
                            }
                        }
                    }
                    performAsyncRequest(request);
                    return request;
                }
            } else {
                try {
                    return m.invoke(this, args);
                } catch (IllegalArgumentException e) {
                    throw new JudoException("No @RequestMethod on " + m.getName());
                }
            }
        } catch (final JudoException e) {
            if (rpc.getErrorLoggers().size() ==0 && !(e instanceof CancelException)) {
                final Request finalRequest = request;
                rpc.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        for(ErrorLogger errorLogger : rpc.getErrorLoggers()) {
                            errorLogger.onError(e, finalRequest);
                        }
                    }
                });
            }
            throw e;
        }
    }

    protected synchronized int getNextId() {
        return ++id;
    }

    @SuppressWarnings("unchecked")
    protected Request callAsync(int id, Method m, String name, Object[] args, Type[] types, int timeout, RequestMethod ann) throws Exception {
        Object[] newArgs;
        CallbackInterface<Object> callback = null;
        Type returnType = Void.class;
        if (args.length > 0 && args[args.length - 1] instanceof CallbackInterface) {
            callback = (CallbackInterface<Object>) args[args.length - 1];
            returnType = ((ParameterizedType) types[args.length - 1]).getActualTypeArguments()[0];
        } else {
            Type[] genericTypes = m.getGenericParameterTypes();
            if (genericTypes.length > 0 && genericTypes[genericTypes.length - 1] instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericTypes[genericTypes.length - 1];
                if (parameterizedType.getRawType().equals(CallbackInterface.class)) {
                    returnType = parameterizedType.getActualTypeArguments()[0];
                }
            }
        }
        if (args.length > 1) {
            newArgs = new Object[args.length - 1];
            System.arraycopy(args, 0, newArgs, 0, args.length - 1);
        } else {
            newArgs = null;
        }

        Request request = new Request(id, rpc, m, name, ann, newArgs, returnType, timeout, callback, rpc.getProtocolController().getAdditionalRequestData());
        ann.modifier().newInstance().modify(request);
        return request;
    }


    public void callBatch() {
        List<Request> batches;
        synchronized (batchRequests) {
            for (int i = batchRequests.size() - 1; i >= 0; i--) {
                if (batchRequests.get(i).isCancelled()) {
                    batchRequests.remove(i);
                }
            }
        }
        if (batchRequests.size() > 0) {

            if (mode.equals(BatchMode.AUTO)) {
                synchronized (batchRequests) {
                    batches = new ArrayList<Request>(batchRequests.size());
                    batches.addAll(batchRequests);
                    batchRequests.clear();
                    this.batchEnabled = false;
                }
            } else {
                this.batchEnabled = false;
                batches = batchRequests;
            }

            Request.invokeBatchCallbackStart(rpc, this);

            Map<Integer, Pair<Request, Object>> cacheObjects = new HashMap<Integer, Pair<Request, Object>>();
            if (rpc.isCacheEnabled()) {
                for (int i = batches.size() - 1; i >= 0; i--) {
                    Request req = batches.get(i);
                    if (req.isLocalCachable() || rpc.isTest()) {
                        CacheResult result = rpc.getMemoryCache().get(req.getMethodId(), req.getArgs(), rpc.isTest() ? 0 : req.getLocalCacheLifeTime(), req.getLocalCacheSize());
                        LocalCacheLevel cacheLevel = rpc.isTest() ? LocalCacheLevel.DISK_CACHE : req.getLocalCacheLevel();
                        if (result.result) {
                            if (rpc.getCacheMode() == CacheMode.CLONE) {
                                result.object = rpc.getClonner().clone(result.object);
                            }
                            cacheObjects.put(req.getId(), new Pair<Request, Object>(req, result.object));
                            if (req.getLocalCacheOnlyOnErrorMode().equals(OnlyOnError.NO)) {
                                batches.remove(i);
                                req.invokeStart(new CacheInfo(true, result.time));
                            }


                        } else if (cacheLevel != LocalCacheLevel.MEMORY_ONLY) {
                            CacheMethod cacheMethod = new CacheMethod(CacheMethod.getMethodId(req.getMethod()), req.getName(), req.getMethod().getDeclaringClass().getSimpleName()
                                    , rpc.getTestName(), rpc.getTestRevision(), rpc.getUrl(), cacheLevel);
                            result = rpc.getDiskCache().get(cacheMethod, Arrays.deepToString(req.getArgs()), req.getLocalCacheLifeTime());
                            if (result.result) {
                                if (!rpc.isTest()) {
                                    rpc.getMemoryCache().put(req.getMethodId(), req.getArgs(), result.object, req.getLocalCacheSize());
                                }
                                cacheObjects.put(req.getId(), new Pair<Request, Object>(req, result.object));
                                if (req.getLocalCacheOnlyOnErrorMode().equals(OnlyOnError.NO)) {
                                    batches.remove(i);
                                    req.invokeStart(new CacheInfo(true, result.time));
                                }
                            }

                        }
                    }

                }
            }

            BatchProgressObserver batchProgressObserver = new BatchProgressObserver(rpc, this, batches);
            List<RequestResult> responses;
            if (batches.size() > 0) {
                sendBatchRequest(batches, batchProgressObserver, cacheObjects);

            } else {
                responses = new ArrayList<RequestResult>();
                batchProgressObserver.setMaxProgress(1);
                batchProgressObserver.progressTick(1);
                receiveResponse(batches, responses, cacheObjects);
            }


        } else {
            this.batchEnabled = false;
            if (batchCallback != null) {
                Request.invokeBatchCallback(rpc, this, new Object[]{});
            }
        }
    }


    protected void receiveResponse(List<Request> batches, List<RequestResult> responses, Map<Integer, Pair<Request, Object>> cacheObjects) {
        if (rpc.isCacheEnabled()) {
            for (int i = responses.size() - 1; i >= 0; i--) {
                RequestResult result = responses.get(i);
                if (cacheObjects.containsKey(result.id) && result instanceof ErrorResult) {
                    OnlyOnError onlyOnErrorMode = cacheObjects.get(result.id).first.getLocalCacheOnlyOnErrorMode();
                    if (onlyOnErrorMode.equals(OnlyOnError.ON_ALL_ERROR) ||
                            (onlyOnErrorMode.equals(OnlyOnError.ON_CONNECTION_ERROR) && result.error instanceof ConnectionException)) {
                        responses.remove(result);
                        RequestSuccessResult res = new RequestSuccessResult(cacheObjects.get(result.id).second);
                        res.id = result.id;
                        responses.add(res);
                    }
                    cacheObjects.remove(result.id);
                } else {
                    cacheObjects.remove(result.id);
                }
            }

            for (Map.Entry<Integer, Pair<Request, Object>> pairs : cacheObjects.entrySet()) {
                RequestSuccessResult res = new RequestSuccessResult(cacheObjects.get(pairs.getKey()).second);
                res.id = pairs.getKey();
                responses.add(res);
                batches.add(pairs.getValue().first);
            }
        }
        //TODO change to map implementation
        Collections.sort(batches, new Comparator<Request>() {
            @Override
            public int compare(Request lhs, Request rhs) {
                return lhs.getId().compareTo(rhs.getId());
            }
        });
        Collections.sort(responses, new Comparator<RequestResult>() {
            @Override
            public int compare(RequestResult lhs, RequestResult rhs) {
                return lhs.id.compareTo(rhs.id);
            }
        });
        handleBatchResponse(batches, batchCallback, responses);
    }

    protected List<List<Request>> assignRequestsToConnections(List<Request> list, final int partsNo) {
        if (rpc.isTimeProfiler()) {
            return BatchTask.timeAssignRequests(list, partsNo);
        } else {
            return BatchTask.simpleAssignRequests(list, partsNo);
        }
    }


    protected int calculateTimeout(List<Request> batches) {
        int timeout = 0;
        if (rpc.getTimeoutMode() == BatchTimeoutMode.TIMEOUTS_SUM) {
            for (Request req : batches) {
                timeout += req.getTimeout();
            }
        } else {
            for (Request req : batches) {
                timeout = Math.max(timeout, req.getTimeout());
            }
        }
        return timeout;
    }

    protected void sendBatchRequest(final List<Request> batches, BatchProgressObserver progressObserver,
                                    final Map<Integer, Pair<Request, Object>> cacheObjects) {
        final List<RequestResult> responses = new ArrayList<RequestResult>(batches.size());

        try {
            rpc.getHandler().post(new AsyncResultSender(batches));
            int connections = rpc.getBestConnectionsSize()-(rpc.getExecutorService().getActiveCount()-1);
            connections = Math.max(Math.min(connections, batches.size()),1);
            if (connections > 1) {

                List<List<Request>> requestParts = assignRequestsToConnections(batches, connections);

                final List<BatchTask> tasks = new ArrayList<BatchTask>(connections);

                progressObserver.setMaxProgress((requestParts.size() + cacheObjects.size()) * TimeStat.TICKS);
                if (cacheObjects.size() > 0) {
                    progressObserver.progressTick(cacheObjects.size() * TimeStat.TICKS);
                }

                for (List<Request> requests : requestParts) {

                    BatchTask task = new BatchTask(rpc, progressObserver, calculateTimeout(requests), requests);
                    tasks.add(task);
                }
                for (BatchTask task : tasks) {
                    task.execute();
                }
                Runnable waitAndMergeTask = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            for (BatchTask task : tasks) {
                                task.join();
                                if (task.getEx() != null) {
                                    throw task.getEx();
                                } else {
                                    responses.addAll(task.getResponse());
                                }
                            }
                            Collections.sort(responses);
                            receiveResponse(batches, responses, cacheObjects);
                        } catch (final JudoException e) {
                            for (Request request : batches) {
                                responses.add(new ErrorResult(request.getId(), e));
                            }
                            Collections.sort(responses);
                            receiveResponse(batches, responses, cacheObjects);
                        }
                    }
                };
                new Thread(waitAndMergeTask, "JudoNetworking WaitAndMergeTask").start();

            } else {
                progressObserver.setMaxProgress(TimeStat.TICKS);
                responses.addAll(rpc.getRequestConnector().callBatch(batches, progressObserver, calculateTimeout(batches)));
                Collections.sort(responses);
                receiveResponse(batches, responses, cacheObjects);
            }
        } catch (final JudoException e) {
            for (Request request : batches) {
                responses.add(new ErrorResult(request.getId(), e));
            }
            Collections.sort(responses);
            receiveResponse(batches, responses, cacheObjects);
        }

    }

    protected void handleBatchResponse(List<Request> requests, BatchInterface batch, List<RequestResult> responses) {
        Object[] results = new Object[requests.size()];
        JudoException ex = null;
        Request exceptionRequest = null;
        int i = 0;
        for (Request request : requests) {
            try {
                RequestResult response = null;

                if(i<responses.size()) {
                    response = responses.get(i);
                }

                if (response!=null && response.cacheObject != null) {
                    results[i] = response.cacheObject;
                } else {

                    if(requests.size()!=responses.size()){
                        throw new ParseException("Wrong server response. Expect " + requests.size() + " batch responses, get " + responses.size());
                    }
                    else if (response.error != null) {
                        throw response.error;
                    }

                    if (request.getReturnType() != Void.class) {
                        if (rpc.isVerifyResultModel()) {
                            RequestConnector.verifyResult(request, response);
                        }
                        if (rpc.isProcessingMethod()) {
                            RequestConnector.processingMethod(response.result);
                        }
                        results[i] = response.result;
                        if ((rpc.isCacheEnabled() && request.isLocalCachable()) || rpc.isTest()) {
                            rpc.getMemoryCache().put(request.getMethodId(), request.getArgs(), results[i], request.getLocalCacheSize());
                            if (rpc.getCacheMode() == CacheMode.CLONE) {
                                results[i] = rpc.getClonner().clone(results[i]);
                            }
                            LocalCacheLevel cacheLevel = rpc.isTest() ? LocalCacheLevel.DISK_CACHE : request.getLocalCacheLevel();

                            if (cacheLevel != LocalCacheLevel.MEMORY_ONLY) {
                                CacheMethod cacheMethod = new CacheMethod(CacheMethod.getMethodId(request.getMethod()), request.getName(), request.getMethod().getDeclaringClass().getSimpleName(), rpc.getTestName(), rpc.getTestRevision(), rpc.getUrl(), cacheLevel);
                                rpc.getDiskCache().put(cacheMethod, Arrays.deepToString(request.getArgs()), results[i], request.getLocalCacheSize());
                            }
                        } else if (rpc.isCacheEnabled() && request.isServerCachable() && (response.hash != null || response.time != null)) {
                            CacheMethod cacheMethod = new CacheMethod(CacheMethod.getMethodId(request.getMethod()), request.getName(), request.getMethod().getDeclaringClass().getSimpleName(), rpc.getUrl(), response.hash, response.time, request.getServerCacheLevel());
                            rpc.getDiskCache().put(cacheMethod, Arrays.deepToString(request.getArgs()), results[i], request.getServerCacheSize());
                        }
                    }
                }
                request.invokeCallback(results[i]);
            } catch (JudoException e) {
                if (request.isBatchFatal()) {
                    ex = e;
                    exceptionRequest = request;
                }
                request.invokeCallbackException(e);
            }
            i++;
        }
        if (batch != null) {
            if (ex == null) {
                Request.invokeBatchCallback(rpc, this, results);
            } else {
                Request.invokeBatchCallbackException(rpc, this, ex);
            }
        }
        if (ex != null) {
            final JudoException finalEx = ex;
            final Request finalRequest = exceptionRequest;
            if (rpc.getErrorLoggers().size() != 0 && !(ex instanceof CancelException)) {
                rpc.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        for(ErrorLogger errorLogger : rpc.getErrorLoggers()) {
                            errorLogger.onError(finalEx, finalRequest);
                        }
                    }
                });

            }
        }
    }

    static void addToExceptionMessage(String additionalMessage, Exception exception) {
        try {
            Field field = Throwable.class.getDeclaredField("detailMessage");
            field.setAccessible(true);
            String message = additionalMessage + ": " + field.get(exception);
            field.set(exception, message);
        } catch (Exception ex) {
            LoggerImpl.log(ex);
        }
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void cancel() {
        if (!cancelled) {
            this.cancelled = true;
            for (Request request : batchRequests) {
                request.cancel();
            }
            if (running) {
                running = false;
                rpc.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        batchCallback.onFinish();
                    }
                });
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public void done() {
        this.done = true;
        this.running = false;
    }

    public void start() {
        this.running = true;
    }

    public BatchInterface<?> getBatchCallback() {
        return batchCallback;
    }

}
