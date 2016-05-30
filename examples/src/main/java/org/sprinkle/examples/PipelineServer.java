/*
 * * Copyright 2016 Intuit Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sprinkle.examples;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.sprinkle.CircuitBreaker;
import org.sprinkle.Pipeline;
import org.sprinkle.Try;
import org.sprinkle.examples.AsyncUtils.AsyncHandler;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.util.StringUtil;

import javax.servlet.AsyncContext;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import org.eclipse.jetty.client.api.Request;

import static org.sprinkle.CacheTool.constructCache;
import static org.sprinkle.CacheTool.withCache;
import static org.sprinkle.CircuitBreaker.withCircuitBreaker;
import static org.sprinkle.Http.*;
import static org.sprinkle.Pipeline.lift;
import static org.sprinkle.Pipeline.pipelineLog;
import static org.sprinkle.examples.Functions.getAddQueryParam;
import static org.sprinkle.examples.PipelineServer.AsyncRecoverHandler.asyncRecoverHandler;
import static org.sprinkle.examples.PipelineServer.AsyncRecoverHandlerEx.asyncRecoverHandlerEx;
import static org.sprinkle.examples.PipelineServer.AsyncRecoverWithHandler.asyncRecoverWithHandler;
import static org.sprinkle.examples.PipelineServer.AsyncRecoverWithHandlerEx.asyncRecoverWithHandlerEx;
import static org.sprinkle.examples.PipelineServer.CacheHandlerWithHiddenCache.cacheHandlerWithHiddenCache;
import static org.sprinkle.examples.PipelineServer.CacheHandlerWithKeyExtraction.cacheHandlerWithKeyExtraction;
import static org.sprinkle.examples.PipelineServer.CircuitBreakerHandler.circuitBreakerHandler;
import static org.sprinkle.examples.PipelineServer.CircuitBreakerLoadHandler.circuitBreakerLoadHandler;
import static org.sprinkle.examples.PipelineServer.ExtractProcessEnrichHandler.extractProcessEnrichHandler;
import static org.sprinkle.examples.PipelineServer.FallbackToHandler.fallbackToHandler;
import static org.sprinkle.examples.PipelineServer.FallbackToHandlerEx.fallbackToHandlerEx;
import static org.sprinkle.examples.PipelineServer.MultipleFallbackToHandler.multipleFallbackToHandler;
import static org.sprinkle.examples.PipelineServer.MultipleServicesCircuitBreakerHandler.multipleServicesCircuitBreakerHandler;
import static org.sprinkle.examples.PipelineServer.MultipleServicesHandler.multipleServicesHandler;
import static org.sprinkle.examples.PipelineServer.MultipleServicesHandler2.multipleServicesHandler2;
import static org.sprinkle.examples.PipelineServer.NormalizationHandler.normalizationHandler;
import static org.sprinkle.examples.PipelineServer.ParallelComposeHandler.parallelComposeHandler;
import static org.sprinkle.examples.PipelineServer.ParallelHandler.parallelHandler;
import static org.sprinkle.examples.PipelineServer.ProcessSequenceHandler.processSequenceHandler;
import static org.sprinkle.examples.PipelineServer.SimpleGetHandler.simpleGetHandler;
import static org.sprinkle.examples.PipelineServer.SimplePostHandler.simplePostHandler;
import static org.sprinkle.examples.PipelineServer.CacheHandler.cacheHandler;
import static org.sprinkle.examples.PipelineServer.ComplexOrchestrationHandler.complexOrchestrationHandler;
import static org.sprinkle.examples.PipelineServer.ComplexOrchestrationHandlerOptimized.complexOrchestrationHandlerOptimized;
import static org.sprinkle.examples.PipelineServer.FallbackCircuitBreakerHandler.fallbackCircuitBreakerHandler;
import static org.sprinkle.examples.AsyncUtils.*;
import static org.sprinkle.Tuple.*;
import static org.sprinkle.PipelineEx.*;
import static org.sprinkle.examples.PipelineServer.SwitchCaseHandler.switchCaseHandler;
import static org.sprinkle.Pipeline.tryPar;

/**
 * Created by mboussarov on 11/12/15.
 *
 * Demo of the capabilities of the Sprinkle toolkit
 *
 * Run the demo server with the following command form the root directory (make sure nothing runs on the local ports 8080 and 9901 to 9907):
 *
 *     gradle runExamples
 *
 * Use any of the following commands to demo the various capabilities:
 *     curl -v http://localhost:8080/simpleGet/
 *     curl -H "Content-type: application/json" -v -X POST -d '{"body": "this is a request body"}' http://localhost:8080/simplePost/
 * Send the following multiple times and observe the timestamp in the response remains the same for some period, because coming from the cache
 *     curl -v http://localhost:8080/cache/
 * The query parameter is the key used to cache the response content, change the key to observe how it affects the response:
 *     curl -v http://localhost:8080/cacheWithKey/?key=100
 * Alternative/shorter implementation of the above:
 *     curl -v http://localhost:8080/cacheWithKeyHiddenCache/?key=100
 * Try the following multiple times - the call goes to non-existent backend - and observe the circuit breaker changing to closed/open/half-open state
 *     curl -v http://localhost:8080/circuitBreaker/
 * Run the following, which generates load against the circuit breaker configuration and observe changing from closes to half-open to closed
 *     curl -v http://localhost:8080/circuitBreakerLoad/
 * To demo recover, recoverWith and fallbackTo run the following commands:
 *     curl -v http://localhost:8080/recover/
 *     curl -v http://localhost:8080/recoverEx/
 *     curl -v http://localhost:8080/recoverWith/
 *     curl -v http://localhost:8080/recoverWithEx/
 *     curl -v http://localhost:8080/fallbackTo/
 *     curl -v http://localhost:8080/fallbackToEx/
 *     curl -v http://localhost:8080/multipleFallbackTo/
 * Execute the following to demonstrate calling multiple services in sequence and composing the result:
 *     curl -v http://localhost:8080/multipleServices/
 *     curl -v http://localhost:8080/multipleServices2/
 * Demonstrate calling multiple services protected with circuit breaker:
 *     curl -v http://localhost:8080/multipleServicesCircuitBreaker/
 * Execute multiple services in parallel:
 *     curl -v http://localhost:8080/parallel/
 *     curl -v http://localhost:8080/parallelCompose/
 * Demonstrate various functional patterns:
 *     curl -v http://localhost:8080/extractProcessEnrich/
 *     curl -v http://localhost:8080/switchCase/?target=first
 *     curl -v http://localhost:8080/switchCase/?target=second
 *     curl -v http://localhost:8080/switchCase/?target=third
 *     curl -v http://localhost:8080/processSequence/?ignore=first
 *     curl -v http://localhost:8080/processSequence/?ignore=second
 *     curl -v http://localhost:8080/processSequence/?ignore=third
 *     curl -v http://localhost:8080/normalize/
 * Demonstrate a truly complex orchestrations
 *     curl -v http://localhost:8080/complexOrchestration/?id=123
 *     curl -v http://localhost:8080/complexOrchestrationOptimized/?id=123
 * Demonstrate constricting a fallback composition with circuit breaker
 *     curl -v http://localhost:8080/fallbackCircuitBreaker/?id=123
 */
public class PipelineServer {

