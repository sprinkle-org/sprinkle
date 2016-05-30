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

import org.sprinkle.TriFunction;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentProvider;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpMethod;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.sprinkle.Pipeline.lift;
import static org.sprinkle.Pipeline.liftFuture;
import static org.sprinkle.Pipeline.liftFutureBiFunction;
import static org.sprinkle.Tuple.*;

/**
 * Created by mboussarov on 6/23/15.
 *
 * Http methods as completable futures and monads
 */
public class Http {
    // Function that constructs the HTTP Client

    // Fully non-blocking
    public static CompletableFuture<Tuple2<Response, StringBuffer>> sendReceive(Request request){
        // Construct an empty future/promise
        final CompletableFuture<Tuple2<Response, StringBuffer>> future = new CompletableFuture<>();

        Charset charset = Charset.defaultCharset();
        StringBuffer body = new StringBuffer();

        // Deal with the processing
        request.onResponseContent((response, buffer) -> {
                    body.append(charset.decode(buffer));
                })
                .send(result -> {
                    if(result.isFailed()){
                        // Failure
                        Throwable exception = result.getFailure();
                        if(exception != null) {
                            future.completeExceptionally(exception);
                        } else {
                            future.completeExceptionally(new RuntimeException("Unknown exception on http request"));
                        }
                    } else {
                        // Success
                        future.complete(tuple2(result.getResponse(), body));
                    }
                });

        // Return the future
        return future;
    }

    // Send the request, possibly in chinks to a receiver
    public static CompletableFuture<Response> sendTo(Request request, Response.Listener listener){
        // Construct an empty future/promise
        final CompletableFuture<Response> future = new CompletableFuture<>();

        // Complete the future once everything is done, however, leave to the listener to handle all the details
        request.onComplete(result -> {
                                if(result.isFailed()){
                                    // Failure
                                    Throwable exception = result.getFailure();
                                    if(exception != null) {
                                        future.completeExceptionally(exception);
                                    } else {
                                        future.completeExceptionally(new RuntimeException("Unknown exception on http request"));
                                    }
                                } else {
                                    // Success
                                    future.complete(result.getResponse());
                                }
                            })
                .send(listener);

        return future;
    }

    // Lift to benefit from the Try monad
    public static Function<Request, CompletableFuture<Try<Tuple2<Response, StringBuffer>>>> trySendReceive = liftFuture(Http::sendReceive);

    // Lift to benefit from the Try monad
    public static BiFunction<Request, Response.Listener, CompletableFuture<Try<Response>>> trySendTo = liftFutureBiFunction(Http::sendTo);

    // Generic functions
    // Regular no-body, no content function like GET
    public static Request requestOp(HttpMethod httpMethod, HttpClient httpClient, String url) {
        Request request = httpClient.newRequest(url);
        request.method(httpMethod);
        return request;
    }

    // Regular body function like POST, PUT etc.
    public static Request requestOpBody(HttpMethod httpMethod, HttpClient httpClient, String url, ContentProvider provider, String contentType) {
        Request request = httpClient.newRequest(url);
        request.content(provider, contentType);
        request.method(httpMethod);
        return request;
    }

    // Regular GET function
    public static Request getOp(HttpClient httpClient, String url){
        return requestOp(HttpMethod.GET, httpClient, url);
    }

    // Regular POST function
    public static Request postOp(HttpClient httpClient, String url, ContentProvider provider, String contentType) {
        return requestOpBody(HttpMethod.POST, httpClient, url, provider, contentType);
    }

    // Regular POST function
    public static Request putOp(HttpClient httpClient, String url, ContentProvider provider, String contentType) {
        return requestOpBody(HttpMethod.PUT, httpClient, url, provider, contentType);
    }

    // Regular DELETE function
    public static Request deleteOp(HttpClient httpClient, String url, ContentProvider provider, String contentType) {
        return requestOpBody(HttpMethod.DELETE, httpClient, url, provider, contentType);
    }

