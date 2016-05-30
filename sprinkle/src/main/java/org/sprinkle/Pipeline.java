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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.sprinkle.Tuple.*;

/**
 * Created by mboussarov on 6/23/15.
 *
 * The main class for building pipeline monadic flows of independent unrelated functions
 */

public abstract class Pipeline<AA, BB> {
    /**
     * Create a synchronous pipeline object by wrapping an initial function
     *
     * @param function
     * @param <A>
     * @param <T>
     * @return
     */
    public static <A, T> SyncPipeline<A, T> createPipeline(Function<A, Try<T>> function) {
        return new SyncPipeline<>(function);
    }

    /**
     * Create an asynchronous pipeline object by wrapping an initial function that returns a completable future
     *
     * @param function
     * @param <A>
     * @param <T>
     * @return
     */
    public static <A, T> AsyncPipeline<A, T> createAsyncPipeline(Function<A, CompletableFuture<Try<T>>> function) {
        return new AsyncPipeline<>(function);
    }

    /**
     * "Bind" monad function
     *
     * @param function
     * @param <CC>
     * @return
     */
    public <CC> Pipeline<AA, CC> flatMap(Function<Function<AA, BB>, Pipeline<AA, CC>> function) {
        return function.apply(get());
    }

    /**
     * Get the wrapped function out of the pipeline monad
     *
     * @return
     */
    public abstract Function<AA, BB> get();

    public static class SyncPipeline<A, T> extends Pipeline<A, Try<T>> {
        private Function<A, Try<T>> wrappedFunction;

        private SyncPipeline(Function<A, Try<T>> function) {
            wrappedFunction = function;
        }

        /**
         * @see Pipeline#get()
         *
         * @return
         */
        @Override
        public Function<A, Try<T>> get() {
            return wrappedFunction;
        }

        /**
         * Bind a sync function to the already wrapped function composition
         *
         * @param function
         * @param <TT>
         * @return
         */
        public <TT> SyncPipeline<A, TT> pipeToSync(Function<T, Try<TT>> function) {
            return (SyncPipeline<A, TT>) flatMap(a -> createPipeline(tryPipe(a, function)));
            // return new SyncPipeline<>(tryPipe(wrappedFunction, function));
        }

        /**
         * Bind an async function to the already wrapped function composition
         *
         * @param function
         * @param <TT>
         * @return
         */
        public <TT> AsyncPipeline<A, TT> pipeToAsync(Function<T, CompletableFuture<Try<TT>>> function) {
            return (AsyncPipeline<A, TT>) flatMap(a -> createAsyncPipeline(tryPipeAsync(a, function)));
        }

        /**
         * Pipe a recover function to the wrapped sync function composition. If the wrapped function succeeds,
         * the recover function is skipped. If it fails, the result is the value of the recover function
         *
         * @param function
         * @return
         */
        public SyncPipeline<A, T> recover(Function<Throwable, T> function) {
            return (SyncPipeline<A, T>) flatMap(a -> createPipeline(tryPipeRecover(a, function)));
        }

        /**
         * The function is the same as recover, the only difference is it gets as a parameter
         * the exception from the previous functions and the original input passed to the previous function
         *
         * @param function
         * @return
         */
        public SyncPipeline<A, T> recoverEx(Function<Tuple2<Throwable, A>, T> function) {
            return (SyncPipeline<A, T>) flatMap(a -> createPipeline(tryPipeRecoverEx(a, function)));
        }

        /**
         * Pipe a recover function to the wrapped sync function composition. If the wrapped function succeeds,
         * the recover function is skipped. If it fails, the result is the value of the recoverWith function.
         * The difference with the recover function is that the result could be a failure again, not necesserely
         * a success value. This allows for example the exception to be transformed
         *
         * @param function
         * @return
         */
        public SyncPipeline<A, T> recoverWith(Function<Throwable, Try<T>> function) {
            return (SyncPipeline<A, T>) flatMap(a -> createPipeline(tryPipeRecoverWith(a, function)));
        }

        /**
         * The same function as recoverWith, the difference is that the function gets the exception and
         * the original input to previous function
         *
         * @param function
         * @return
         */
        public SyncPipeline<A, T> recoverWithEx(Function<Tuple2<Throwable, A>, Try<T>> function) {
            return (SyncPipeline<A, T>) flatMap(a -> createPipeline(tryPipeRecoverWithEx(a, function)));
        }
    }

    public static class AsyncPipeline<A, T> extends Pipeline<A, CompletableFuture<Try<T>>> {
        private Function<A, CompletableFuture<Try<T>>> wrappedFunction;

        private AsyncPipeline(Function<A, CompletableFuture<Try<T>>> function) {
            wrappedFunction = function;
        }

        /**
         * @see Pipeline#get
         *
         * @return
         */
        @Override
        public Function<A, CompletableFuture<Try<T>>> get() {
            return wrappedFunction;
        }

        /**
         * Bind a sync function to the already wrapped function composition
         *
         * @param function
         * @param <TT>
         * @return
         */
        public <TT> AsyncPipeline<A, TT> pipeToSync(Function<T, Try<TT>> function) {
            return (AsyncPipeline<A, TT>) flatMap(a -> createAsyncPipeline(tryAsyncPipe(a, function)));
        }

        /**
         * Bind an async function to the already wrapped function composition
         *
         * @param function
         * @param <TT>
         * @return
         */
        public <TT> AsyncPipeline<A, TT> pipeToAsync(Function<T, CompletableFuture<Try<TT>>> function) {
            return (AsyncPipeline<A, TT>) flatMap(a -> createAsyncPipeline(tryAsyncPipeAsync(a, function)));
        }

        /**
         * Pipe a recover function to the wrapped sync function composition. If the wrapped function succeeds,
         * the recover function is skipped. If it fails, the result is the value of the recover function
         *
         * @param function
         * @return
         */
        public AsyncPipeline<A, T> recover(Function<Throwable, T> function) {
            return (AsyncPipeline<A, T>) flatMap(a -> createAsyncPipeline(tryAsyncPipeAsyncRecover(a, function)));
        }

        /**
         * The function is the same as recover, the only difference is it gets as a parameter
         * the exception from the previous functions and the original input passed to the previous function
         *
         * @param function
         * @return
         */
        public AsyncPipeline<A, T> recoverEx(Function<Tuple2<Throwable, A>, T> function) {
            return (AsyncPipeline<A, T>) flatMap(a -> createAsyncPipeline(tryAsyncPipeAsyncRecoverEx(a, function)));
        }

        /**
         * Pipe a recover function to the wrapped sync function composition. If the wrapped function succeeds,
         * the recover function is skipped. If it fails, the result is the value of the recoverWith function.
         * The difference with the recover function is that the result could be a failure again, not necesserely
         * a success value. This allows for example the exception to be transformed
         *
         * @param function
         * @return
         */
        public AsyncPipeline<A, T> recoverWith(Function<Throwable, Try<T>> function) {
            return (AsyncPipeline<A, T>) flatMap(a -> createAsyncPipeline(tryAsyncPipeAsyncRecoverWith(a, function)));
        }