    public static void main( String[] args ) throws Exception {
        // Start the simulator
        SimpleSimulator.startAllServers();

        // Start the main demo program program
        Server server = new Server(8080);

        ContextHandlerCollection contexts = new ContextHandlerCollection();
        contexts.setHandlers(new Handler[]{
                contextHandler("/simpleGet/", simpleGetHandler("http://localhost:9901/v1")),
                contextHandler("/simplePost/", simplePostHandler("http://localhost:9902/v1", "{\"id\"=\"100\"}")),
                contextHandler("/cache/", cacheHandler("http://localhost:9903/v1")),
                contextHandler("/cacheWithKey/", cacheHandlerWithKeyExtraction("http://localhost:9903/v1")),
                contextHandler("/cacheWithKeyHiddenCache/", cacheHandlerWithHiddenCache("http://localhost:9903/v1")),
                contextHandler("/circuitBreaker/", circuitBreakerHandler("http://localhost:9900/v1")), // Nothing runs on this port
                contextHandler("/circuitBreakerLoad/", circuitBreakerLoadHandler("http://localhost:9900/v1")),
                contextHandler("/recover/", asyncRecoverHandler("http://localhost:9900/v1")), // Nothing runs on this port, use the recover
                contextHandler("/recoverEx/", asyncRecoverHandlerEx("http://localhost:9900/v1")), // Nothing runs on this port, use the recover
                contextHandler("/recoverWith/", asyncRecoverWithHandler("http://localhost:9900/v1")), // Nothing runs on this port, use the recover
                contextHandler("/recoverWithEx/", asyncRecoverWithHandlerEx("http://localhost:9900/v1")), // Nothing runs on this port, use the recover
                contextHandler("/fallbackTo/", fallbackToHandler("http://localhost:9900/v1", "http://localhost:9901/v1")),
                contextHandler("/fallbackToEx/", fallbackToHandlerEx("http://localhost:9900/v1", "http://localhost:9901/v1", "http://localhost:9902/v1")),
                contextHandler("/multipleFallbackTo/", multipleFallbackToHandler("http://localhost:9899/v1", "http://localhost:9900/v1", "http://localhost:9902/v1")),
                contextHandler("/multipleServices/", multipleServicesHandler("http://localhost:9901/v1", "http://localhost:9902/v1", "http://localhost:9903/v1")),
                contextHandler("/multipleServices2/", multipleServicesHandler2("http://localhost:9901/v1", "http://localhost:9902/v1", "http://localhost:9903/v1")),
                contextHandler("/multipleServicesCircuitBreaker/", multipleServicesCircuitBreakerHandler("http://localhost:9900/v1", "http://localhost:9901/v1")),
                contextHandler("/parallel/", parallelHandler("http://localhost:9901/v1", "http://localhost:9902/v1", "http://localhost:9903/v1")),
                contextHandler("/parallelCompose/", parallelComposeHandler("http://localhost:9901/v1", "http://localhost:9902/v1", "http://localhost:9903/v1")),
                contextHandler("/extractProcessEnrich/", extractProcessEnrichHandler("http://localhost:9901/v1")),
                contextHandler("/switchCase/", switchCaseHandler("http://localhost:9901/v1", "http://localhost:9902/v1", "http://localhost:9903/v1")), // The URI must be /switchCase/?target=first (or "second", or "third")
                contextHandler("/processSequence/", processSequenceHandler("http://localhost:9901/v1", "http://localhost:9902/v1", "http://localhost:9903/v1")), // The URI must be /processSequence/?ignore=first (or "second", or "third")
                contextHandler("/normalize/", normalizationHandler("http://localhost:9900/v1", "http://localhost:9901/v1")),
                contextHandler("/complexOrchestration/", complexOrchestrationHandler()),
                contextHandler("/complexOrchestrationOptimized/", complexOrchestrationHandlerOptimized()),
                contextHandler("/fallbackCircuitBreaker/", fallbackCircuitBreakerHandler("http://localhost:9901/v1", "http://localhost:9902/v1")),
        });

        server.setHandler(contexts);

        // Start the server
        server.start();
    }

    // Handlers that demonstrate different capabilities of the sprinkle toolkit

    // Simple Get
    public static class SimpleGetHandler extends AsyncHandler {
        private String url;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineGetFunc;

        // Factory method
        public static SimpleGetHandler simpleGetHandler(String url) throws Exception {
            return new SimpleGetHandler(url);
        }

