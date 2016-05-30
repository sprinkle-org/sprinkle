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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.sprinkle.Tuple.*;

/**
 * Created by mboussarov on 11/25/15.
 *
 * Patterns and other utility functions for the main pipeline
 */
public class PipelineEx {
    /**
     * Generic recursive fold function, can be used for completable futures
     *
     * Here is an example on how to use it
     *  List<T> -> List<AsyncPolicy> asyncPolicies
     *  U -> CompletableFuture<Try<Context>>
     *  function:
     *  public CompletableFuture<Try<Context>> processPolicy(AsyncPolicy asyncPolicy, CompletableFuture<Try<Context>> tryContext){
     *  CompletableFuture<Try<Context>> promise = new CompletableFuture<>();
     *  tryContext.thenAccept(a -> {
     *            if(a.isSuccess()){
     *                Context context = a.get();
     *
     *                if(asyncPolicy.isActive(context)){
     *                    // If the promise is active - execute it
     *                    CompletableFuture<Try<Context>> asyncPolicyExec = asyncPolicy.applyPolicy(context);
     *                    asyncPolicyExec.thenAccept(a -> promise.complete(a));
     *                } else{
     *                    // If not active - just skip it and return the original value
     *                    promise.complete(a);
     *                }
     *            } else {
     *                // This is a failure
     *                promise.complete(a);
     *            }
     *        }
     *    );
     *    return promise;
     *  }
     *
     *  How to use the above
     *  On the input we have asyncPolicies, initialContext and the function from above
     *  CompletableFuture<Try<Context>> initialContextFuture = new CompletableFuture<>();
     *  initialContextFuture.complete(Try.success(initialContext));
     *
     *  CompletableFuture<Try<Context>> result = foldLeft(asyncPolicies, initialContextFuture, WhateverClass::processPolicy);
     *
     * @param list
     * @param zeroValue
     * @param function
     * @param <T>
     * @param <U>
     * @return
     */
    // Generic recursive fold function, can be used for completable futures
    public static <T, U> U foldLeft(List<T> list, U zeroValue, BiFunction<T, U, U> function) {
        if (list.isEmpty()) {
            return zeroValue;
        } else {
            return foldLeft(list.subList(1, list.size()), function.apply(list.get(0), zeroValue), function);
        }
    }

    /**
     * A function that implements a truly common pattern - when a container object caries some information,
     * and some of this info is required to do some work, and once completed the result can be used to enrich
     * the original container
     *
     * @param extract
     * @param process
     * @param enrich
     * @param <T>
     * @param <U>
     * @param <V>
     * @param <R>
     * @return
     */
    public static <T, U, V, R> Function<T, CompletableFuture<Try<R>>> extractProcessEnrich(Function<T, Try<U>> extract,
                                                                                           Function<U, CompletableFuture<Try<V>>> process,
                                                                                           Function<Tuple2<T, V>, Try<R>> enrich) {
        Function<T, Try<Tuple2<T, U>>> extractPlus = a -> {
            return extract.apply(a)
                    .flatMap(b -> Try.success(tuple2(a, b)));
        };

        Function<Tuple2<T, U>, CompletableFuture<Try<Tuple2<T, V>>>> processPlus = a -> {
            final CompletableFuture<Try<Tuple2<T, V>>> promise = new CompletableFuture<>();

            CompletableFuture<Try<V>> future = process.apply(a._2());

            future.thenAccept(r -> {
                if(r.isSuccess()) {
                    promise.complete(Try.success(tuple2(a._1(), r.get())));
                } else {
                    promise.complete(Try.failure(r.getException()));
                }
            });
            future.exceptionally(ex -> {
                promise.complete(Try.failure(ex));
                return null; // Don't get it, why is this needed?
            });

            return promise;
        };

        return Pipeline.createPipeline(extractPlus)
                .pipeToAsync(processPlus)
                .pipeToSync(enrich)
                .get();
    }

    /**
     * Shorter version of the above
     *
     * @param extract
     * @param process
     * @param enrich
     * @param <T>
     * @param <U>
     * @param <V>
     * @param <R>
     * @return
     */
    public static <T, U, V, R> Function<T, CompletableFuture<Try<R>>> xpe(Function<T, Try<U>> extract,
                                                                          Function<U, CompletableFuture<Try<V>>> process,
                                                                          Function<Tuple2<T, V>, Try<R>> enrich) {
        return extractProcessEnrich(extract, process, enrich);
    }

    /**
     * Works like switch/case statements in Java - executes the first function where the matching predicate returns "true"
     *
     * @param cases
     * @param <T>
     * @param <R>
     * @return
     */
    public static <T, R> Function<T, CompletableFuture<Try<R>>> switchCase(List<Tuple2<Predicate<T>, Function<T, CompletableFuture<Try<R>>>>> cases, Throwable notFoundException){
        // Define the default error response
        CompletableFuture<Try<R>> errorResponse = new CompletableFuture<>();
        errorResponse.complete(Try.failure(notFoundException));

        // Construct the function
        return a -> {
            for(Tuple2<Predicate<T>, Function<T, CompletableFuture<Try<R>>>> caseEntity: cases){
                if(caseEntity._1().test(a)){
                    return caseEntity._2().apply(a);
                }
            }

            // If no match found - return an error
            return errorResponse;
        };
    }

    /**
     * Process a sequence of completable futures, use a predicates to determine if they have to be executed or skipped
     *
     * @param sequence
     * @param <T>
     * @return
     */
    public static <T> Function<T, CompletableFuture<Try<T>>> processSequence(List<Tuple2<Predicate<T>, Function<T, CompletableFuture<Try<T>>>>> sequence){
        BiFunction<Tuple2<Predicate<T>, Function<T, CompletableFuture<Try<T>>>>, CompletableFuture<Try<T>>, CompletableFuture<Try<T>>> innerFunc = (a, b) -> {
            CompletableFuture<Try<T>> promise = new CompletableFuture<>();

            b.thenAccept(c -> {
                if(c.isSuccess()){
                    T value = c.get();

                    if(a._1().test(value)){
                        // Apply the function that corresponds to the predicate - TODO verify this!!!
                        a._2().apply(value).thenAccept(d -> {
                            promise.complete(d);
                        });
                    } else {
                        // Just propagate the original value
                        promise.complete(c);
                    }

                } else {
                    // "c" is a failure, just propagate it
                    promise.complete(c);
                }
            });

            return promise;
        };

        return a -> {
            // Define the initial value
            CompletableFuture<Try<T>> zeroValue = new CompletableFuture<>();
            zeroValue.complete(Try.success(a));

            return foldLeft(sequence, zeroValue, innerFunc);
        };
    }

    /**
     * Convert the output from a function to some other format, presumably normalized form
     *
     * @param function
     * @param normalizerFunc
     * @param <T>
     * @param <R>
     * @param <RR>
     * @return
     */
    public static <T, R, RR> Function<T, CompletableFuture<Try<RR>>> normalize(Function<T, CompletableFuture<Try<R>>> function, Function<R, Try<RR>> normalizerFunc){
        return a -> {
            CompletableFuture<Try<RR>> promise = new CompletableFuture<>();

            function.apply(a).thenAccept(b -> {
                if(b.isSuccess()){
                    // Apply the normalization function on the output of the main one
                    promise.complete(normalizerFunc.apply(b.get()));
                } else {
                    // Failed, propagate the error
                    promise.complete(Try.failure(b.getException()));
                }
            });

            return promise;
        };
    }
}