    // Curried functions
    // Curried function for HTTP GET
    public static Function<HttpClient, Function<String, Request>> constructGet = httpClient -> {
        return url -> getOp(httpClient, url);
    };

    public static Function<HttpClient, Function<String, Try<Request>>> tryConstructGet = httpClient -> {
        return lift(url -> getOp(httpClient, url));
    };

    // Construct any HTTP method with no body
    public static Function<HttpClient, Try<Request>> constructWithNoBody(String url, HttpMethod httpMethod) {
        return httpClient -> {
            Request request = httpClient.newRequest(url)
                    .method(httpMethod);

            return Try.success(request);
        };
    }

    // Construct any HTTP method with body
    public static Function<HttpClient, Try<Request>> constructWithBody(String url, ContentProvider provider, String contentType, HttpMethod httpMethod) {
        return httpClient -> {
            Request request = httpClient.newRequest(url)
                    .content(provider, contentType)
                    .method(httpMethod);
            return Try.success(request);
        };
    }

    // Same as above, just uses the content provider as a parameter
    public static Function<ContentProvider, Try<Request>> constructWithBody(String url, HttpClient httpClient, String contentType, HttpMethod httpMethod) {
        return contentProvider -> {
            Request request = httpClient.newRequest(url)
                    .content(contentProvider, contentType)
                    .method(httpMethod);
            return Try.success(request);
        };
    }

    // Construct a get request function
    public static Function<HttpClient, Try<Request>> constructGet(String url) {
        return constructWithNoBody(url, HttpMethod.GET);
    }

    // Curried function for HTTP POST
    public static Function<HttpClient, TriFunction<String, ContentProvider, String, Request>> constructPost = httpClient -> {
        return (url, provider, contentType) -> postOp(httpClient, url, provider, contentType);
    };

    // Construct a post request function
    public static Function<HttpClient, Try<Request>> tryConstructPost(String url, ContentProvider provider, String contentType) {
        return constructWithBody(url, provider, contentType, HttpMethod.POST);
    }

    // Construct a post request function, where the parameter is the content provider
    public static Function<ContentProvider, Try<Request>> tryConstructPost(String url, HttpClient httpClient, String contentType) {
        return constructWithBody(url, httpClient, contentType, HttpMethod.POST);
    }

    // Curried function for HTTP PUT
    public static Function<HttpClient, TriFunction<String, ContentProvider, String, Request>> constructPut = httpClient -> {
        return (url, provider, contentType) -> putOp(httpClient, url, provider, contentType);
    };

    // Construct a put request function
    public static Function<HttpClient, Try<Request>> constructPut(String url, ContentProvider provider, String contentType) {
        return constructWithBody(url, provider, contentType, HttpMethod.PUT);
    }

    // Curried function for HTTP DELETE
    public static Function<HttpClient, TriFunction<String, ContentProvider, String, Request>> constructDelete = httpClient -> {
        return (url, provider, contentType) -> deleteOp(httpClient, url, provider, contentType);
    };

    // Construct a delete request function
    public static Function<HttpClient, Try<Request>> constructDelete(String url, ContentProvider provider, String contentType) {
        return constructWithBody(url, provider, contentType, HttpMethod.DELETE);
    }

    // Utility functions
    public static Function<Request, Try<Request>> addHeaders(Map<String, String> headers){
        return a -> {
            // Internal mutable variable
            Request request = a;

            for(Map.Entry<String, String> header : headers.entrySet()){
                request = request.header(header.getKey(), header.getValue());
            }

            return Try.success(request);
        };
    }

    public static Function<Request, Try<Request>> addHeader(String header, String value){
        return a -> {
            return Try.success(a.header(header, value));
        };
    }

    public static Function<Tuple2<Response, StringBuffer>, Try<String>> unmarshallToString(){
        return a -> {
            return Try.success(a._2().toString());
        };
    }
}