        /**
         * The same function as recoverWith, the difference is that the function gets the exception and
         * the original input to the previous function
         *
         * @param function
         * @return
         */
        public AsyncPipeline<A, T> recoverWithEx(Function<Tuple2<Throwable, A>, Try<T>> function) {
            return (AsyncPipeline<A, T>) flatMap(a -> createAsyncPipeline(tryAsyncPipeAsyncRecoverWithEx(a, function)));
        }

        /**
         * In case the previous function composition succeeds the fallback is skipped. If it fails,
         * the final result is the output of the fallback function
         *
         * @param function
         * @return
         */
        public AsyncPipeline<A, T> fallbackTo(Function<Throwable, CompletableFuture<Try<T>>> function) {
            return (AsyncPipeline<A, T>) flatMap(a -> createAsyncPipeline(tryAsyncPipeAsyncFallbackTo(a, function)));
        }

        /**
         * The same function as fallbackTo, the only difference is function gets as parameters the exception
         * and the original input to the previous function
         *
         * @param function
         * @return
         */
        public AsyncPipeline<A, T> fallbackToEx(Function<Tuple2<Throwable, A>, CompletableFuture<Try<T>>> function) {
            return (AsyncPipeline<A, T>) flatMap(a -> createAsyncPipeline(tryAsyncPipeAsyncFallbackToEx(a, function)));
        }
    }

    // Static utility functions
    // Functions to be deprecated
    public static <A, B, C> Function<A, C> pipe(Function<A, B> first, Function<B, C> second) {
        return first.andThen(second);
    }

    public static <A, B, C> Function<A, CompletableFuture<C>> pipeAsync(Function<A, B> first, Function<B, CompletableFuture<C>> second) {
        return first.andThen(second);
    }

    public static <A, B, C> Function<A, CompletableFuture<C>> asyncPipeAsync(Function<A, CompletableFuture<B>> first, Function<B, CompletableFuture<C>> second) {
        return a -> first.apply(a).thenCompose(second);
    }

    public static <A, B, C> Function<A, CompletableFuture<C>> asyncPipe(Function<A, CompletableFuture<B>> first, Function<B, C> second) {
        return a -> first.apply(a).thenApply(second);
    }

    /**
     * A classic functional programming "lift" function -
     *
     * @param func
     * @param <A>
     * @param <B>
     * @return
     */
    public static <A, B> Function<A, Try<B>> lift(Function<A, B> func) {
        return a -> {
            try {
                return Try.success(func.apply(a));
            } catch (Exception ex) {
                return Try.failure(ex);
            }
        };
    }

    public static <A, B> Function<A, CompletableFuture<Try<B>>> liftFuture(Function<A, CompletableFuture<B>> func) {
        return a -> {
            final CompletableFuture<Try<B>> promise = new CompletableFuture<>();

            CompletableFuture<B> future = func.apply(a);

            future.thenAccept(b -> promise.complete(Try.success(b)));
            future.exceptionally(ex -> {
                promise.complete(Try.failure((Exception) ex));
                return null; // Java???
            });

            // Alternative implementation
            /*
            func.apply(a).handle((b, ex) -> {
                if(b != null) {
                    promise.complete(Try.success(b));
                } else {
                    promise.complete(Try.failure((Exception)ex));
                }
                return null;
            });
            */

            return promise;
        };
    }

    // Lift a bi-function
    public static <A, B, R> BiFunction<A, B, CompletableFuture<Try<R>>> liftFutureBiFunction(BiFunction<A, B, CompletableFuture<R>> func) {
        return (a, b) -> {
            final CompletableFuture<Try<R>> promise = new CompletableFuture<>();

            CompletableFuture<R> future = func.apply(a, b);

            future.thenAccept(r -> promise.complete(Try.success(r)));
            future.exceptionally(ex -> {
                promise.complete(Try.failure((Exception) ex));
                return null; // Java???
            });

            return promise;
        };
    }

    // Log function - logs the message and pipes the argument with no changes
    public static <A> Function<A, Try<A>> pipelineLog(String message) {
        return a -> {
            System.out.println(message + a);
            return Try.success(a);
        };
    }

    public static <A, B, C> Function<A, Try<C>> tryPipe(Function<A, Try<B>> first, Function<B, Try<C>> second) {
        return a -> {
            return first.apply(a).flatMap(b -> second.apply(b));
        };
    }

    public static <A, B> Function<A, Try<B>> tryPipeRecover(Function<A, Try<B>> first, Function<Throwable, B> recover) {
        return a -> {
            Try<B> r = first.apply(a);

            if (r.isSuccess()) {
                // If success, return the try value itself
                return r;
            } else {
                // If failure, return the recover value
                return Try.success(recover.apply(r.getException()));
            }
        };
    }

    // Recover function takes the exception and the original value
    public static <A, B> Function<A, Try<B>> tryPipeRecoverEx(Function<A, Try<B>> first, Function<Tuple2<Throwable, A>, B> recover) {
        return a -> {
            Try<B> r = first.apply(a);

            if (r.isSuccess()) {
                // If success, return the try value itself
                return r;
            } else {
                // If failure, return the recover value
                Tuple2<Throwable, A> tpl = tuple2(r.getException(), a);
                return Try.success(recover.apply(tpl));
            }
        };
    }

    public static <A, B> Function<A, Try<B>> tryPipeRecoverWith(Function<A, Try<B>> first, Function<Throwable, Try<B>> recover) {
        return a -> {
            Try<B> r = first.apply(a);

            if (r.isSuccess()) {
                // If success, return the try value itself
                return r;
            } else {
                // If failure, return the recover value
                return recover.apply(r.getException());
            }
        };
    }

    // RecoverWith function takes the exception and the original value
    public static <A, B> Function<A, Try<B>> tryPipeRecoverWithEx(Function<A, Try<B>> first, Function<Tuple2<Throwable, A>, Try<B>> recover) {
        return a -> {
            Try<B> r = first.apply(a);

            if (r.isSuccess()) {
                // If success, return the try value itself
                return r;
            } else {
                // If failure, return the recover value
                return recover.apply(tuple2(r.getException(), a));
            }
        };
    }

    public static <A, B, C> Function<A, CompletableFuture<Try<C>>> tryPipeAsync(Function<A, Try<B>> first, Function<B, CompletableFuture<Try<C>>> second) {
        return a -> {
            Try<B> b = first.apply(a);
            if (b.isSuccess()) {
                return second.apply(b.get());
            } else {
                CompletableFuture<Try<C>> failureResponse = new CompletableFuture<Try<C>>();
                failureResponse.complete(Try.failure(b));
                return failureResponse;
            }
        };
    }

    public static <A, B, C> Function<A, CompletableFuture<Try<C>>> tryAsyncPipeAsync(Function<A, CompletableFuture<Try<B>>> first, Function<B, CompletableFuture<Try<C>>> second) {
        return a -> {
            final CompletableFuture<Try<C>> promise = new CompletableFuture<>();

            CompletableFuture<Try<B>> future = first.apply(a);

            future.thenAccept(b -> {
                if (b.isSuccess()) {
                    CompletableFuture<Try<C>> secondFuture = second.apply(b.get());
                    secondFuture.thenAccept(c -> promise.complete(c));
                } else {
                    promise.complete(Try.failure(b));
                }
            });

            return promise;
        };
    }

