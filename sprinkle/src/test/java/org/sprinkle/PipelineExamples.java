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
package org.sprinkle;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.junit.Test;
import org.junit.Ignore;
import org.sprinkle.Pipeline;
import org.sprinkle.Try;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.sprinkle.Http.*;
import static org.sprinkle.Pipeline.lift;
import static org.sprinkle.CacheTool.*;
import static org.sprinkle.Pipeline.pipelineLog;
import static org.sprinkle.Pipeline.tryPar;
import static org.sprinkle.Tuple.*;
import static org.sprinkle.CircuitBreaker.*;

/**
 * Created by mboussarov on 6/24/15.
 */
public class PipelineExamples {
    @Ignore
    @Test
    public void testPipelinePost() throws Exception {
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        ContentProvider provider = new StringContentProvider("{\"id\"=\"100\"}");

        //...and the completable future that makes the call
        Function<HttpClient, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.createPipeline(tryConstructPost("http://localhost:9903/v1", provider, "application/json"))
                        .<Request>pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .get();

        CompletableFuture<Try<String>> result = pipelineFunc.apply(httpClient);

        result.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });

        Thread.sleep(5000);
    }

    @Ignore
    @Test
    public void testPipeline() throws Exception {
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        //...and a get function that depends on this client
        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the get request...
        Request getRequest = get.apply("http://localhost:9903/v1");

        //...and the completable future that makes the call
        Function<Request, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.<Request, Request>createPipeline(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                         .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                         .<String>pipeToSync(unmarshallToString())
                         .get();

        CompletableFuture<Try<String>> result = pipelineFunc.apply(getRequest);

        result.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });

        Thread.sleep(5000);
    }

    @Ignore
    @Test
    public void testCache() throws Exception {
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        //...and a get function that depends on this client
        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the pipeline
        Function<String, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.<String, Request>createPipeline(lift(get))
                        .<Request>pipeToSync(pipelineLog("Pipeline executed"))
                        .<Request>pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .get();

        // Construct the cache
        CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES);

        Cache<String, CompletableFuture<Try<String>>> cache = constructCache(cacheBuilder, pipelineFunc);
        Function<String, CompletableFuture<Try<String>>> cacheGet = withCache(cache);

        // Get the value - makes the call
        CompletableFuture<Try<String>> future1 = cacheGet.apply("http://localhost:9903/v1");

        // Get the value - retrieve from the cache
        CompletableFuture<Try<String>> future2 = cacheGet.apply("http://localhost:9903/v1");

        // Get the value - retrieve from the cache
        CompletableFuture<Try<String>> future3 = cacheGet.apply("http://localhost:9903/v1");

        // Get the value - makes the call
        CompletableFuture<Try<String>> future4 = cacheGet.apply("http://localhost:9904/v1");

        // Just print the result
        future1.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success future1: " + a.get());
            } else {
                // Failure
                System.out.println("Failure future1: " + a.getException().toString());
            }
        });

        future2.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success future2: " + a.get());
            } else {
                // Failure
                System.out.println("Failure future2: " + a.getException().toString());
            }
        });

        future3.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success future3: " + a.get());
            } else {
                // Failure
                System.out.println("Failure future3: " + a.getException().toString());
            }
        });

        future4.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success future4: " + a.get());
            } else {
                // Failure
                System.out.println("Failure future4: " + a.getException().toString());
            }
        });

        Thread.sleep(5000);
    }

    @Ignore
    @Test
    public void testCacheHidden() throws Exception {
        // Yet another way of using the cache..

        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        //...and a get function that depends on this client
        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the cache
        CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES);

        // Construct the pipeline
        Function<String, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.createPipeline(lift(get))
                        .pipeToSync(pipelineLog("Pipeline executed"))
                        .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .pipeToAsync(withCache(cacheBuilder, trySendReceive))
                        .pipeToSync(unmarshallToString())
                        .get();

        // Will make the call
        CompletableFuture<Try<String>> result1 = pipelineFunc.apply("http://localhost:9903/v1");
        // Will retrieve from cache
        CompletableFuture<Try<String>> result2 = pipelineFunc.apply("http://localhost:9903/v1");

        result1.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });

        result2.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });

        Thread.sleep(5000);
    }

    @Ignore
    @Test
    public void testCircuitBreaker() throws Exception {
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();

        httpClient.setMaxRequestsQueuedPerDestination(10000);
        httpClient.setMaxConnectionsPerDestination(10000);
        httpClient.setConnectTimeout(1000);

        httpClient.setIdleTimeout(60000);
        httpClient.start();

        //...and a get function that depends on this client
        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the circuit breaker
        ExecutorService executorService = Executors.newFixedThreadPool(20);

        CircuitBreakerStateManager circuitBreakerStateManager =
                CircuitBreakerStateManager.createStateManager(10, Duration.ofSeconds(5), executorService);

        circuitBreakerStateManager.addClosedListener(() -> System.out.println("Circuit breaker CLOSED"));
        circuitBreakerStateManager.addOpenListener(() -> System.out.println("Circuit breaker OPEN"));
        circuitBreakerStateManager.addHalfOpenListener(() -> System.out.println("Circuit breaker HALF-OPEN"));

        //...and the completable future that makes the call
        Function<Request, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.<Request, Request>createPipeline(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(withCircuitBreaker(trySendReceive, executorService, circuitBreakerStateManager))
                        .<String>pipeToSync(unmarshallToString())
                        .get();

        for(int i = 0; i <= 10000; i++){
            // Start 500 threads at a time
            if(i > 0 && i % 500 == 0){
                System.out.println("> Executed " + i + " requests...");
                Thread.sleep(1000);
            }

            CompletableFuture<Try<String>> result = pipelineFunc.apply(get.apply("http://localhost:9903/v1"));

            result.thenAccept(a -> {
                if(a.isSuccess()){
                    System.out.println("Success: " + a.get());
                } else {
                    // Failure
                    System.out.println("Failure: " + a.getException().toString());
                }
            });
        }

        Thread.sleep(10000);
    }

    @Ignore
    @Test
    public void testAsyncRecover() throws Exception {
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        //...and a get function that depends on this client
        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the get request...
        Request getRequest = get.apply("http://localhost:9901/v1");

        Function<Throwable, String> recoverFunc = t -> {
            if(t instanceof TimeoutException){
                // Look for a particular exception
                return "{\"errorMessage\": \"Got TimeoutException, trying to recover...\"}";
            } else {
                // Must have a catch all exception
                return "{\"errorMessage\": \"Got some error, trying to recover...\"}";
            }
        };

        //...and the completable future that makes the call
        Function<Request, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.<Request, Request>createPipeline(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .recover(recoverFunc)
                        .get();

        CompletableFuture<Try<String>> result = pipelineFunc.apply(getRequest);

        result.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });

        Thread.sleep(5000);
    }

    @Ignore
    @Test
    public void testAsyncRecoverWith() throws Exception {
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        //...and a get function that depends on this client
        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the get request...
        Request getRequest = get.apply("http://localhost:9901/v1");

        Function<Throwable, Try<String>> recoverWithFunc = t -> {
            if(t instanceof TimeoutException){
                // Look for a particular exception
                return Try.success("{\"action\": \"Convert error to success value...\"}");
            } else {
                // Must have a catch all exception
                return Try.failure(new RuntimeException("{\"errorMessage\": \"Convert error to some other error...\"}"));
            }
        };

        //...and the completable future that makes the call
        Function<Request, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.<Request, Request>createPipeline(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .recoverWith(recoverWithFunc)
                        .get();

        CompletableFuture<Try<String>> result = pipelineFunc.apply(getRequest);

        result.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });

        Thread.sleep(5000);
    }

    @Ignore
    @Test
    public void testFallbackTo() throws Exception {
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        //...and a get function that depends on this client
        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the get request...
        Request getRequestFailure = get.apply("http://localhost:9901/v1");
        Request getRequestSuccess = get.apply("http://localhost:9903/v1");

        //...and the completable future that makes the call
        Function<Request, CompletableFuture<Try<String>>> pipelineFunc =
                Pipeline.<Request, Request>createPipeline(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .get();

        Function<Throwable, CompletableFuture<Try<String>>> fallbackToFunc = t -> {
            return pipelineFunc.apply(getRequestSuccess);
        };

        Function<Request, CompletableFuture<Try<String>>> pipelineFallbackTo =
                Pipeline.createAsyncPipeline(pipelineFunc)
                        .fallbackTo(fallbackToFunc)
                        .get();

        CompletableFuture<Try<String>> result = pipelineFallbackTo.apply(getRequestFailure);

        result.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });

        Thread.sleep(5000);
    }

    @Ignore
    @Test
    public void testMultiple() throws Exception {
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        //...and a get function that depends on this client
        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the get request...
        String urlFirst = "http://localhost:9907/v1";
        String urlSecond = "http://localhost:9903/v1";
        String urlThird = "http://localhost:9905/v1";

        //...and the completable future that makes the call
        Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
                Pipeline.<String, Request>createPipeline(lift(get))
                        .<Request>pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .get();

        Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
                Pipeline.<String, Request>createPipeline(lift(get))
                        .<Request>pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .get();

        Function<String, CompletableFuture<Try<String>>> pipelineFuncThird =
                Pipeline.<String, Request>createPipeline(lift(get))
                        .<Request>pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .get();

        Function<String, CompletableFuture<Try<Tuple3<String, String, String>>>> compositeFunction =
                Pipeline.createAsyncPipeline(pipelineFuncFirst)
                    .pipeToAsync(s -> {
                        return composeTuple2(s, pipelineFuncSecond.apply(urlSecond));
                    })
                    .pipeToAsync(t -> {
                        return composeTuple3(t, pipelineFuncThird.apply(urlThird));
                    })
                    .get();

        Function<String, CompletableFuture<Try<Tuple2<String, String>>>> innerFunc2 = s -> {
            return composeTuple2(s, pipelineFuncSecond.apply(urlSecond));
        };

        Function<Tuple2<String, String>, CompletableFuture<Try<Tuple3<String, String, String>>>> innerFunc3 = t -> {
            return composeTuple3(t, pipelineFuncThird.apply(urlThird));
        };

        Function<String, CompletableFuture<Try<Tuple3<String, String, String>>>> compositeFunction2 =
                Pipeline.createAsyncPipeline(pipelineFuncFirst)
                        .pipeToAsync(innerFunc2)
                        .pipeToAsync(innerFunc3)
                        .get();

        CompletableFuture<Try<Tuple3<String, String, String>>> result = compositeFunction.apply(urlFirst);

        result.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });

        CompletableFuture<Try<Tuple3<String, String, String>>> result2 = compositeFunction2.apply(urlFirst);

        result2.thenAccept(a -> {
            if(a.isSuccess()){
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException().toString());
            }
        });

        Thread.sleep(5000);
    }

    @Ignore
    @Test
    public void testMultipleWithCircuitBreaker() throws Exception {
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        //...and a get function that depends on this client
        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the get request...
        String urlFirst = "http://localhost:9901/v1";
        String urlSecond = "http://localhost:9903/v1";

        // Functions to compose
        Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
                Pipeline.<String, Request>createPipeline(lift(get))
                        .<Request>pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .get();

        Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
                Pipeline.<String, Request>createPipeline(lift(get))
                        .<Request>pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .get();

        Function<Throwable, CompletableFuture<Try<String>>> recoverFunction = t -> {
            return pipelineFuncSecond.apply(urlSecond);
        };

        // Circuit breaker definitions
        // Construct the circuit breaker
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        CircuitBreakerStateManager circuitBreakerStateManager =
                CircuitBreakerStateManager.createStateManager(5, Duration.ofSeconds(5), executorService);

        circuitBreakerStateManager.addClosedListener(() -> System.out.println("Circuit breaker CLOSED"));
        circuitBreakerStateManager.addOpenListener(() -> System.out.println("Circuit breaker OPEN"));
        circuitBreakerStateManager.addHalfOpenListener(() -> System.out.println("Circuit breaker HALF-OPEN"));

        // Composite function
        Function<String, CompletableFuture<Try<String>>> compositeFunction =
                Pipeline.createAsyncPipeline(withCircuitBreaker(pipelineFuncFirst, executorService, circuitBreakerStateManager))
                        .fallbackTo(recoverFunction)
                        .get();

        for(int i = 0; i <=100; i++) {
            CompletableFuture<Try<String>> result = compositeFunction.apply(urlFirst);

            result.thenAccept(a -> {
                if (a.isSuccess()) {
                    System.out.println("Success: " + a.get());
                } else {
                    // Failure
                    System.out.println("Failure: " + a.getException().toString());
                }
            });

            Thread.sleep(200);
        }

        Thread.sleep(5000);
    }

    @Ignore
    @Test
    public void testParallel() throws Exception {
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        //...and a get function that depends on this client
        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the get request...
        String urlFirst = "http://localhost:9907/v1";
        String urlSecond = "http://localhost:9903/v1";
        String urlThird = "http://localhost:9905/v1";

        //...and the completable futures that makes the call
        Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
                Pipeline.<String, Request>createPipeline(lift(get))
                        .<Request>pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .get();

        Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
                Pipeline.<String, Request>createPipeline(lift(get))
                        .<Request>pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .get();

        Function<String, CompletableFuture<Try<String>>> pipelineFuncThird =
                Pipeline.<String, Request>createPipeline(lift(get))
                        .<Request>pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .<Tuple2<Response, StringBuffer>>pipeToAsync(trySendReceive)
                        .<String>pipeToSync(unmarshallToString())
                        .get();

        // Create the composite parallel function
        Function<Tuple3<String, String, String>, CompletableFuture<Tuple3<Try<String>, Try<String>, Try<String>>>> parFunc =
                Pipeline.par(pipelineFuncFirst, pipelineFuncSecond, pipelineFuncThird);

        CompletableFuture<Tuple3<Try<String>, Try<String>, Try<String>>> result = parFunc.apply(tuple3(urlFirst, urlSecond, urlThird));

        result.thenAccept(a -> {
            System.out.println("Result: ");
            System.out.println("1." + a._1().getOrElse(""));
            System.out.println("2." + a._2().getOrElse(""));
            System.out.println("3." + a._3().getOrElse(""));
        });

        Thread.sleep(5000);
    }

    @Ignore
    @Test
    public void testParallelAndCompose() throws Exception {
        // Create an async HTTP client...
        HttpClient httpClient = new HttpClient();
        httpClient.start();

        //...and a get function that depends on this client
        Function<String, Request> get = constructGet.apply(httpClient);

        // Construct the get request...
        String urlFirst = "http://localhost:9907/v1";
        String urlSecond = "http://localhost:9903/v1";
        String urlThird = "http://localhost:9905/v1";

        //...and the completable futures that makes the call
        Function<String, CompletableFuture<Try<String>>> pipelineFuncFirst =
                Pipeline.createPipeline(lift(get))
                        .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .pipeToAsync(trySendReceive)
                        .pipeToSync(unmarshallToString())
                        .get();

        Function<String, CompletableFuture<Try<String>>> pipelineFuncSecond =
                Pipeline.createPipeline(lift(get))
                        .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .pipeToAsync(trySendReceive)
                        .pipeToSync(unmarshallToString())
                        .get();

        Function<String, CompletableFuture<Try<String>>> pipelineFuncThird =
                Pipeline.createPipeline(lift(get))
                        .pipeToSync(addHeader("Authorization", "Basic dXNlcm5hbWU6cGFzc3dvcmQ="))
                        .pipeToAsync(trySendReceive)
                        .pipeToSync(unmarshallToString())
                        .get();

        // Create the parallel function
        Function<Tuple3<String, String, String>, CompletableFuture<Try<Tuple3<String, String, String>>>> parFunc =
                Pipeline.tryPar(pipelineFuncFirst, pipelineFuncSecond, pipelineFuncThird);

        Function<Tuple3<String, String, String>, String> aggFunc = a -> {
            return a._1() + "; " + a._2() + "; " + a._3();
        };

        // Create the final composite function
        Function<Tuple3<String, String, String>, CompletableFuture<Try<String>>> compositeFunction =
                Pipeline.createAsyncPipeline(parFunc)
                        .pipeToSync(lift(aggFunc))
                        .get();

        CompletableFuture<Try<String>> result = compositeFunction.apply(tuple3(urlFirst, urlSecond, urlThird));

        result.thenAccept(a -> {
            if (a.isSuccess()) {
                System.out.println("Success: " + a.get());
            } else {
                // Failure
                System.out.println("Failure: " + a.getException());
            }
        });

        Thread.sleep(5000);
    }
}