        // Constructor
        private SimpleGetHandler(String url) throws Exception {
            super();
            this.url = url;

            pipelineGetFunc = Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                                      .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                                      .pipeToAsync(trySendReceive)
                                      .pipeToSync(unmarshallToString())
                                      .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineGetFunc.apply(url);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Simple Post
    public static class SimplePostHandler extends AsyncHandler {
        private String postContent;

        // Pipeline function that does everything
        private Function<ContentProvider, CompletableFuture<Try<String>>> pipelinePostFunc;

        // Factory method
        public static SimplePostHandler simplePostHandler(String url, String postContent) throws Exception {
            return new SimplePostHandler(url, postContent);
        }

        // Constructor
        private SimplePostHandler(String url, String postContent) throws Exception {
            super();
            this.postContent = postContent;

            pipelinePostFunc = Pipeline.createPipeline(tryConstructPost(url, getHttpClient(), "application/json"))
                                       .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                                       .pipeToAsync(trySendReceive)
                                       .pipeToSync(unmarshallToString())
                                       .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            ContentProvider provider = new StringContentProvider(postContent);

            CompletableFuture<Try<String>> result = pipelinePostFunc.apply(provider);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Cache demo
    public static class CacheHandler extends AsyncHandler {
        private String url;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineCacheFunc;

        // Factory method
        public static CacheHandler cacheHandler(String url) throws Exception {
            return new CacheHandler(url);
        }

        // Constructor
        private CacheHandler(String url) throws Exception {
            super();
            this.url = url;

            Function<String, CompletableFuture<Try<String>>> pipelineGetFunction =
                Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                        .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .pipeToAsync(trySendReceive)
                        .pipeToSync(unmarshallToString())
                        .get();

            // Construct the cache
            CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(10, TimeUnit.SECONDS);

            // This cache reference can be used to invalidate certain entities etc.
            Cache<String, CompletableFuture<Try<String>>> cache = constructCache(cacheBuilder, pipelineGetFunction);

            // Wrap the original function in cache
            pipelineCacheFunc = withCache(cache);

            // Alternatively:
            // pipelineCacheFunc = withCache(constructCache(cacheBuilder, pipelineGetFunction));
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineCacheFunc.apply(url);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Cache demo - extract the cached key from a request query parameter
    public static class CacheHandlerWithKeyExtraction extends AsyncHandler {
        // Pipeline function that does everything
        private Function<AsyncContext, CompletableFuture<Try<String>>> pipelineCacheFunc;

        // Factory method
        public static CacheHandlerWithKeyExtraction cacheHandlerWithKeyExtraction(String url) throws Exception {
            return new CacheHandlerWithKeyExtraction(url);
        }

        // Constructor
        private CacheHandlerWithKeyExtraction(String url) throws Exception {
            super();

            // Extract a key from a request
            Function<AsyncContext, Try<String>> extractKey = a -> {
                // I.e. URI is /v1?key=100
                String key = a.getRequest().getParameter("key");
                if(key != null){
                    return Try.success(key);
                } else {
                    return Try.failure(new RuntimeException("Invalid key"));
                }
            };

            // Construct the target URL
            Function<String, Try<String>> constructUrl = a -> {
                return Try.success(url + "?key=" + a);
            };

            // Main processing function
            Function<String, CompletableFuture<Try<String>>> wrappedPipelineFunc =
                    Pipeline.createPipeline(constructUrl)
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            // Construct the cache
            CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(10, TimeUnit.SECONDS);

            // This cache reference can be used to invalidate certain entities etc.
            Cache<String, CompletableFuture<Try<String>>> cache = constructCache(cacheBuilder, wrappedPipelineFunc);

            // Wrap the original function in cache
            pipelineCacheFunc = withCache(cache, extractKey);

            // Alternatively:
            // pipelineCacheFunc = withCache(constructCache(cacheBuilder, wrappedPipelineFunc), extractKey);
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineCacheFunc.apply(asyncContext);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Cache demo - extract the cached key from a request query parameter using hidden cache
    public static class CacheHandlerWithHiddenCache extends AsyncHandler {
        // Pipeline function that does everything
        private Function<AsyncContext, CompletableFuture<Try<String>>> pipelineCacheFunc;

        // Factory method
        public static CacheHandlerWithHiddenCache cacheHandlerWithHiddenCache(String url) throws Exception {
            return new CacheHandlerWithHiddenCache(url);
        }

        // Constructor
        private CacheHandlerWithHiddenCache(String url) throws Exception {
            super();

            // Extract a key from a request
            Function<AsyncContext, Try<String>> extractKey = a -> {
                // I.e. URI is /v1?key=100
                String key = a.getRequest().getParameter("key");
                if(key != null){
                    return Try.success(key);
                } else {
                    return Try.failure(new RuntimeException("Invalid key"));
                }
            };

            // Construct the target URL
            Function<String, Try<String>> constructUrl = a -> {
                return Try.success(url + "?key=" + a);
            };

            CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(10, TimeUnit.SECONDS);

            // Main processing function
            pipelineCacheFunc = withCache(
                    cacheBuilder,
                    Pipeline.createPipeline(constructUrl)
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get(),
                    extractKey);
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineCacheFunc.apply(asyncContext);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Circuit Breaker
    public static class CircuitBreakerHandler extends AsyncHandler {
        private String url;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineGetFunc;

        // Factory method
        public static CircuitBreakerHandler circuitBreakerHandler(String url) throws Exception {
            return new CircuitBreakerHandler(url);
        }

        // Constructor
        private CircuitBreakerHandler(String url) throws Exception {
            super();
            this.url = url;

            // Construct the circuit breaker
            ExecutorService executorService = Executors.newFixedThreadPool(20);

            // Open the circuit breaker on 3 errors for the duration of 5 seconds
            CircuitBreaker.CircuitBreakerStateManager circuitBreakerStateManager =
                    CircuitBreaker.CircuitBreakerStateManager.createStateManager(3, Duration.ofSeconds(10), Executors.newFixedThreadPool(10));

            // Construct the function with circuit breaker
            pipelineGetFunc = Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(withCircuitBreaker(trySendReceive, executorService, circuitBreakerStateManager))
                    .pipeToSync(unmarshallToString())
                    .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            // Now the call is protected with a circuit breaker
            CompletableFuture<Try<String>> result = pipelineGetFunc.apply(url);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Circuit Breaker with load
    public static class CircuitBreakerLoadHandler extends AsyncHandler {
        private String url;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineGetFunc;

        // Factory method
        public static CircuitBreakerLoadHandler circuitBreakerLoadHandler(String url) throws Exception {
            return new CircuitBreakerLoadHandler(url);
        }

        // Constructor
        private CircuitBreakerLoadHandler(String url) throws Exception {
            super();
            this.url = url;

            // Construct the circuit breaker
            ExecutorService executorService = Executors.newFixedThreadPool(20);

            // Open the circuit breaker on 3 errors for the duration of 5 seconds
            CircuitBreaker.CircuitBreakerStateManager circuitBreakerStateManager =
                    CircuitBreaker.CircuitBreakerStateManager.createStateManager(3, Duration.ofSeconds(5), Executors.newFixedThreadPool(10));

            circuitBreakerStateManager.addClosedListener(() -> System.out.println("Circuit breaker CLOSED"));
            circuitBreakerStateManager.addOpenListener(() -> System.out.println("Circuit breaker OPEN"));
            circuitBreakerStateManager.addHalfOpenListener(() -> System.out.println("Circuit breaker HALF-OPEN"));

            // Construct the function with circuit breaker
            pipelineGetFunc = Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(withCircuitBreaker(trySendReceive, executorService, circuitBreakerStateManager))
                    .pipeToSync(unmarshallToString())
                    .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            // Mini-load test
            for(int i = 0; i <= 8000; i++){
                // Start 2000 threads at a time
                if(i > 0 && i % 2000 == 0){
                    System.out.println("> Executed " + i + " requests...");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {} // Ignore
                }

                CompletableFuture<Try<String>> result = pipelineGetFunc.apply(url);

                result.thenAccept(a -> {
                    if(a.isSuccess()){
                        System.out.println("Success: " + a.get());
                    } else {
                        // Failure
                        System.out.println("Failure: " + a.getException().toString());
                    }
                });
            }

            CompletableFuture<Try<String>> response = new CompletableFuture<>();
            response.complete(Try.success("Load completed, check the logs"));
            response.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Recover
    public static class AsyncRecoverHandler extends AsyncHandler {
        private String url;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineGetFuncWithRecover;

        // Factory method
        public static AsyncRecoverHandler asyncRecoverHandler(String url) throws Exception {
            return new AsyncRecoverHandler(url);
        }

        // Constructor
        private AsyncRecoverHandler(String url) throws Exception {
            super();
            this.url = url;

            Function<Throwable, String> recoverFunc = t -> {
                if(t instanceof TimeoutException){
                    // Look for a particular exception
                    return "{\"errorMessage\": \"Got TimeoutException, recovering... Original error: " + t.getMessage() + "\"}";
                } else {
                    // Must have a catch all exception
                    return "{\"errorMessage\": \"Got some error, recovering... Original error: " + t.getMessage() + "\"}";
                }
            };

            pipelineGetFuncWithRecover = Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .recover(recoverFunc)
                    .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineGetFuncWithRecover.apply(url);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Extended recover - with the original value
    public static class AsyncRecoverHandlerEx extends AsyncHandler {
        private String url;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineGetFuncWithRecover;

        // Factory method
        public static AsyncRecoverHandlerEx asyncRecoverHandlerEx(String url) throws Exception {
            return new AsyncRecoverHandlerEx(url);
        }

        // Constructor
        private AsyncRecoverHandlerEx(String url) throws Exception {
            super();
            this.url = url;

            Function<Tuple2<Throwable, String>, String> recoverFunc = t -> {
                if(t._1() instanceof TimeoutException){
                    // Look for a particular exception
                    return "{\"errorMessage\": \"Got TimeoutException, recovering... Original error: " + t._1().getMessage() +
                            "; Original input: " + t._2() + "\"}";
                } else {
                    // Must have a catch all exception
                    return "{\"errorMessage\": \"Got some error, recovering... Original error: " + t._1().getMessage() +
                            "; Original input: " + t._2() + "\"}";
                }
            };

            pipelineGetFuncWithRecover = Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .recoverEx(recoverFunc)
                    .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineGetFuncWithRecover.apply(url);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Recover With
    public static class AsyncRecoverWithHandler extends AsyncHandler {
        private String url;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineFunc;

        // Factory method
        public static AsyncRecoverWithHandler asyncRecoverWithHandler(String url) throws Exception {
            return new AsyncRecoverWithHandler(url);
        }

        // Constructor
        private AsyncRecoverWithHandler(String url) throws Exception {
            super();
            this.url = url;

            Function<Throwable, Try<String>> recoverWithFunc = t -> {
                if(t instanceof TimeoutException){
                    // Look for a particular exception
                    return Try.success("{\"errorMessage\": \"Got TimeoutException, recovering... Original error: " + t.getMessage() + "\"}");
                } else {
                    // Must have a catch all exception
                    return Try.failure(new RuntimeException("\"errorMessage\": \"Convert an error to some other error... Original error: " +
                            t.getMessage() + "\"}"));
                }
            };

            pipelineFunc = Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .recoverWith(recoverWithFunc)
                    .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineFunc.apply(url);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Extended recover with - with the original value
    public static class AsyncRecoverWithHandlerEx extends AsyncHandler {
        private String url;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineFunc;

        // Factory method
        public static AsyncRecoverWithHandlerEx asyncRecoverWithHandlerEx(String url) throws Exception {
            return new AsyncRecoverWithHandlerEx(url);
        }

        // Constructor
        private AsyncRecoverWithHandlerEx(String url) throws Exception {
            super();
            this.url = url;

            Function<Tuple2<Throwable, String>, Try<String>> recoverWithFunc = t -> {
                if(t._1() instanceof TimeoutException){
                    // Look for a particular exception
                    return Try.success("{\"errorMessage\": \"Got TimeoutException, recovering with... Original error: " + t._1().getMessage() +
                            "; Original input: " + t._2() + "\"}");
                } else {
                    // Must have a catch all exception
                    return Try.failure(new RuntimeException("{\"errorMessage\": \"Convert an error to some other error... Original error: " + t._1().getMessage() +
                            "; Original input: " + t._2() + "\"}"));
                }
            };

            pipelineFunc = Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .recoverWithEx(recoverWithFunc)
                    .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineFunc.apply(url);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Fallback To
    public static class FallbackToHandler extends AsyncHandler {
        private String urlPrimary;
        private String urlSecondary;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineFunc;

        // Factory method
        public static FallbackToHandler fallbackToHandler(String urlPrimary, String urlSecondary) throws Exception {
            return new FallbackToHandler(urlPrimary, urlSecondary);
        }

        // Constructor
        private FallbackToHandler(String urlPrimary, String urlSecondary) throws Exception {
            super();
            this.urlPrimary = urlPrimary;
            this.urlSecondary = urlSecondary;

            Function<String, CompletableFuture<Try<String>>> pipelineMainFunc =
                Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                    .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                    .pipeToAsync(trySendReceive)
                    .pipeToSync(unmarshallToString())
                    .get();

            Function<Throwable, CompletableFuture<Try<String>>> fallbackToFunc = t -> {
                // Do something with the exception...
                // Use the same function, just to a different destination, or a complete new function
                return pipelineMainFunc.apply(this.urlSecondary);
            };

            // Complete function with primary and fallback
            pipelineFunc = Pipeline.createAsyncPipeline(pipelineMainFunc)
                    .fallbackTo(fallbackToFunc)
                    .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineFunc.apply(urlPrimary);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Fallback To Extended
    public static class FallbackToHandlerEx extends AsyncHandler {
        private String urlPrimary;
        private String urlSecondary;
        private String urlTertiary;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineFunc;

        // Factory method
        public static FallbackToHandlerEx fallbackToHandlerEx(String urlPrimary, String urlSecondary, String urlThird) throws Exception {
            return new FallbackToHandlerEx(urlPrimary, urlSecondary, urlThird);
        }

        // Constructor
        private FallbackToHandlerEx(String urlPrimary, String urlSecondary, String urlTertiary) throws Exception {
            super();
            this.urlPrimary = urlPrimary;
            this.urlSecondary = urlSecondary;
            this.urlTertiary = urlTertiary;

            Function<String, CompletableFuture<Try<String>>> pipelineMainFunc =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<Tuple2<Throwable, String>, CompletableFuture<Try<String>>> fallbackToExFunc = t -> {
                // Do something with the exception and the original input
                if(t._1() instanceof TimeoutException &&
                   t._2().contains("9900")){
                    return pipelineMainFunc.apply(this.urlSecondary);
                } else {
                    // Some other exception
                    return pipelineMainFunc.apply(this.urlTertiary);
                }
            };

            // Complete function with primary and fallback
            pipelineFunc = Pipeline.createAsyncPipeline(pipelineMainFunc)
                    .fallbackToEx(fallbackToExFunc)
                    .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineFunc.apply(urlPrimary);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Fallback To with multiple destinations
    public static class MultipleFallbackToHandler extends AsyncHandler {
        private String urlPrimary;
        private String urlSecondary;
        private String urlTertiary;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineFunc;

        // Factory method
        public static MultipleFallbackToHandler multipleFallbackToHandler(String urlPrimary, String urlSecondary, String urlThird) throws Exception {
            return new MultipleFallbackToHandler(urlPrimary, urlSecondary, urlThird);
        }

        // Constructor
        private MultipleFallbackToHandler(String urlPrimary, String urlSecondary, String urlTertiary) throws Exception {
            super();
            this.urlPrimary = urlPrimary;
            this.urlSecondary = urlSecondary;
            this.urlTertiary = urlTertiary;

            Function<String, CompletableFuture<Try<String>>> pipelineMainFunc =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<Throwable, CompletableFuture<Try<String>>> fallbackToFunc1 = t -> {
                return pipelineMainFunc.apply(this.urlSecondary);
            };

            Function<Throwable, CompletableFuture<Try<String>>> fallbackToFunc2 = t -> {
                return pipelineMainFunc.apply(this.urlTertiary);
            };

            // Complete function with primary and fallback
            pipelineFunc = Pipeline.createAsyncPipeline(pipelineMainFunc)
                    .fallbackTo(fallbackToFunc1)
                    .fallbackTo(fallbackToFunc2)
                    .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineFunc.apply(urlPrimary);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Multiple service calls and response composition
    public static class MultipleServicesHandler extends AsyncHandler {
        private String urlPrimary;
        private String urlSecondary;
        private String urlTertiary;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<Tuple3<String, String, String>>>> pipelineFunc;

        // Factory method
        public static MultipleServicesHandler multipleServicesHandler(String urlPrimary, String urlSecondary, String urlThird) throws Exception {
            return new MultipleServicesHandler(urlPrimary, urlSecondary, urlThird);
        }

        // Constructor
        private MultipleServicesHandler(String urlPrimary, String urlSecondary, String urlTertiary) throws Exception {
            super();
            this.urlPrimary = urlPrimary;
            this.urlSecondary = urlSecondary;
            this.urlTertiary = urlTertiary;

            Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, CompletableFuture<Try<String>>> pipelineFuncThird =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            pipelineFunc = Pipeline.createAsyncPipeline(pipelineFuncFirst)
                            .pipeToAsync(s -> {
                                return composeTuple2(s, pipelineFuncSecond.apply(urlSecondary));
                            })
                            .pipeToAsync(t -> {
                                return composeTuple3(t, pipelineFuncThird.apply(urlTertiary));
                            })
                            .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<Tuple3<String, String, String>>> result = pipelineFunc.apply(urlPrimary);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Multiple service calls and response composition - version 2
    public static class MultipleServicesHandler2 extends AsyncHandler {
        private String urlPrimary;
        private String urlSecondary;
        private String urlTertiary;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<Tuple3<String, String, String>>>> pipelineFunc;

        // Factory method
        public static MultipleServicesHandler2 multipleServicesHandler2(String urlPrimary, String urlSecondary, String urlThird) throws Exception {
            return new MultipleServicesHandler2(urlPrimary, urlSecondary, urlThird);
        }

        // Constructor
        private MultipleServicesHandler2(String urlPrimary, String urlSecondary, String urlTertiary) throws Exception {
            super();
            this.urlPrimary = urlPrimary;
            this.urlSecondary = urlSecondary;
            this.urlTertiary = urlTertiary;

            Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, CompletableFuture<Try<String>>> pipelineFuncThird =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, CompletableFuture<Try<Tuple2<String, String>>>> innerFunc2 = s -> {
                return composeTuple2(s, pipelineFuncSecond.apply(urlSecondary));
            };

            Function<Tuple2<String, String>, CompletableFuture<Try<Tuple3<String, String, String>>>> innerFunc3 = t -> {
                return composeTuple3(t, pipelineFuncThird.apply(urlTertiary));
            };

            pipelineFunc = Pipeline.createAsyncPipeline(pipelineFuncFirst)
                            .pipeToAsync(innerFunc2)
                            .pipeToAsync(innerFunc3)
                            .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<Tuple3<String, String, String>>> result = pipelineFunc.apply(urlPrimary);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Protect the service that fails, send everything to the second
    public static class MultipleServicesCircuitBreakerHandler extends AsyncHandler {
        private String urlPrimary;
        private String urlSecondary;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineFunc;

        // Factory method
        public static MultipleServicesCircuitBreakerHandler multipleServicesCircuitBreakerHandler(String urlPrimary, String urlSecondary) throws Exception {
            return new MultipleServicesCircuitBreakerHandler(urlPrimary, urlSecondary);
        }

        // Constructor
        private MultipleServicesCircuitBreakerHandler(String urlPrimary, String urlSecondary) throws Exception {
            super();
            this.urlPrimary = urlPrimary;
            this.urlSecondary = urlSecondary;

            Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<Throwable, CompletableFuture<Try<String>>> recoverFunction = t -> {
                return pipelineFuncSecond.apply(urlSecondary);
            };

            // Circuit breaker definitions
            // Construct the circuit breaker
            ExecutorService executorService = Executors.newFixedThreadPool(10);

            CircuitBreaker.CircuitBreakerStateManager circuitBreakerStateManager =
                    CircuitBreaker.CircuitBreakerStateManager.createStateManager(5, Duration.ofSeconds(5), Executors.newFixedThreadPool(2));

            circuitBreakerStateManager.addClosedListener(() -> System.out.println("Circuit breaker CLOSED"));
            circuitBreakerStateManager.addOpenListener(() -> System.out.println("Circuit breaker OPEN"));
            circuitBreakerStateManager.addHalfOpenListener(() -> System.out.println("Circuit breaker HALF-OPEN"));

            // Composite function
            pipelineFunc = Pipeline.createAsyncPipeline(withCircuitBreaker(pipelineFuncFirst, executorService, circuitBreakerStateManager))
                           .fallbackTo(recoverFunction)
                           .get();

        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineFunc.apply(urlPrimary);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Executing multiple functions in parallel
    public static class ParallelHandler extends AsyncHandler {
        private String urlPrimary;
        private String urlSecondary;
        private String urlTertiary;

        // Pipeline function that does everything
        private Function<Tuple3<String, String, String>, CompletableFuture<Tuple3<Try<String>, Try<String>, Try<String>>>> pipelineFunc;

        // Factory method
        public static ParallelHandler parallelHandler(String urlPrimary, String urlSecondary, String urlThird) throws Exception {
            return new ParallelHandler(urlPrimary, urlSecondary, urlThird);
        }

        // Constructor
        private ParallelHandler(String urlPrimary, String urlSecondary, String urlTertiary) throws Exception {
            super();
            this.urlPrimary = urlPrimary;
            this.urlSecondary = urlSecondary;
            this.urlTertiary = urlTertiary;

            Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, CompletableFuture<Try<String>>> pipelineFuncThird =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            pipelineFunc = Pipeline.par(pipelineFuncFirst, pipelineFuncSecond, pipelineFuncThird);
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Tuple3<Try<String>, Try<String>, Try<String>>> result = pipelineFunc.apply(tuple3(urlPrimary, urlSecondary, urlTertiary));
            result.thenAccept(getAsyncResponseFunctionPar(asyncContext, "application/json"));
        }
    }

    // Executing multiple functions in parallel and compose the result
    public static class ParallelComposeHandler extends AsyncHandler {
        private String urlPrimary;
        private String urlSecondary;
        private String urlTertiary;

        // Pipeline function that does everything
        private Function<Tuple3<String, String, String>, CompletableFuture<Try<String>>> pipelineFunc;

        // Factory method
        public static ParallelComposeHandler parallelComposeHandler(String urlPrimary, String urlSecondary, String urlThird) throws Exception {
            return new ParallelComposeHandler(urlPrimary, urlSecondary, urlThird);
        }

        // Constructor
        private ParallelComposeHandler(String urlPrimary, String urlSecondary, String urlTertiary) throws Exception {
            super();
            this.urlPrimary = urlPrimary;
            this.urlSecondary = urlSecondary;
            this.urlTertiary = urlTertiary;

            Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, CompletableFuture<Try<String>>> pipelineFuncThird =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            // Create the parallel function
            Function<Tuple3<String, String, String>, CompletableFuture<Try<Tuple3<String, String, String>>>> parFunc =
                    Pipeline.tryPar(pipelineFuncFirst, pipelineFuncSecond, pipelineFuncThird);

            Function<Tuple3<String, String, String>, String> aggFunc = a -> {
                return "[" + a._1() + ", " + a._2() + ", " + a._3() + "]";
            };

            // Create the final composite function
            pipelineFunc = Pipeline.createAsyncPipeline(parFunc)
                            .pipeToSync(lift(aggFunc))
                            .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineFunc.apply(tuple3(urlPrimary, urlSecondary, urlTertiary));
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Pattern examples

    // Extract, process, enrich handler
    public static class ExtractProcessEnrichHandler extends AsyncHandler {
        private String urlPrimary;

        // Pipeline function that does everything
        private Function<PipelineContext, CompletableFuture<Try<PipelineContext>>> pipelineFunc;

        // Factory method
        public static ExtractProcessEnrichHandler extractProcessEnrichHandler(String urlPrimary) throws Exception {
            return new ExtractProcessEnrichHandler(urlPrimary);
        }

        // Constructor
        private ExtractProcessEnrichHandler(String urlPrimary) throws Exception {
            super();
            this.urlPrimary = urlPrimary;

            Function<String, CompletableFuture<Try<String>>> processFunc =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<PipelineContext, Try<String>> extractFunc = a -> {
                return Try.success(a.getUrlOne());
            };

            Function<Tuple2<PipelineContext, String>, Try<PipelineContext>> enrichFunc = a -> {
                // If true functional programmer, "a" should be immutable
                a._1().setResponseOne(a._2());
                return Try.success(a._1());
            };

            // Create the final composite function
            pipelineFunc = extractProcessEnrich(extractFunc, processFunc, enrichFunc);
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            PipelineContext pipelineContext = new PipelineContext();
            pipelineContext.setUrlOne(urlPrimary);

            CompletableFuture<Try<PipelineContext>> result = pipelineFunc.apply(pipelineContext);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Switch/case example
    public static class SwitchCaseHandler extends AsyncHandler {
        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineFunc;

        // Factory method
        public static SwitchCaseHandler switchCaseHandler(String urlPrimary, String urlSecondary, String urlThird) throws Exception {
            return new SwitchCaseHandler(urlPrimary, urlSecondary, urlThird);
        }

        // Constructor
        private SwitchCaseHandler(String urlPrimary, String urlSecondary, String urlTertiary) throws Exception {
            super();

            // Function that ignores the input parameter and returns a string it has been initialized with
            Function<String, Function<String, Try<String>>> urlFunc = a -> {
                return b -> Try.success(a);
            };

            Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
                    Pipeline.createPipeline(urlFunc.apply(urlPrimary))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
                    Pipeline.createPipeline(urlFunc.apply(urlSecondary))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, CompletableFuture<Try<String>>> pipelineFuncThird =
                    Pipeline.createPipeline(urlFunc.apply(urlTertiary))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<String, Predicate<String>> condition = a -> {
                return b -> { return a.equalsIgnoreCase(b);};
            };

            // Construct the final function, the types cannot be inferred unfortunately in Java, so the types myst be specified
            pipelineFunc = switchCase(Arrays.<Tuple2<Predicate<String>, Function<String, CompletableFuture<Try<String>>>>>asList(
                    tuple(condition.apply("first"), pipelineFuncFirst),
                    tuple(condition.apply("second"), pipelineFuncSecond),
                    tuple(condition.apply("third"), pipelineFuncThird)
                ), new RuntimeException("No matching condition"));
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineFunc.apply(asyncContext.getRequest().getParameter("target"));
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Process a sequence of functions as long as the correspondent condition matches
    public static class ProcessSequenceHandler extends AsyncHandler {
        private String urlPrimary;
        private String urlSecondary;
        private String urlTertiary;

        // Pipeline function that does everything
        private Function<PipelineContext, CompletableFuture<Try<PipelineContext>>> pipelineFunc;

        // Factory method
        public static ProcessSequenceHandler processSequenceHandler(String urlPrimary, String urlSecondary, String urlTertiary) throws Exception {
            return new ProcessSequenceHandler(urlPrimary, urlSecondary, urlTertiary);
        }

        // Constructor
        private ProcessSequenceHandler(String urlPrimary, String urlSecondary, String urlTertiary) throws Exception {
            super();
            this.urlPrimary = urlPrimary;
            this.urlSecondary = urlSecondary;
            this.urlTertiary = urlTertiary;

            // Base function
            Function<String, CompletableFuture<Try<String>>> processFunc =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            // Support functions
            Function<PipelineContext, Try<String>> extractFuncOne = a -> {
                return Try.success(a.getUrlOne());
            };

            Function<Tuple2<PipelineContext, String>, Try<PipelineContext>> enrichFuncOne = a -> {
                // If true functional programmer, "a" should be immutable
                a._1().setResponseOne(a._2());
                return Try.success(a._1());
            };

            // Composition
            Function<PipelineContext, CompletableFuture<Try<PipelineContext>>> pipelineFuncOne =
                    extractProcessEnrich(extractFuncOne, processFunc, enrichFuncOne);

            // Again..
            // Support functions
            Function<PipelineContext, Try<String>> extractFuncTwo = a -> {
                return Try.success(a.getUrlTwo());
            };

            Function<Tuple2<PipelineContext, String>, Try<PipelineContext>> enrichFuncTwo = a -> {
                // If true functional programmer, "a" should be immutable
                a._1().setResponseTwo(a._2());
                return Try.success(a._1());
            };

            // Composition
            Function<PipelineContext, CompletableFuture<Try<PipelineContext>>> pipelineFuncTwo =
                    extractProcessEnrich(extractFuncTwo, processFunc, enrichFuncTwo);

            // And again..
            // Support functions
            Function<PipelineContext, Try<String>> extractFuncThree = a -> {
                return Try.success(a.getUrlThree());
            };

            Function<Tuple2<PipelineContext, String>, Try<PipelineContext>> enrichFuncThree = a -> {
                // If true functional programmer, "a" should be immutable
                a._1().setResponseThree(a._2());
                return Try.success(a._1());
            };

            // Composition
            Function<PipelineContext, CompletableFuture<Try<PipelineContext>>> pipelineFuncThree =
                    extractProcessEnrich(extractFuncThree, processFunc, enrichFuncThree);

            // Condition function
            Function<String, Predicate<PipelineContext>> condition = a -> {
                return b -> !a.equalsIgnoreCase(b.getConditionParameter());
            };

            // Final function
            pipelineFunc = processSequence(Arrays.<Tuple2<Predicate<PipelineContext>, Function<PipelineContext, CompletableFuture<Try<PipelineContext>>>>>asList(
                    tuple(condition.apply("first"), pipelineFuncOne),
                    tuple(condition.apply("second"), pipelineFuncTwo),
                    tuple(condition.apply("third"), pipelineFuncThree)));
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            PipelineContext pipelineContext = new PipelineContext();
            pipelineContext.setUrlOne(urlPrimary);
            pipelineContext.setUrlTwo(urlSecondary);
            pipelineContext.setUrlThree(urlTertiary);
            pipelineContext.setConditionParameter(asyncContext.getRequest().getParameter("ignore"));

            CompletableFuture<Try<PipelineContext>> result = pipelineFunc.apply(pipelineContext);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Normalization example
    public static class NormalizationHandler extends AsyncHandler {
        private String urlPrimary;
        private String urlSecondary;

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineFunc;

        // Factory method
        public static NormalizationHandler normalizationHandler(String urlPrimary, String urlSecondary) throws Exception {
            return new NormalizationHandler(urlPrimary, urlSecondary);
        }

        // Constructor
        private NormalizationHandler(String urlPrimary, String urlSecondary) throws Exception {
            super();
            this.urlPrimary = urlPrimary;
            this.urlSecondary = urlSecondary;

            Function<String, CompletableFuture<Try<String>>> pipelineMainFunc =
                    Pipeline.createPipeline(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            // Imaginary fallback function
            Function<Throwable, CompletableFuture<Try<ValueContainer>>> fallbackToFunc = t -> {
                Function<String, Try<ValueContainer>> convertorFunc = a -> {
                    return Try.success(new ValueContainer(a));
                };

                Function<String, CompletableFuture<Try<ValueContainer>>> innerFallbackToFunc =
                        Pipeline.createAsyncPipeline(pipelineMainFunc)
                                .pipeToSync(convertorFunc)
                                .get();

                return innerFallbackToFunc.apply(this.urlSecondary);
            };

            // Normalization function
            Function<ValueContainer, Try<String>> normalizerFunc = a -> {
                return Try.success(a.getValue());
            };

            // Complete function where the second function is normalized to the expected result
            pipelineFunc = Pipeline.createAsyncPipeline(pipelineMainFunc)
                    .fallbackTo(normalize(fallbackToFunc, normalizerFunc))
                    .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            CompletableFuture<Try<String>> result = pipelineFunc.apply(urlPrimary);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Super-complex orchestration - six services with multiple fallback and recovery
    public static class ComplexOrchestrationHandler extends AsyncHandler {
        private String defaultQueryId = "100";
        private String urlFirst = "http://localhost:9901/v1";
        private String urlFirstFallback = "http://localhost:9902/v1";
        private String urlSecond = "http://localhost:9903/v1";
        private String urlSecondFallback = "http://localhost:9904/v1";
        private String urlThird = "http://localhost:9905/v1";
        private String urlThirdFallback = "http://localhost:9906/v1";

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineFunc;

        // Factory method
        public static ComplexOrchestrationHandler complexOrchestrationHandler() throws Exception {
            return new ComplexOrchestrationHandler();
        }

        // Constructor
        private ComplexOrchestrationHandler() throws Exception {
            super();

            //// First service
            // First destination
            Function<String, CompletableFuture<Try<String>>> firstFunc =
                    Pipeline.createPipeline(getAddQueryParam(urlFirst, "idFirst"))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            // Second destination
            Function<String, CompletableFuture<Try<String>>> firstFallbackFunc =
                    Pipeline.createPipeline(getAddQueryParam(urlFirstFallback, "idFirstFallback"))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<Tuple2<Throwable, String>, CompletableFuture<Try<String>>> firstFallbackWrappedFunc = t -> {
                return firstFallbackFunc.apply(t._2());
            };

            Function<Throwable, String> firstRecoverFunc = t -> {
                return "{\"errorMessage\": \"Got some error, recovering first service... Original error: " + t.getMessage() + "\"}";
            };

            // The first composite function that will participate in the final composition
            Function<String, CompletableFuture<Try<String>>> firstCompositeFunc =
                    Pipeline.createAsyncPipeline(firstFunc)
                        .fallbackToEx(firstFallbackWrappedFunc)
                        .recover(firstRecoverFunc)
                        .get();

            //// Second service
            // Second destination
            Function<String, CompletableFuture<Try<String>>> secondFunc =
                    Pipeline.createPipeline(getAddQueryParam(urlSecond, "idSecond"))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            // Second destination
            Function<String, CompletableFuture<Try<String>>> secondFallbackFunc =
                    Pipeline.createPipeline(getAddQueryParam(urlSecondFallback, "idSecondFallback"))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<Tuple2<Throwable, String>, CompletableFuture<Try<String>>> secondFallbackWrappedFunc = t -> {
                return secondFallbackFunc.apply(t._2());
            };

            Function<Throwable, String> secondRecoverFunc = t -> {
                return "{\"errorMessage\": \"Got some error, recovering second service... Original error: " + t.getMessage() + "\"}";
            };

            // The second composite function that will participate in the final composition
            Function<String, CompletableFuture<Try<String>>> secondCompositeFunc =
                    Pipeline.createAsyncPipeline(secondFunc)
                            .fallbackToEx(secondFallbackWrappedFunc)
                            .recover(secondRecoverFunc)
                            .get();

            //// Third service
            // Third destination
            Function<String, CompletableFuture<Try<String>>> thirdFunc =
                    Pipeline.createPipeline(getAddQueryParam(urlThird, "idThird"))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            // Second destination
            Function<String, CompletableFuture<Try<String>>> thirdFallbackFunc =
                    Pipeline.createPipeline(getAddQueryParam(urlThirdFallback, "idThirdFallback"))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<Tuple2<Throwable, String>, CompletableFuture<Try<String>>> thirdFallbackWrappedFunc = t -> {
                return thirdFallbackFunc.apply(t._2());
            };

            Function<Throwable, String> thirdRecoverFunc = t -> {
                return "{\"errorMessage\": \"Got some error, recovering third service... Original error: " + t.getMessage() + "\"}";
            };

            // The third composite function that will participate in the final composition
            Function<String, CompletableFuture<Try<String>>> thirdCompositeFunc =
                    Pipeline.createAsyncPipeline(thirdFunc)
                            .fallbackToEx(thirdFallbackWrappedFunc)
                            .recover(thirdRecoverFunc)
                            .get();

            // Composing all three services - execute in parallel for example
            // Convert a single parameter to a tuple3 of the same parameter
            Function<String, Tuple3<String, String, String>> multiplyParameterFunc = a -> {
                return tuple3(a, a, a);
            };

            // Aggregate the result from the three functions execution
            Function<Tuple3<String, String, String>, String> aggregateResultFunc = a -> {
                return "[" + a._1() + ", " + a._2() + ", " + a._3() + "]";
            };

            // Complete composite function
            pipelineFunc = Pipeline.createPipeline(lift(multiplyParameterFunc))
                    .pipeToAsync(tryPar(firstCompositeFunc, secondCompositeFunc, thirdCompositeFunc))
                    .pipeToSync(lift(aggregateResultFunc))
                    .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            String paramId = asyncContext.getRequest().getParameter("id");
            String queryId = StringUtil.isBlank(paramId) ? defaultQueryId : paramId;

            CompletableFuture<Try<String>> result = pipelineFunc.apply(queryId);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    public static class ComplexOrchestrationHandlerOptimized extends AsyncHandler {
        private String defaultQueryId = "100";
        private String urlFirst = "http://localhost:9901/v1";
        private String urlFirstFallback = "http://localhost:9902/v1";
        private String urlSecond = "http://localhost:9903/v1";
        private String urlSecondFallback = "http://localhost:9904/v1";
        private String urlThird = "http://localhost:9905/v1";
        private String urlThirdFallback = "http://localhost:9906/v1";

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineFunc;

        // Factory method
        public static ComplexOrchestrationHandlerOptimized complexOrchestrationHandlerOptimized() throws Exception {
            return new ComplexOrchestrationHandlerOptimized();
        }

        // Higher-order function that a destination with fallback and recovery
        public Function<String, CompletableFuture<Try<String>>> getDestinationFunc (
            String urlPrimary,
            String queryParamNamePrimary,
            String urlFallback,
            String queryParamNameFallback,
            Function<Request, Try<Request>> authHeaderFunc,
            Function<Throwable, String> recoverFunc
        ) throws Exception {
            // First destination
            Function<String, CompletableFuture<Try<String>>> primaryFunc =
                    Pipeline.createPipeline(getAddQueryParam(urlPrimary, queryParamNamePrimary))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(authHeaderFunc)
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            // Second destination
            Function<String, CompletableFuture<Try<String>>> fallbackFunc =
                    Pipeline.createPipeline(getAddQueryParam(urlFallback, queryParamNameFallback))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToSync(authHeaderFunc)
                            .pipeToAsync(trySendReceive)
                            .pipeToSync(unmarshallToString())
                            .get();

            Function<Tuple2<Throwable, String>, CompletableFuture<Try<String>>> fallbackWrappedFunc = t -> {
                return fallbackFunc.apply(t._2());
            };

            // The first composite function that will participate in the final composition
            Function<String, CompletableFuture<Try<String>>> firstCompositeFunc =
                    Pipeline.createAsyncPipeline(primaryFunc)
                            .fallbackToEx(fallbackWrappedFunc)
                            .recover(recoverFunc)
                            .get();

            return firstCompositeFunc;
        }

        // Constructor
        private ComplexOrchestrationHandlerOptimized() throws Exception {
            super();

            Function<Throwable, String> recoverFunc = t -> {
                return "{\"errorMessage\": \"Got some error, recovering first service... Original error: " + t.getMessage() + "\"}";
            };

            // The first composite function that will participate in the final composition
            Function<String, CompletableFuture<Try<String>>> firstCompositeFunc =
                getDestinationFunc(urlFirst,
                    "idFirst",
                    urlFirstFallback,
                    "idFirstFallback",
                    addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="),
                    recoverFunc);

            // The second composite function that will participate in the final composition
            Function<String, CompletableFuture<Try<String>>> secondCompositeFunc =
                getDestinationFunc(urlSecond,
                    "idSecond",
                    urlSecondFallback,
                    "idSecondFallback",
                    addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="),
                    recoverFunc);

            // The third composite function that will participate in the final composition
            Function<String, CompletableFuture<Try<String>>> thirdCompositeFunc =
                getDestinationFunc(urlThird,
                    "idThird",
                    urlThirdFallback,
                    "idThirdFallback",
                    addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="),
                    recoverFunc);

            // Composing all three services - execute in parallel for example
            // Convert a single parameter to a tuple3 of the same parameter
            Function<String, Tuple3<String, String, String>> multiplyParameterFunc = a -> {
                return tuple3(a, a, a);
            };

            // Aggregate the result from the three functions execution
            Function<Tuple3<String, String, String>, String> aggregateResultFunc = a -> {
                return "[" + a._1() + ", " + a._2() + ", " + a._3() + "]";
            };

            // Complete composite function
            pipelineFunc = Pipeline.createPipeline(lift(multiplyParameterFunc))
                    .pipeToAsync(tryPar(firstCompositeFunc, secondCompositeFunc, thirdCompositeFunc))
                    .pipeToSync(lift(aggregateResultFunc))
                    .get();
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            String paramId = asyncContext.getRequest().getParameter("id");
            String queryId = StringUtil.isBlank(paramId) ? defaultQueryId : paramId;

            CompletableFuture<Try<String>> result = pipelineFunc.apply(queryId);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }

    // Fallback with circuit breaker
    public static class FallbackCircuitBreakerHandler extends AsyncHandler {
        private String defaultQueryId = "100";

        // Stubbed normalization function, in a non-sample implementation will convert the input to a normalized object
        private Function<String, Try<String>> identityFunc = a -> {
            return Try.success(a);
        };

        private Function<String, CompletableFuture<Try<String>>> constructFallbackCircuitBreaker(
                String primaryUrl,
                Function<String, Try<String>> primaryNormalizationFunc,
                String fallbackUrl,
                Function<String, Try<String>> fallbackNormalizationFunc
        ) throws Exception {
            // Construct the thread pools for the circuit breaker
            ExecutorService primaryExecutorService = Executors.newFixedThreadPool(10);
            ExecutorService secondaryExecutorService = Executors.newFixedThreadPool(10);
            ExecutorService logExecutorService = Executors.newFixedThreadPool(5);

            // Construct the circuit breaker state managers
            CircuitBreaker.CircuitBreakerStateManager primaryCircuitBreakerStateManager =
                    CircuitBreaker.CircuitBreakerStateManager.createStateManager(3, Duration.ofSeconds(10), logExecutorService);

            primaryCircuitBreakerStateManager.addClosedListener(() -> System.out.println("Circuit breaker CLOSED"));
            primaryCircuitBreakerStateManager.addOpenListener(() -> System.out.println("Circuit breaker OPEN"));
            primaryCircuitBreakerStateManager.addHalfOpenListener(() -> System.out.println("Circuit breaker HALF-OPEN"));

            // Construct the circuit breaker state managers
            CircuitBreaker.CircuitBreakerStateManager fallbackCircuitBreakerStateManager =
                    CircuitBreaker.CircuitBreakerStateManager.createStateManager(3, Duration.ofSeconds(10), logExecutorService);

            fallbackCircuitBreakerStateManager.addClosedListener(() -> System.out.println("Circuit breaker CLOSED"));
            fallbackCircuitBreakerStateManager.addOpenListener(() -> System.out.println("Circuit breaker OPEN"));
            fallbackCircuitBreakerStateManager.addHalfOpenListener(() -> System.out.println("Circuit breaker HALF-OPEN"));

            // First destination
            Function<String, CompletableFuture<Try<String>>> primaryFunc =
                    Pipeline.createPipeline(getAddQueryParam(primaryUrl, "id"))
                            .pipeToSync(pipelineLog("Constricted primary url: "))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToAsync(withCircuitBreaker(trySendReceive, primaryExecutorService, primaryCircuitBreakerStateManager))
                            .pipeToSync(unmarshallToString())
                            .pipeToSync(primaryNormalizationFunc)
                            .get();

            Function<String, CompletableFuture<Try<String>>> fallbackFunc =
                    Pipeline.createPipeline(getAddQueryParam(fallbackUrl, "id"))
                            .pipeToSync(pipelineLog("Constricted fallback url: "))
                            .pipeToSync(tryConstructGet.apply(getHttpClient()))
                            .pipeToAsync(withCircuitBreaker(trySendReceive, secondaryExecutorService, fallbackCircuitBreakerStateManager))
                            .pipeToSync(unmarshallToString())
                            .pipeToSync(fallbackNormalizationFunc)
                            .get();

            Function<Tuple2<Throwable, String>, CompletableFuture<Try<String>>> fallbackWrappedFunc = t -> {
                return fallbackFunc.apply(t._2());
            };

            // The complete composite function
            Function<String, CompletableFuture<Try<String>>> compositeFunc =
                    Pipeline.createAsyncPipeline(primaryFunc)
                            .fallbackToEx(fallbackWrappedFunc)
                            .get();

            return compositeFunc;
        }

        // Pipeline function that does everything
        private Function<String, CompletableFuture<Try<String>>> pipelineFunc;

        // Factory method
        public static FallbackCircuitBreakerHandler fallbackCircuitBreakerHandler(String url, String fallbackUrl) throws Exception {
            return new FallbackCircuitBreakerHandler(url, fallbackUrl);
        }

        // Constructor
        private FallbackCircuitBreakerHandler(String url, String fallbackUrl) throws Exception {
            pipelineFunc = constructFallbackCircuitBreaker(url, identityFunc, fallbackUrl, identityFunc);
        }

        @Override
        public void handle(AsyncContext asyncContext) {
            String paramId = asyncContext.getRequest().getParameter("id");
            String queryId = StringUtil.isBlank(paramId) ? defaultQueryId : paramId;

            CompletableFuture<Try<String>> result = pipelineFunc.apply(queryId);
            result.thenAccept(getAsyncResponseFunction(asyncContext, "application/json"));
        }
    }
}