    public static <A, B> Function<A, CompletableFuture<Try<B>>> tryAsyncPipeAsyncRecover(Function<A, CompletableFuture<Try<B>>> first, Function<Throwable, B> recover) {
        return a -> {
            final CompletableFuture<Try<B>> promise = new CompletableFuture<>();

            CompletableFuture<Try<B>> future = first.apply(a);

            future.thenAccept(b -> {
                if (b.isSuccess()) {
                    // Complete with the successful value
                    promise.complete(b);
                } else {
                    // On failure complete with the recover value
                    promise.complete(Try.success(recover.apply(b.getException())));
                }
            });

            return promise;
        };
    }

    public static <A, B> Function<A, CompletableFuture<Try<B>>> tryAsyncPipeAsyncRecoverEx(Function<A, CompletableFuture<Try<B>>> first, Function<Tuple2<Throwable, A>, B> recover) {
        return a -> {
            final CompletableFuture<Try<B>> promise = new CompletableFuture<>();

            CompletableFuture<Try<B>> future = first.apply(a);

            future.thenAccept(b -> {
                if (b.isSuccess()) {
                    // Complete with the successful value
                    promise.complete(b);
                } else {
                    // On failure complete with the recover value
                    Tuple2<Throwable, A> tpl = tuple2(b.getException(), a);
                    promise.complete(Try.success(recover.apply(tpl)));
                }
            });

            return promise;
        };
    }

    public static <A, B> Function<A, CompletableFuture<Try<B>>> tryAsyncPipeAsyncRecoverWith(Function<A, CompletableFuture<Try<B>>> first, Function<Throwable, Try<B>> recover) {
        return a -> {
            final CompletableFuture<Try<B>> promise = new CompletableFuture<>();

            CompletableFuture<Try<B>> future = first.apply(a);

            future.thenAccept(b -> {
                if (b.isSuccess()) {
                    // Complete with the successful value
                    promise.complete(b);
                } else {
                    // On failure complete with the recover value
                    promise.complete(recover.apply(b.getException()));
                }
            });

            return promise;
        };
    }

    public static <A, B> Function<A, CompletableFuture<Try<B>>> tryAsyncPipeAsyncRecoverWithEx(Function<A, CompletableFuture<Try<B>>> first, Function<Tuple2<Throwable, A>, Try<B>> recover) {
        return a -> {
            final CompletableFuture<Try<B>> promise = new CompletableFuture<>();

            CompletableFuture<Try<B>> future = first.apply(a);

            future.thenAccept(b -> {
                if (b.isSuccess()) {
                    // Complete with the successful value
                    promise.complete(b);
                } else {
                    // On failure complete with the recover value
                    promise.complete(recover.apply(tuple2(b.getException(), a)));
                }
            });

            return promise;
        };
    }

    public static <A, B> Function<A, CompletableFuture<Try<B>>> tryAsyncPipeAsyncFallbackTo(Function<A, CompletableFuture<Try<B>>> first, Function<Throwable, CompletableFuture<Try<B>>> fallbackTo) {
        return a -> {
            final CompletableFuture<Try<B>> promise = new CompletableFuture<>();

            CompletableFuture<Try<B>> future = first.apply(a);

            future.thenAccept(b -> {
                if (b.isSuccess()) {
                    // Complete with the successful value
                    promise.complete(b);
                } else {
                    // On failure complete with the recover value
                    CompletableFuture<Try<B>> fallback = fallbackTo.apply(b.getException());
                    fallback.thenAccept(c -> {
                        promise.complete(c);
                    });
                }
            });

            return promise;
        };
    }

    public static <A, B> Function<A, CompletableFuture<Try<B>>> tryAsyncPipeAsyncFallbackToEx(Function<A, CompletableFuture<Try<B>>> first, Function<Tuple2<Throwable, A>, CompletableFuture<Try<B>>> fallbackTo) {
        return a -> {
            final CompletableFuture<Try<B>> promise = new CompletableFuture<>();

            CompletableFuture<Try<B>> future = first.apply(a);

            future.thenAccept(b -> {
                if (b.isSuccess()) {
                    // Complete with the successful value
                    promise.complete(b);
                } else {
                    // On failure complete with the recover value
                    CompletableFuture<Try<B>> fallback = fallbackTo.apply(tuple2(b.getException(), a));
                    fallback.thenAccept(c -> {
                        promise.complete(c);
                    });
                }
            });

            return promise;
        };
    }

    public static <A, B, C> Function<A, CompletableFuture<Try<C>>> tryAsyncPipe(Function<A, CompletableFuture<Try<B>>> first, Function<B, Try<C>> second) {
        return a -> {
            final CompletableFuture<Try<C>> promise = new CompletableFuture<>();

            CompletableFuture<Try<B>> future = first.apply(a);

            future.thenAccept(b -> {
                if (b.isSuccess()) {
                    promise.complete(second.apply(b.get()));
                } else {
                    promise.complete(Try.failure(b));
                }
            });

            return promise;
        };
    }

    // Executing in parallel
    public static <A1, A2, R1, R2> Function<Tuple2<A1, A2>, CompletableFuture<Tuple2<Try<R1>, Try<R2>>>> par(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                             Function<A2, CompletableFuture<Try<R2>>> func2) {
        return a -> {
            CompletableFuture<Tuple2<Try<R1>, Try<R2>>> promise = new CompletableFuture<>();

            CompletableFuture<Try<R1>> func1Future = func1.apply(a._1());
            CompletableFuture<Try<R2>> func2Future = func2.apply(a._2());

            CompletableFuture.allOf(
                    func1Future,
                    func2Future
            ).thenAccept(b -> {
                try {
                    promise.complete(tuple2(func1Future.get(), func2Future.get()));
                } catch (Exception ex) {
                    promise.complete(tuple2(Try.failure(ex), Try.failure(ex)));
                }
            });

            return promise;
        };
    }

    public static <A1, A2, R1, R2> Function<Tuple2<A1, A2>, CompletableFuture<Try<Tuple2<R1, R2>>>> tryPar(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                           Function<A2, CompletableFuture<Try<R2>>> func2) {
        return a -> {
            CompletableFuture<Try<Tuple2<R1, R2>>> promise = new CompletableFuture<>();

            par(func1, func2)
                    .apply(a)
                    .thenAccept(b -> {
                        promise.complete(liftTuple(b));
                    });

            return promise;
        };
    }

    public static <A1, A2, A3, R1, R2, R3> Function<Tuple3<A1, A2, A3>, CompletableFuture<Tuple3<Try<R1>, Try<R2>, Try<R3>>>> par(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                  Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                  Function<A3, CompletableFuture<Try<R3>>> func3) {
        return a -> {
            CompletableFuture<Tuple3<Try<R1>, Try<R2>, Try<R3>>> promise = new CompletableFuture<>();

            CompletableFuture<Try<R1>> func1Future = func1.apply(a._1());
            CompletableFuture<Try<R2>> func2Future = func2.apply(a._2());
            CompletableFuture<Try<R3>> func3Future = func3.apply(a._3());

            CompletableFuture.allOf(
                    func1Future,
                    func2Future,
                    func3Future
            ).thenAccept(b -> {
                try {
                    promise.complete(tuple3(func1Future.get(), func2Future.get(), func3Future.get()));
                } catch (Exception ex) {
                    promise.complete(tuple3(Try.failure(ex), Try.failure(ex), Try.failure(ex)));
                }
            });

            return promise;
        };
    }

    public static <A1, A2, A3, R1, R2, R3> Function<Tuple3<A1, A2, A3>, CompletableFuture<Try<Tuple3<R1, R2, R3>>>> tryPar(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                           Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                           Function<A3, CompletableFuture<Try<R3>>> func3) {
        return a -> {
            CompletableFuture<Try<Tuple3<R1, R2, R3>>> promise = new CompletableFuture<>();

            par(func1, func2, func3)
                    .apply(a)
                    .thenAccept(b -> {
                        promise.complete(liftTuple(b));
                    });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, R1, R2, R3, R4> Function<Tuple4<A1, A2, A3, A4>, CompletableFuture<Tuple4<Try<R1>, Try<R2>, Try<R3>, Try<R4>>>> par(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                       Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                       Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                       Function<A4, CompletableFuture<Try<R4>>> func4) {
        return a -> {
            CompletableFuture<Tuple4<Try<R1>, Try<R2>, Try<R3>, Try<R4>>> promise = new CompletableFuture<>();

            CompletableFuture<Try<R1>> func1Future = func1.apply(a._1());
            CompletableFuture<Try<R2>> func2Future = func2.apply(a._2());
            CompletableFuture<Try<R3>> func3Future = func3.apply(a._3());
            CompletableFuture<Try<R4>> func4Future = func4.apply(a._4());

            CompletableFuture.allOf(
                    func1Future,
                    func2Future,
                    func3Future,
                    func4Future
            ).thenAccept(b -> {
                try {
                    promise.complete(tuple4(func1Future.get(), func2Future.get(), func3Future.get(), func4Future.get()));
                } catch (Exception ex) {
                    promise.complete(tuple4(Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex)));
                }
            });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, R1, R2, R3, R4> Function<Tuple4<A1, A2, A3, A4>, CompletableFuture<Try<Tuple4<R1, R2, R3, R4>>>> tryPar(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                           Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                           Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                           Function<A4, CompletableFuture<Try<R4>>> func4) {
        return a -> {
            CompletableFuture<Try<Tuple4<R1, R2, R3, R4>>> promise = new CompletableFuture<>();

            par(func1, func2, func3, func4)
                    .apply(a)
                    .thenAccept(b -> {
                        promise.complete(liftTuple(b));
                    });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, A5, R1, R2, R3, R4, R5> Function<Tuple5<A1, A2, A3, A4, A5>, CompletableFuture<Tuple5<Try<R1>, Try<R2>, Try<R3>, Try<R4>, Try<R5>>>> par(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                                            Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                                            Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                                            Function<A4, CompletableFuture<Try<R4>>> func4,
                                                                                                                                                                            Function<A5, CompletableFuture<Try<R5>>> func5) {
        return a -> {
            CompletableFuture<Tuple5<Try<R1>, Try<R2>, Try<R3>, Try<R4>, Try<R5>>> promise = new CompletableFuture<>();

            CompletableFuture<Try<R1>> func1Future = func1.apply(a._1());
            CompletableFuture<Try<R2>> func2Future = func2.apply(a._2());
            CompletableFuture<Try<R3>> func3Future = func3.apply(a._3());
            CompletableFuture<Try<R4>> func4Future = func4.apply(a._4());
            CompletableFuture<Try<R5>> func5Future = func5.apply(a._5());

            CompletableFuture.allOf(
                    func1Future,
                    func2Future,
                    func3Future,
                    func4Future,
                    func5Future
            ).thenAccept(b -> {
                try {
                    promise.complete(tuple5(func1Future.get(), func2Future.get(), func3Future.get(), func4Future.get(), func5Future.get()));
                } catch (Exception ex) {
                    promise.complete(tuple5(Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex)));
                }
            });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, A5, R1, R2, R3, R4, R5> Function<Tuple5<A1, A2, A3, A4, A5>, CompletableFuture<Try<Tuple5<R1, R2, R3, R4, R5>>>> tryPar(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                           Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                           Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                           Function<A4, CompletableFuture<Try<R4>>> func4,
                                                                                                                                                           Function<A5, CompletableFuture<Try<R5>>> func5) {
        return a -> {
            CompletableFuture<Try<Tuple5<R1, R2, R3, R4, R5>>> promise = new CompletableFuture<>();

            par(func1, func2, func3, func4, func5)
                    .apply(a)
                    .thenAccept(b -> {
                        promise.complete(liftTuple(b));
                    });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, A5, A6, R1, R2, R3, R4, R5, R6> Function<Tuple6<A1, A2, A3, A4, A5, A6>, CompletableFuture<Tuple6<Try<R1>, Try<R2>, Try<R3>, Try<R4>, Try<R5>, Try<R6>>>> par(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                                                                 Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                                                                 Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                                                                 Function<A4, CompletableFuture<Try<R4>>> func4,
                                                                                                                                                                                                 Function<A5, CompletableFuture<Try<R5>>> func5,
                                                                                                                                                                                                 Function<A6, CompletableFuture<Try<R6>>> func6) {
        return a -> {
            CompletableFuture<Tuple6<Try<R1>, Try<R2>, Try<R3>, Try<R4>, Try<R5>, Try<R6>>> promise = new CompletableFuture<>();

            CompletableFuture<Try<R1>> func1Future = func1.apply(a._1());
            CompletableFuture<Try<R2>> func2Future = func2.apply(a._2());
            CompletableFuture<Try<R3>> func3Future = func3.apply(a._3());
            CompletableFuture<Try<R4>> func4Future = func4.apply(a._4());
            CompletableFuture<Try<R5>> func5Future = func5.apply(a._5());
            CompletableFuture<Try<R6>> func6Future = func6.apply(a._6());

            CompletableFuture.allOf(
                    func1Future,
                    func2Future,
                    func3Future,
                    func4Future,
                    func5Future,
                    func6Future
            ).thenAccept(b -> {
                try {
                    promise.complete(tuple6(func1Future.get(), func2Future.get(), func3Future.get(), func4Future.get(), func5Future.get(), func6Future.get()));
                } catch (Exception ex) {
                    promise.complete(tuple6(Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex)));
                }
            });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, A5, A6, R1, R2, R3, R4, R5, R6> Function<Tuple6<A1, A2, A3, A4, A5, A6>, CompletableFuture<Try<Tuple6<R1, R2, R3, R4, R5, R6>>>> tryPar(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                                           Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                                           Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                                           Function<A4, CompletableFuture<Try<R4>>> func4,
                                                                                                                                                                           Function<A5, CompletableFuture<Try<R5>>> func5,
                                                                                                                                                                           Function<A6, CompletableFuture<Try<R6>>> func6) {
        return a -> {
            CompletableFuture<Try<Tuple6<R1, R2, R3, R4, R5, R6>>> promise = new CompletableFuture<>();

            par(func1, func2, func3, func4, func5, func6)
                    .apply(a)
                    .thenAccept(b -> {
                        promise.complete(liftTuple(b));
                    });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, A5, A6, A7, R1, R2, R3, R4, R5, R6, R7> Function<Tuple7<A1, A2, A3, A4, A5, A6, A7>, CompletableFuture<Tuple7<Try<R1>, Try<R2>, Try<R3>, Try<R4>, Try<R5>, Try<R6>, Try<R7>>>> par(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                                                                                      Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                                                                                      Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                                                                                      Function<A4, CompletableFuture<Try<R4>>> func4,
                                                                                                                                                                                                                      Function<A5, CompletableFuture<Try<R5>>> func5,
                                                                                                                                                                                                                      Function<A6, CompletableFuture<Try<R6>>> func6,
                                                                                                                                                                                                                      Function<A7, CompletableFuture<Try<R7>>> func7) {
        return a -> {
            CompletableFuture<Tuple7<Try<R1>, Try<R2>, Try<R3>, Try<R4>, Try<R5>, Try<R6>, Try<R7>>> promise = new CompletableFuture<>();

            CompletableFuture<Try<R1>> func1Future = func1.apply(a._1());
            CompletableFuture<Try<R2>> func2Future = func2.apply(a._2());
            CompletableFuture<Try<R3>> func3Future = func3.apply(a._3());
            CompletableFuture<Try<R4>> func4Future = func4.apply(a._4());
            CompletableFuture<Try<R5>> func5Future = func5.apply(a._5());
            CompletableFuture<Try<R6>> func6Future = func6.apply(a._6());
            CompletableFuture<Try<R7>> func7Future = func7.apply(a._7());

            CompletableFuture.allOf(
                    func1Future,
                    func2Future,
                    func3Future,
                    func4Future,
                    func5Future,
                    func6Future,
                    func7Future
            ).thenAccept(b -> {
                try {
                    promise.complete(tuple7(func1Future.get(), func2Future.get(), func3Future.get(), func4Future.get(), func5Future.get(), func6Future.get(), func7Future.get()));
                } catch (Exception ex) {
                    promise.complete(tuple7(Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex)));
                }
            });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, A5, A6, A7, R1, R2, R3, R4, R5, R6, R7> Function<Tuple7<A1, A2, A3, A4, A5, A6, A7>, CompletableFuture<Try<Tuple7<R1, R2, R3, R4, R5, R6, R7>>>> tryPar(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                                                           Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                                                           Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                                                           Function<A4, CompletableFuture<Try<R4>>> func4,
                                                                                                                                                                                           Function<A5, CompletableFuture<Try<R5>>> func5,
                                                                                                                                                                                           Function<A6, CompletableFuture<Try<R6>>> func6,
                                                                                                                                                                                           Function<A7, CompletableFuture<Try<R7>>> func7) {
        return a -> {
            CompletableFuture<Try<Tuple7<R1, R2, R3, R4, R5, R6, R7>>> promise = new CompletableFuture<>();

            par(func1, func2, func3, func4, func5, func6, func7)
                    .apply(a)
                    .thenAccept(b -> {
                        promise.complete(liftTuple(b));
                    });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, A5, A6, A7, A8, R1, R2, R3, R4, R5, R6, R7, R8> Function<Tuple8<A1, A2, A3, A4, A5, A6, A7, A8>, CompletableFuture<Tuple8<Try<R1>, Try<R2>, Try<R3>, Try<R4>, Try<R5>, Try<R6>, Try<R7>, Try<R8>>>> par(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                                                                                                           Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                                                                                                           Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                                                                                                           Function<A4, CompletableFuture<Try<R4>>> func4,
                                                                                                                                                                                                                                           Function<A5, CompletableFuture<Try<R5>>> func5,
                                                                                                                                                                                                                                           Function<A6, CompletableFuture<Try<R6>>> func6,
                                                                                                                                                                                                                                           Function<A7, CompletableFuture<Try<R7>>> func7,
                                                                                                                                                                                                                                           Function<A8, CompletableFuture<Try<R8>>> func8) {
        return a -> {
            CompletableFuture<Tuple8<Try<R1>, Try<R2>, Try<R3>, Try<R4>, Try<R5>, Try<R6>, Try<R7>, Try<R8>>> promise = new CompletableFuture<>();

            CompletableFuture<Try<R1>> func1Future = func1.apply(a._1());
            CompletableFuture<Try<R2>> func2Future = func2.apply(a._2());
            CompletableFuture<Try<R3>> func3Future = func3.apply(a._3());
            CompletableFuture<Try<R4>> func4Future = func4.apply(a._4());
            CompletableFuture<Try<R5>> func5Future = func5.apply(a._5());
            CompletableFuture<Try<R6>> func6Future = func6.apply(a._6());
            CompletableFuture<Try<R7>> func7Future = func7.apply(a._7());
            CompletableFuture<Try<R8>> func8Future = func8.apply(a._8());

            CompletableFuture.allOf(
                    func1Future,
                    func2Future,
                    func3Future,
                    func4Future,
                    func5Future,
                    func6Future,
                    func7Future,
                    func8Future
            ).thenAccept(b -> {
                try {
                    promise.complete(tuple8(func1Future.get(), func2Future.get(), func3Future.get(), func4Future.get(), func5Future.get(), func6Future.get(), func7Future.get(), func8Future.get()));
                } catch (Exception ex) {
                    promise.complete(tuple8(Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex)));
                }
            });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, A5, A6, A7, A8, R1, R2, R3, R4, R5, R6, R7, R8> Function<Tuple8<A1, A2, A3, A4, A5, A6, A7, A8>, CompletableFuture<Try<Tuple8<R1, R2, R3, R4, R5, R6, R7, R8>>>> tryPar(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                                                                           Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                                                                           Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                                                                           Function<A4, CompletableFuture<Try<R4>>> func4,
                                                                                                                                                                                                           Function<A5, CompletableFuture<Try<R5>>> func5,
                                                                                                                                                                                                           Function<A6, CompletableFuture<Try<R6>>> func6,
                                                                                                                                                                                                           Function<A7, CompletableFuture<Try<R7>>> func7,
                                                                                                                                                                                                           Function<A8, CompletableFuture<Try<R8>>> func8) {
        return a -> {
            CompletableFuture<Try<Tuple8<R1, R2, R3, R4, R5, R6, R7, R8>>> promise = new CompletableFuture<>();

            par(func1, func2, func3, func4, func5, func6, func7, func8)
                    .apply(a)
                    .thenAccept(b -> {
                        promise.complete(liftTuple(b));
                    });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, A5, A6, A7, A8, A9, R1, R2, R3, R4, R5, R6, R7, R8, R9> Function<Tuple9<A1, A2, A3, A4, A5, A6, A7, A8, A9>, CompletableFuture<Tuple9<Try<R1>, Try<R2>, Try<R3>, Try<R4>, Try<R5>, Try<R6>, Try<R7>, Try<R8>, Try<R9>>>> par(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                                                                                                                                Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                                                                                                                                Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                                                                                                                                Function<A4, CompletableFuture<Try<R4>>> func4,
                                                                                                                                                                                                                                                                Function<A5, CompletableFuture<Try<R5>>> func5,
                                                                                                                                                                                                                                                                Function<A6, CompletableFuture<Try<R6>>> func6,
                                                                                                                                                                                                                                                                Function<A7, CompletableFuture<Try<R7>>> func7,
                                                                                                                                                                                                                                                                Function<A8, CompletableFuture<Try<R8>>> func8,
                                                                                                                                                                                                                                                                Function<A9, CompletableFuture<Try<R9>>> func9) {
        return a -> {
            CompletableFuture<Tuple9<Try<R1>, Try<R2>, Try<R3>, Try<R4>, Try<R5>, Try<R6>, Try<R7>, Try<R8>, Try<R9>>> promise = new CompletableFuture<>();

            CompletableFuture<Try<R1>> func1Future = func1.apply(a._1());
            CompletableFuture<Try<R2>> func2Future = func2.apply(a._2());
            CompletableFuture<Try<R3>> func3Future = func3.apply(a._3());
            CompletableFuture<Try<R4>> func4Future = func4.apply(a._4());
            CompletableFuture<Try<R5>> func5Future = func5.apply(a._5());
            CompletableFuture<Try<R6>> func6Future = func6.apply(a._6());
            CompletableFuture<Try<R7>> func7Future = func7.apply(a._7());
            CompletableFuture<Try<R8>> func8Future = func8.apply(a._8());
            CompletableFuture<Try<R9>> func9Future = func9.apply(a._9());

            CompletableFuture.allOf(
                    func1Future,
                    func2Future,
                    func3Future,
                    func4Future,
                    func5Future,
                    func6Future,
                    func7Future,
                    func8Future,
                    func9Future
            ).thenAccept(b -> {
                try {
                    promise.complete(tuple9(func1Future.get(), func2Future.get(), func3Future.get(), func4Future.get(), func5Future.get(), func6Future.get(), func7Future.get(), func8Future.get(), func9Future.get()));
                } catch (Exception ex) {
                    promise.complete(tuple9(Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex)));
                }
            });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, A5, A6, A7, A8, A9, R1, R2, R3, R4, R5, R6, R7, R8, R9> Function<Tuple9<A1, A2, A3, A4, A5, A6, A7, A8, A9>, CompletableFuture<Try<Tuple9<R1, R2, R3, R4, R5, R6, R7, R8, R9>>>> tryPar(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                                                                                           Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                                                                                           Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                                                                                           Function<A4, CompletableFuture<Try<R4>>> func4,
                                                                                                                                                                                                                           Function<A5, CompletableFuture<Try<R5>>> func5,
                                                                                                                                                                                                                           Function<A6, CompletableFuture<Try<R6>>> func6,
                                                                                                                                                                                                                           Function<A7, CompletableFuture<Try<R7>>> func7,
                                                                                                                                                                                                                           Function<A8, CompletableFuture<Try<R8>>> func8,
                                                                                                                                                                                                                           Function<A9, CompletableFuture<Try<R9>>> func9) {
        return a -> {
            CompletableFuture<Try<Tuple9<R1, R2, R3, R4, R5, R6, R7, R8, R9>>> promise = new CompletableFuture<>();

            par(func1, func2, func3, func4, func5, func6, func7, func8, func9)
                    .apply(a)
                    .thenAccept(b -> {
                        promise.complete(liftTuple(b));
                    });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10> Function<Tuple10<A1, A2, A3, A4, A5, A6, A7, A8, A9, A10>, CompletableFuture<Tuple10<Try<R1>, Try<R2>, Try<R3>, Try<R4>, Try<R5>, Try<R6>, Try<R7>, Try<R8>, Try<R9>, Try<R10>>>> par(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                                                                                                                                                           Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                                                                                                                                                           Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                                                                                                                                                           Function<A4, CompletableFuture<Try<R4>>> func4,
                                                                                                                                                                                                                                                                                           Function<A5, CompletableFuture<Try<R5>>> func5,
                                                                                                                                                                                                                                                                                           Function<A6, CompletableFuture<Try<R6>>> func6,
                                                                                                                                                                                                                                                                                           Function<A7, CompletableFuture<Try<R7>>> func7,
                                                                                                                                                                                                                                                                                           Function<A8, CompletableFuture<Try<R8>>> func8,
                                                                                                                                                                                                                                                                                           Function<A9, CompletableFuture<Try<R9>>> func9,
                                                                                                                                                                                                                                                                                           Function<A10, CompletableFuture<Try<R10>>> func10) {
        return a -> {
            CompletableFuture<Tuple10<Try<R1>, Try<R2>, Try<R3>, Try<R4>, Try<R5>, Try<R6>, Try<R7>, Try<R8>, Try<R9>, Try<R10>>> promise = new CompletableFuture<>();

            CompletableFuture<Try<R1>> func1Future = func1.apply(a._1());
            CompletableFuture<Try<R2>> func2Future = func2.apply(a._2());
            CompletableFuture<Try<R3>> func3Future = func3.apply(a._3());
            CompletableFuture<Try<R4>> func4Future = func4.apply(a._4());
            CompletableFuture<Try<R5>> func5Future = func5.apply(a._5());
            CompletableFuture<Try<R6>> func6Future = func6.apply(a._6());
            CompletableFuture<Try<R7>> func7Future = func7.apply(a._7());
            CompletableFuture<Try<R8>> func8Future = func8.apply(a._8());
            CompletableFuture<Try<R9>> func9Future = func9.apply(a._9());
            CompletableFuture<Try<R10>> func10Future = func10.apply(a._10());

            CompletableFuture.allOf(
                    func1Future,
                    func2Future,
                    func3Future,
                    func4Future,
                    func5Future,
                    func6Future,
                    func7Future,
                    func8Future,
                    func9Future,
                    func10Future
            ).thenAccept(b -> {
                try {
                    promise.complete(tuple10(func1Future.get(), func2Future.get(), func3Future.get(), func4Future.get(), func5Future.get(), func6Future.get(), func7Future.get(), func8Future.get(), func9Future.get(), func10Future.get()));
                } catch (Exception ex) {
                    promise.complete(tuple10(Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex), Try.failure(ex)));
                }
            });

            return promise;
        };
    }

    public static <A1, A2, A3, A4, A5, A6, A7, A8, A9, A10, R1, R2, R3, R4, R5, R6, R7, R8, R9, R10> Function<Tuple10<A1, A2, A3, A4, A5, A6, A7, A8, A9, A10>, CompletableFuture<Try<Tuple10<R1, R2, R3, R4, R5, R6, R7, R8, R9, R10>>>> tryPar(Function<A1, CompletableFuture<Try<R1>>> func1,
                                                                                                                                                                                                                                                 Function<A2, CompletableFuture<Try<R2>>> func2,
                                                                                                                                                                                                                                                 Function<A3, CompletableFuture<Try<R3>>> func3,
                                                                                                                                                                                                                                                 Function<A4, CompletableFuture<Try<R4>>> func4,
                                                                                                                                                                                                                                                 Function<A5, CompletableFuture<Try<R5>>> func5,
                                                                                                                                                                                                                                                 Function<A6, CompletableFuture<Try<R6>>> func6,
                                                                                                                                                                                                                                                 Function<A7, CompletableFuture<Try<R7>>> func7,
                                                                                                                                                                                                                                                 Function<A8, CompletableFuture<Try<R8>>> func8,
                                                                                                                                                                                                                                                 Function<A9, CompletableFuture<Try<R9>>> func9,
                                                                                                                                                                                                                                                 Function<A10, CompletableFuture<Try<R10>>> func10) {
        return a -> {
            CompletableFuture<Try<Tuple10<R1, R2, R3, R4, R5, R6, R7, R8, R9, R10>>> promise = new CompletableFuture<>();

            par(func1, func2, func3, func4, func5, func6, func7, func8, func9, func10)
                    .apply(a)
                    .thenAccept(b -> {
                        promise.complete(liftTuple(b));
                    });

            return promise;
        };
    }

    /**
     * Construct a fully non-blocking, thread-free function with desired level of parallelism
     *
     * @param parLevel
     * @param processFunc
     * @param <A>
     * @param <B>
     * @return
     */
    public static <A, B> Function<Collection<A>, CompletableFuture<Collection<Try<B>>>> nonBlockPar(int parLevel, Function<A, CompletableFuture<Try<B>>> processFunc) {
        return a -> {
            CompletableFuture<Collection<Try<B>>> promise = new CompletableFuture<>();

            // Do a minimal processing on empty collection
            if(a.isEmpty()){
                promise.complete(new ConcurrentLinkedQueue<>());
                return promise;
            }

            // Initialize all we need for this function
            ConcurrentLinkedQueue<A> inputQueue = new ConcurrentLinkedQueue<A>();
            ConcurrentLinkedQueue<Try<B>> outputQueue = new ConcurrentLinkedQueue<>();

            int inputSize = a.size();
            inputQueue.addAll(a);

            final List<Consumer<Try<B>>> processItemNextList = new ArrayList<>();

            Consumer<Try<B>> processItem = b -> {
                outputQueue.add(b);

                A item = inputQueue.poll();

                if(item != null){
                    CompletableFuture<Try<B>> future = processFunc.apply(item);
                    future.thenAccept(processItemNextList.get(0));
                } else {
                    // No more items to process
                    if(inputSize == outputQueue.size()){
                        // We are done with the processing, complete the promise
                        promise.complete(outputQueue);
                    }
                }
            };

            processItemNextList.add(processItem);
            // Done with the initialization

            // Start the initial set of calls
            IntStream.range(1, Math.max(parLevel, 1))
                    .forEach(i -> {
                        A item = inputQueue.poll();

                        if(item != null){
                            CompletableFuture<Try<B>> future = processFunc.apply(item);
                            future.thenAccept(processItem);
                        }
                    });

            return promise;
        };
    }

    /**
     * Construct a fully non-blocking, thread-free function with desired level of parallelism,
     *     return the result in a Try monad
     *
     * @param parLevel
     * @param processFunc
     * @param <A>
     * @param <B>
     * @return
     */
    public static <A, B> Function<Collection<A>, CompletableFuture<Try<Collection<Try<B>>>>> tryNonBlockPar(int parLevel, Function<A, CompletableFuture<Try<B>>> processFunc) {
        return liftFuture(Pipeline.nonBlockPar(parLevel, processFunc));
    }

    public static class ExceptionWrapper extends RuntimeException {
        private List<Throwable> exceptions;

        public ExceptionWrapper(List<Throwable> exceptions) {
            this.exceptions = exceptions;
        }

        public List<Throwable> getExceptions() {
            return exceptions;
        }

        @Override
        public String getMessage(){
            return exceptions.stream()
                    .map(Throwable::getMessage)
                    .collect(Collectors.joining(", "));
        }

        @Override
        public String toString() {
            return exceptions.stream()
                    .map(Throwable::toString)
                    .collect(Collectors.joining(", "));

        }
    }

    public static <A, B> Try<Tuple2<A, B>> liftTuple(Tuple2<Try<A>, Try<B>> tuple) {
        List<Throwable> exceptions = new ArrayList<>();

        if (tuple._1().isFailure()) {
            exceptions.add(tuple._1().getException());
        }

        if (tuple._2().isFailure()) {
            exceptions.add(tuple._2().getException());
        }

        if (exceptions.isEmpty()) {
            return Try.success(tuple2(tuple._1().get(), tuple._2().get()));
        } else {
            return Try.failure(new ExceptionWrapper(exceptions));
        }
    }

    public static <A, B, C> Try<Tuple3<A, B, C>> liftTuple(Tuple3<Try<A>, Try<B>, Try<C>> tuple) {
        List<Throwable> exceptions = new ArrayList<>();

        if (tuple._1().isFailure()) {
            exceptions.add(tuple._1().getException());
        }

        if (tuple._2().isFailure()) {
            exceptions.add(tuple._2().getException());
        }

        if (tuple._3().isFailure()) {
            exceptions.add(tuple._3().getException());
        }

        if (exceptions.isEmpty()) {
            return Try.success(tuple3(tuple._1().get(), tuple._2().get(), tuple._3().get()));
        } else {
            return Try.failure(new ExceptionWrapper(exceptions));
        }
    }

    public static <A, B, C, D> Try<Tuple4<A, B, C, D>> liftTuple(Tuple4<Try<A>, Try<B>, Try<C>, Try<D>> tuple) {
        List<Throwable> exceptions = new ArrayList<>();

        if (tuple._1().isFailure()) {
            exceptions.add(tuple._1().getException());
        }

        if (tuple._2().isFailure()) {
            exceptions.add(tuple._2().getException());
        }

        if (tuple._3().isFailure()) {
            exceptions.add(tuple._3().getException());
        }

        if (tuple._4().isFailure()) {
            exceptions.add(tuple._4().getException());
        }

        if (exceptions.isEmpty()) {
            return Try.success(tuple4(tuple._1().get(), tuple._2().get(), tuple._3().get(), tuple._4().get()));
        } else {
            return Try.failure(new ExceptionWrapper(exceptions));
        }
    }

    public static <A, B, C, D, E> Try<Tuple5<A, B, C, D, E>> liftTuple(Tuple5<Try<A>, Try<B>, Try<C>, Try<D>, Try<E>> tuple) {
        List<Throwable> exceptions = new ArrayList<>();

        if (tuple._1().isFailure()) {
            exceptions.add(tuple._1().getException());
        }

        if (tuple._2().isFailure()) {
            exceptions.add(tuple._2().getException());
        }

        if (tuple._3().isFailure()) {
            exceptions.add(tuple._3().getException());
        }

        if (tuple._4().isFailure()) {
            exceptions.add(tuple._4().getException());
        }

        if (tuple._5().isFailure()) {
            exceptions.add(tuple._5().getException());
        }

        if (exceptions.isEmpty()) {
            return Try.success(tuple5(tuple._1().get(), tuple._2().get(), tuple._3().get(), tuple._4().get(), tuple._5().get()));
        } else {
            return Try.failure(new ExceptionWrapper(exceptions));
        }
    }

    public static <A, B, C, D, E, F> Try<Tuple6<A, B, C, D, E, F>> liftTuple(Tuple6<Try<A>, Try<B>, Try<C>, Try<D>, Try<E>, Try<F>> tuple) {
        List<Throwable> exceptions = new ArrayList<>();

        if (tuple._1().isFailure()) {
            exceptions.add(tuple._1().getException());
        }

        if (tuple._2().isFailure()) {
            exceptions.add(tuple._2().getException());
        }

        if (tuple._3().isFailure()) {
            exceptions.add(tuple._3().getException());
        }

        if (tuple._4().isFailure()) {
            exceptions.add(tuple._4().getException());
        }

        if (tuple._5().isFailure()) {
            exceptions.add(tuple._5().getException());
        }

        if (tuple._6().isFailure()) {
            exceptions.add(tuple._6().getException());
        }

        if (exceptions.isEmpty()) {
            return Try.success(tuple6(tuple._1().get(), tuple._2().get(), tuple._3().get(), tuple._4().get(), tuple._5().get(), tuple._6().get()));
        } else {
            return Try.failure(new ExceptionWrapper(exceptions));
        }
    }

    public static <A, B, C, D, E, F, G> Try<Tuple7<A, B, C, D, E, F, G>> liftTuple(Tuple7<Try<A>, Try<B>, Try<C>, Try<D>, Try<E>, Try<F>, Try<G>> tuple) {
        List<Throwable> exceptions = new ArrayList<>();

        if (tuple._1().isFailure()) {
            exceptions.add(tuple._1().getException());
        }

        if (tuple._2().isFailure()) {
            exceptions.add(tuple._2().getException());
        }

        if (tuple._3().isFailure()) {
            exceptions.add(tuple._3().getException());
        }

        if (tuple._4().isFailure()) {
            exceptions.add(tuple._4().getException());
        }

        if (tuple._5().isFailure()) {
            exceptions.add(tuple._5().getException());
        }

        if (tuple._6().isFailure()) {
            exceptions.add(tuple._6().getException());
        }

        if (tuple._7().isFailure()) {
            exceptions.add(tuple._6().getException());
        }

        if (exceptions.isEmpty()) {
            return Try.success(tuple7(tuple._1().get(), tuple._2().get(), tuple._3().get(), tuple._4().get(), tuple._5().get(), tuple._6().get(), tuple._7().get()));
        } else {
            return Try.failure(new ExceptionWrapper(exceptions));
        }
    }

    public static <A, B, C, D, E, F, G, H> Try<Tuple8<A, B, C, D, E, F, G, H>> liftTuple(Tuple8<Try<A>, Try<B>, Try<C>, Try<D>, Try<E>, Try<F>, Try<G>, Try<H>> tuple) {
        List<Throwable> exceptions = new ArrayList<>();

        if (tuple._1().isFailure()) {
            exceptions.add(tuple._1().getException());
        }

        if (tuple._2().isFailure()) {
            exceptions.add(tuple._2().getException());
        }

        if (tuple._3().isFailure()) {
            exceptions.add(tuple._3().getException());
        }

        if (tuple._4().isFailure()) {
            exceptions.add(tuple._4().getException());
        }

        if (tuple._5().isFailure()) {
            exceptions.add(tuple._5().getException());
        }

        if (tuple._6().isFailure()) {
            exceptions.add(tuple._6().getException());
        }

        if (tuple._7().isFailure()) {
            exceptions.add(tuple._7().getException());
        }

        if (tuple._8().isFailure()) {
            exceptions.add(tuple._8().getException());
        }

        if (exceptions.isEmpty()) {
            return Try.success(tuple8(tuple._1().get(), tuple._2().get(), tuple._3().get(), tuple._4().get(), tuple._5().get(), tuple._6().get(), tuple._7().get(), tuple._8().get()));
        } else {
            return Try.failure(new ExceptionWrapper(exceptions));
        }
    }

    public static <A, B, C, D, E, F, G, H, I> Try<Tuple9<A, B, C, D, E, F, G, H, I>> liftTuple(Tuple9<Try<A>, Try<B>, Try<C>, Try<D>, Try<E>, Try<F>, Try<G>, Try<H>, Try<I>> tuple) {
        List<Throwable> exceptions = new ArrayList<>();

        if (tuple._1().isFailure()) {
            exceptions.add(tuple._1().getException());
        }

        if (tuple._2().isFailure()) {
            exceptions.add(tuple._2().getException());
        }

        if (tuple._3().isFailure()) {
            exceptions.add(tuple._3().getException());
        }

        if (tuple._4().isFailure()) {
            exceptions.add(tuple._4().getException());
        }

        if (tuple._5().isFailure()) {
            exceptions.add(tuple._5().getException());
        }

        if (tuple._6().isFailure()) {
            exceptions.add(tuple._6().getException());
        }

        if (tuple._7().isFailure()) {
            exceptions.add(tuple._7().getException());
        }

        if (tuple._8().isFailure()) {
            exceptions.add(tuple._8().getException());
        }

        if (tuple._9().isFailure()) {
            exceptions.add(tuple._9().getException());
        }

        if (exceptions.isEmpty()) {
            return Try.success(tuple9(tuple._1().get(), tuple._2().get(), tuple._3().get(), tuple._4().get(), tuple._5().get(), tuple._6().get(), tuple._7().get(), tuple._8().get(), tuple._9().get()));
        } else {
            return Try.failure(new ExceptionWrapper(exceptions));
        }
    }

    public static <A, B, C, D, E, F, G, H, I, J> Try<Tuple10<A, B, C, D, E, F, G, H, I, J>> liftTuple(Tuple10<Try<A>, Try<B>, Try<C>, Try<D>, Try<E>, Try<F>, Try<G>, Try<H>, Try<I>, Try<J>> tuple) {
        List<Throwable> exceptions = new ArrayList<>();

        if (tuple._1().isFailure()) {
            exceptions.add(tuple._1().getException());
        }

        if (tuple._2().isFailure()) {
            exceptions.add(tuple._2().getException());
        }

        if (tuple._3().isFailure()) {
            exceptions.add(tuple._3().getException());
        }

        if (tuple._4().isFailure()) {
            exceptions.add(tuple._4().getException());
        }

        if (tuple._5().isFailure()) {
            exceptions.add(tuple._5().getException());
        }

        if (tuple._6().isFailure()) {
            exceptions.add(tuple._6().getException());
        }

        if (tuple._7().isFailure()) {
            exceptions.add(tuple._7().getException());
        }

        if (tuple._8().isFailure()) {
            exceptions.add(tuple._8().getException());
        }

        if (tuple._9().isFailure()) {
            exceptions.add(tuple._9().getException());
        }

        if (tuple._10().isFailure()) {
            exceptions.add(tuple._10().getException());
        }

        if (exceptions.isEmpty()) {
            return Try.success(tuple10(tuple._1().get(), tuple._2().get(), tuple._3().get(), tuple._4().get(), tuple._5().get(), tuple._6().get(), tuple._7().get(), tuple._8().get(), tuple._9().get(), tuple._10().get()));
        } else {
            return Try.failure(new ExceptionWrapper(exceptions));
        }
    }
}
