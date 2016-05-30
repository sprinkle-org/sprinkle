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

import org.junit.Test;
import org.sprinkle.Pipeline;
import org.sprinkle.Try;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.sprinkle.Pipeline.liftTuple;
import static org.sprinkle.Pipeline.par;
import static org.sprinkle.Pipeline.tryPar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static org.sprinkle.Tuple.*;

/**
 * Created by mboussarov on 12/11/15.
 */
public class PipelineTest {
    Function<String, Try<String>> identity = a -> {
        return Try.success(a);
    };

    Function<String, Try<String>> failure = a -> {
        return Try.failure(new RuntimeException());
    };

    Function<String, CompletableFuture<Try<String>>> identityAsync = a -> {
        CompletableFuture<Try<String>> promise = new CompletableFuture<>();
        promise.complete(identity.apply(a));
        return promise;
    };

    Function<String, CompletableFuture<Try<String>>> failureAsync = a -> {
        CompletableFuture<Try<String>> promise = new CompletableFuture<>();
        promise.complete(failure.apply(a));
        return promise;
    };

    @Test
    public void testSyncPipeline() throws Exception {
        Pipeline<String, Try<String>> syncPipeline = Pipeline.createPipeline(identity);

        // Get
        Function<String, Try<String>> wrappedIdentity = syncPipeline.get();
        assertEquals(wrappedIdentity.apply("hello").get(), "hello");

        // pipeToSync
        wrappedIdentity = Pipeline.createPipeline(identity)
                .pipeToSync(identity)
                .get();

        assertEquals(wrappedIdentity.apply("hello").get(), "hello");

        // pipeToAsync
        Function<String, CompletableFuture<Try<String>>> identityAsyncWrapped =
                Pipeline.createPipeline(identity)
                    .pipeToAsync(identityAsync)
                    .get();

        Try<String> identityAsyncResult = identityAsyncWrapped.apply("hello").get();
        assertEquals(identityAsyncResult.get(), "hello");

        // Recover
        syncPipeline = Pipeline.createPipeline(failure)
                .recover(ex -> {
                    return "hello";
                });

        wrappedIdentity = syncPipeline.get();
        assertEquals(wrappedIdentity.apply("hello").get(), "hello");

        // RecoverEx
        syncPipeline = Pipeline.createPipeline(failure)
                .recoverEx(t2 -> {
                    return t2._2();
                });

        wrappedIdentity = syncPipeline.get();
        assertEquals(wrappedIdentity.apply("hello").get(), "hello");

        // RecoverWith
        syncPipeline = Pipeline.createPipeline(failure)
                .recoverWith(ex -> {
                    return Try.success("world");
                });

        wrappedIdentity = syncPipeline.get();
        assertEquals(wrappedIdentity.apply("world").get(), "world");

        // RecoverWithEx
        syncPipeline = Pipeline.createPipeline(failure)
                .recoverWithEx(t2 -> {
                    return Try.success(t2._2() + " world");
                });

        wrappedIdentity = syncPipeline.get();
        assertEquals(wrappedIdentity.apply("hello").get(), "hello world");
    }

    @Test
    public void testAsyncPipeline() throws Exception {
        Pipeline<String, CompletableFuture<Try<String>>> asyncPipeline =
                Pipeline.createAsyncPipeline(identityAsync);

        // Get
        Function<String, CompletableFuture<Try<String>>> wrappedIdentityAsync = asyncPipeline.get();
        assertEquals(wrappedIdentityAsync.apply("hello").get().get(), "hello");

        // pipeToSync
        wrappedIdentityAsync = Pipeline.createAsyncPipeline(identityAsync)
                .pipeToSync(identity)
                .get();

        assertEquals(wrappedIdentityAsync.apply("hello").get().get(), "hello");

        // pipeToAsync
        wrappedIdentityAsync = Pipeline.createAsyncPipeline(identityAsync)
                        .pipeToAsync(identityAsync)
                        .get();

        assertEquals(wrappedIdentityAsync.apply("hello").get().get(), "hello");

        // Recover
        wrappedIdentityAsync = Pipeline.createAsyncPipeline(failureAsync)
                .recover(ex -> {
                    return "hello";
                })
                .get();

        assertEquals(wrappedIdentityAsync.apply("hello").get().get(), "hello");

        // RecoverEx
        wrappedIdentityAsync = Pipeline.createAsyncPipeline(failureAsync)
                .recoverEx(t2 -> {
                    return t2._2();
                })
                .get();

        assertEquals(wrappedIdentityAsync.apply("hello").get().get(), "hello");

        // RecoverWith
        wrappedIdentityAsync = Pipeline.createAsyncPipeline(failureAsync)
                .recoverWith(ex -> {
                    return Try.success("world");
                })
                .get();

        assertEquals(wrappedIdentityAsync.apply("hello").get().get(), "world");

        // RecoverWithEx
        wrappedIdentityAsync = Pipeline.createAsyncPipeline(failureAsync)
                .recoverWithEx(t2 -> {
                    return Try.success(t2._2() + " world");
                })
                .get();

        assertEquals(wrappedIdentityAsync.apply("hello").get().get(), "hello world");

        // Fallback
        wrappedIdentityAsync = Pipeline.createAsyncPipeline(failureAsync)
                .fallbackTo(ex -> {
                    return identityAsync.apply("hello");
                })
                .get();

        assertEquals(wrappedIdentityAsync.apply("hello").get().get(), "hello");

        // FallbackTo
        wrappedIdentityAsync = Pipeline.createAsyncPipeline(failureAsync)
                .fallbackToEx(t2 -> {
                    return identityAsync.apply(t2._2() + " world");
                })
                .get();

        assertEquals(wrappedIdentityAsync.apply("hello").get().get(), "hello world");
    }

    @Test
    public void testLiftTuple() {
        Try<String> success = Try.success("success");
        Try<String> failure = Try.failure(new RuntimeException());

        // Lift tuple2
        Try<Tuple2<String, String>> liftedTuple2 = liftTuple(tuple2(success, success));
        assertTrue(liftedTuple2.isSuccess());

        liftedTuple2 = liftTuple(tuple2(failure, failure));
        assertTrue(liftedTuple2.isFailure());

        // Lift tuple3
        Try<Tuple3<String, String, String>> liftedTuple3 = liftTuple(tuple3(success, success, success));
        assertTrue(liftedTuple3.isSuccess());

        liftedTuple3 = liftTuple(tuple3(failure, failure, failure));
        assertTrue(liftedTuple3.isFailure());

        // Lift tuple4
        Try<Tuple4<String, String, String, String>> liftedTuple4 = liftTuple(tuple4(success, success, success, success));
        assertTrue(liftedTuple4.isSuccess());

        liftedTuple4 = liftTuple(tuple4(failure, failure, failure, failure));
        assertTrue(liftedTuple4.isFailure());

        // Lift tuple5
        Try<Tuple5<String, String, String, String, String>> liftedTuple5 =
                liftTuple(tuple5(success, success, success, success, success));
        assertTrue(liftedTuple5.isSuccess());

        liftedTuple5 = liftTuple(tuple5(failure, failure, failure, failure, failure));
        assertTrue(liftedTuple5.isFailure());

        // Lift tuple6
        Try<Tuple6<String, String, String, String, String, String>> liftedTuple6 =
                liftTuple(tuple6(success, success, success, success, success, success));
        assertTrue(liftedTuple6.isSuccess());

        liftedTuple6 = liftTuple(tuple6(failure, failure, failure, failure, failure, failure));
        assertTrue(liftedTuple6.isFailure());

        // Lift tuple7
        Try<Tuple7<String, String, String, String, String, String, String>> liftedTuple7 =
                liftTuple(tuple7(success, success, success, success, success, success, success));
        assertTrue(liftedTuple7.isSuccess());

        liftedTuple7 = liftTuple(tuple7(failure, failure, failure, failure, failure, failure, failure));
        assertTrue(liftedTuple7.isFailure());

        // Lift tuple8
        Try<Tuple8<String, String, String, String, String, String, String, String>> liftedTuple8 =
                liftTuple(tuple8(success, success, success, success, success, success, success, success));
        assertTrue(liftedTuple8.isSuccess());

        liftedTuple8 = liftTuple(tuple8(failure, failure, failure, failure, failure, failure, failure, failure));
        assertTrue(liftedTuple8.isFailure());

        // Lift tuple9
        Try<Tuple9<String, String, String, String, String, String, String, String, String>> liftedTuple9 =
                liftTuple(tuple9(success, success, success, success, success, success, success, success, success));
        assertTrue(liftedTuple9.isSuccess());

        liftedTuple9 = liftTuple(tuple9(failure, failure, failure, failure, failure, failure, failure, failure, failure));
        assertTrue(liftedTuple9.isFailure());

        // Lift tuple10
        Try<Tuple10<String, String, String, String, String, String, String, String, String, String>> liftedTuple10 =
                liftTuple(tuple10(success, success, success, success, success, success, success, success, success, success));
        assertTrue(liftedTuple10.isSuccess());

        liftedTuple10 = liftTuple(tuple10(failure, failure, failure, failure, failure, failure, failure, failure, failure, failure));
        assertTrue(liftedTuple10.isFailure());
    }

    @Test
    public void testPar() throws Exception {
        // Par 2
        Function<Tuple2<String, String>, CompletableFuture<Tuple2<Try<String>, Try<String>>>> par2 =
                par(identityAsync, identityAsync);

        CompletableFuture<Tuple2<Try<String>, Try<String>>> par2Result = par2.apply(tuple2("hello", "hello"));

        assertEquals(par2Result.get()._1().get(), "hello");
        assertEquals(par2Result.get()._2().get(), "hello");

        // Par 3
        Function<Tuple3<String, String, String>, CompletableFuture<Tuple3<Try<String>, Try<String>, Try<String>>>> par3 =
                par(identityAsync, identityAsync, identityAsync);

        CompletableFuture<Tuple3<Try<String>, Try<String>, Try<String>>> par3Result =
                par3.apply(tuple3("hello", "hello", "hello"));

        assertEquals(par3Result.get()._1().get(), "hello");
        assertEquals(par3Result.get()._2().get(), "hello");
        assertEquals(par3Result.get()._3().get(), "hello");

        // Par 4
        Function<Tuple4<String, String, String, String>,
                CompletableFuture<Tuple4<Try<String>, Try<String>, Try<String>, Try<String>>>> par4 =
                par(identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Tuple4<Try<String>, Try<String>, Try<String>, Try<String>>> par4Result =
                par4.apply(tuple4("hello", "hello", "hello", "hello"));

        assertEquals(par4Result.get()._1().get(), "hello");
        assertEquals(par4Result.get()._2().get(), "hello");
        assertEquals(par4Result.get()._3().get(), "hello");
        assertEquals(par4Result.get()._4().get(), "hello");

        // Par 5
        Function<Tuple5<String, String, String, String, String>,
                CompletableFuture<Tuple5<Try<String>, Try<String>, Try<String>, Try<String>, Try<String>>>> par5 =
                par(identityAsync, identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Tuple5<Try<String>, Try<String>, Try<String>, Try<String>, Try<String>>> par5Result =
                par5.apply(tuple5("hello", "hello", "hello", "hello", "hello"));

        assertEquals(par5Result.get()._1().get(), "hello");
        assertEquals(par5Result.get()._2().get(), "hello");
        assertEquals(par5Result.get()._3().get(), "hello");
        assertEquals(par5Result.get()._4().get(), "hello");
        assertEquals(par5Result.get()._5().get(), "hello");

        // Par 6
        Function<Tuple6<String, String, String, String, String, String>,
                CompletableFuture<Tuple6<Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>>>> par6 =
                par(identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Tuple6<Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>>> par6Result =
                par6.apply(tuple6("hello", "hello", "hello", "hello", "hello", "hello"));

        assertEquals(par6Result.get()._1().get(), "hello");
        assertEquals(par6Result.get()._2().get(), "hello");
        assertEquals(par6Result.get()._3().get(), "hello");
        assertEquals(par6Result.get()._4().get(), "hello");
        assertEquals(par6Result.get()._5().get(), "hello");
        assertEquals(par6Result.get()._6().get(), "hello");

        // Par 7
        Function<Tuple7<String, String, String, String, String, String, String>,
                CompletableFuture<Tuple7<Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>>>> par7 =
                par(identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Tuple7<Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>>> par7Result =
                par7.apply(tuple7("hello", "hello", "hello", "hello", "hello", "hello", "hello"));

        assertEquals(par7Result.get()._1().get(), "hello");
        assertEquals(par7Result.get()._2().get(), "hello");
        assertEquals(par7Result.get()._3().get(), "hello");
        assertEquals(par7Result.get()._4().get(), "hello");
        assertEquals(par7Result.get()._5().get(), "hello");
        assertEquals(par7Result.get()._6().get(), "hello");
        assertEquals(par7Result.get()._7().get(), "hello");

        // Par 8
        Function<Tuple8<String, String, String, String, String, String, String, String>,
                CompletableFuture<Tuple8<Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>>>> par8 =
                par(identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Tuple8<Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>>> par8Result =
                par8.apply(tuple8("hello", "hello", "hello", "hello", "hello", "hello", "hello", "hello"));

        assertEquals(par8Result.get()._1().get(), "hello");
        assertEquals(par8Result.get()._2().get(), "hello");
        assertEquals(par8Result.get()._3().get(), "hello");
        assertEquals(par8Result.get()._4().get(), "hello");
        assertEquals(par8Result.get()._5().get(), "hello");
        assertEquals(par8Result.get()._6().get(), "hello");
        assertEquals(par8Result.get()._7().get(), "hello");
        assertEquals(par8Result.get()._8().get(), "hello");

        // Par 9
        Function<Tuple9<String, String, String, String, String, String, String, String, String>,
                CompletableFuture<Tuple9<Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>>>> par9 =
                par(identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Tuple9<Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>>> par9Result =
                par9.apply(tuple9("hello", "hello", "hello", "hello", "hello", "hello", "hello", "hello", "hello"));

        assertEquals(par9Result.get()._1().get(), "hello");
        assertEquals(par9Result.get()._2().get(), "hello");
        assertEquals(par9Result.get()._3().get(), "hello");
        assertEquals(par9Result.get()._4().get(), "hello");
        assertEquals(par9Result.get()._5().get(), "hello");
        assertEquals(par9Result.get()._6().get(), "hello");
        assertEquals(par9Result.get()._7().get(), "hello");
        assertEquals(par9Result.get()._8().get(), "hello");
        assertEquals(par9Result.get()._9().get(), "hello");

        // Par 10
        Function<Tuple10<String, String, String, String, String, String, String, String, String, String>,
                CompletableFuture<Tuple10<Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>>>> par10 =
                par(identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Tuple10<Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>, Try<String>>> par10Result =
                par10.apply(tuple10("hello", "hello", "hello", "hello", "hello", "hello", "hello", "hello", "hello", "hello"));

        assertEquals(par10Result.get()._1().get(), "hello");
        assertEquals(par10Result.get()._2().get(), "hello");
        assertEquals(par10Result.get()._3().get(), "hello");
        assertEquals(par10Result.get()._4().get(), "hello");
        assertEquals(par10Result.get()._5().get(), "hello");
        assertEquals(par10Result.get()._6().get(), "hello");
        assertEquals(par10Result.get()._7().get(), "hello");
        assertEquals(par10Result.get()._8().get(), "hello");
        assertEquals(par10Result.get()._9().get(), "hello");
        assertEquals(par10Result.get()._10().get(), "hello");
    }

    @Test
    public void testTryPar() throws Exception {
        // tryPar 2
        Function<Tuple2<String, String>, CompletableFuture<Try<Tuple2<String, String>>>> tryPar2 =
                tryPar(identityAsync, identityAsync);

        CompletableFuture<Try<Tuple2<String, String>>> par2Result = tryPar2.apply(tuple2("hello", "hello"));

        assertEquals(par2Result.get().get()._1(), "hello");
        assertEquals(par2Result.get().get()._2(), "hello");

        // tryPar 3
        Function<Tuple3<String, String, String>, CompletableFuture<Try<Tuple3<String, String, String>>>> tryPar3 =
                tryPar(identityAsync, identityAsync, identityAsync);

        CompletableFuture<Try<Tuple3<String, String, String>>> par3Result =
                tryPar3.apply(tuple3("hello", "hello", "hello"));

        assertEquals(par3Result.get().get()._1(), "hello");
        assertEquals(par3Result.get().get()._2(), "hello");
        assertEquals(par3Result.get().get()._3(), "hello");

        // tryPar 4
        Function<Tuple4<String, String, String, String>,
                CompletableFuture<Try<Tuple4<String, String, String, String>>>> tryPar4 =
                tryPar(identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Try<Tuple4<String, String, String, String>>> par4Result =
                tryPar4.apply(tuple4("hello", "hello", "hello", "hello"));

        assertEquals(par4Result.get().get()._1(), "hello");
        assertEquals(par4Result.get().get()._2(), "hello");
        assertEquals(par4Result.get().get()._3(), "hello");
        assertEquals(par4Result.get().get()._4(), "hello");

        // tryPar 5
        Function<Tuple5<String, String, String, String, String>,
                CompletableFuture<Try<Tuple5<String, String, String, String, String>>>> tryPar5 =
                tryPar(identityAsync, identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Try<Tuple5<String, String, String, String, String>>> par5Result =
                tryPar5.apply(tuple5("hello", "hello", "hello", "hello", "hello"));

        assertEquals(par5Result.get().get()._1(), "hello");
        assertEquals(par5Result.get().get()._2(), "hello");
        assertEquals(par5Result.get().get()._3(), "hello");
        assertEquals(par5Result.get().get()._4(), "hello");
        assertEquals(par5Result.get().get()._5(), "hello");

        // tryPar 6
        Function<Tuple6<String, String, String, String, String, String>,
                CompletableFuture<Try<Tuple6<String, String, String, String, String, String>>>> tryPar6 =
                tryPar(identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Try<Tuple6<String, String, String, String, String, String>>> par6Result =
                tryPar6.apply(tuple6("hello", "hello", "hello", "hello", "hello", "hello"));

        assertEquals(par6Result.get().get()._1(), "hello");
        assertEquals(par6Result.get().get()._2(), "hello");
        assertEquals(par6Result.get().get()._3(), "hello");
        assertEquals(par6Result.get().get()._4(), "hello");
        assertEquals(par6Result.get().get()._5(), "hello");
        assertEquals(par6Result.get().get()._6(), "hello");

        // tryPar 7
        Function<Tuple7<String, String, String, String, String, String, String>,
                CompletableFuture<Try<Tuple7<String, String, String, String, String, String, String>>>> tryPar7 =
                tryPar(identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Try<Tuple7<String, String, String, String, String, String, String>>> par7Result =
                tryPar7.apply(tuple7("hello", "hello", "hello", "hello", "hello", "hello", "hello"));

        assertEquals(par7Result.get().get()._1(), "hello");
        assertEquals(par7Result.get().get()._2(), "hello");
        assertEquals(par7Result.get().get()._3(), "hello");
        assertEquals(par7Result.get().get()._4(), "hello");
        assertEquals(par7Result.get().get()._5(), "hello");
        assertEquals(par7Result.get().get()._6(), "hello");
        assertEquals(par7Result.get().get()._7(), "hello");

        // tryPar 8
        Function<Tuple8<String, String, String, String, String, String, String, String>,
                CompletableFuture<Try<Tuple8<String, String, String, String, String, String, String, String>>>> tryPar8 =
                tryPar(identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Try<Tuple8<String, String, String, String, String, String, String, String>>> par8Result =
                tryPar8.apply(tuple8("hello", "hello", "hello", "hello", "hello", "hello", "hello", "hello"));

        assertEquals(par8Result.get().get()._1(), "hello");
        assertEquals(par8Result.get().get()._2(), "hello");
        assertEquals(par8Result.get().get()._3(), "hello");
        assertEquals(par8Result.get().get()._4(), "hello");
        assertEquals(par8Result.get().get()._5(), "hello");
        assertEquals(par8Result.get().get()._6(), "hello");
        assertEquals(par8Result.get().get()._7(), "hello");
        assertEquals(par8Result.get().get()._8(), "hello");

        // tryPar 9
        Function<Tuple9<String, String, String, String, String, String, String, String, String>,
                CompletableFuture<Try<Tuple9<String, String, String, String, String, String, String, String, String>>>> tryPar9 =
                tryPar(identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Try<Tuple9<String, String, String, String, String, String, String, String, String>>> par9Result =
                tryPar9.apply(tuple9("hello", "hello", "hello", "hello", "hello", "hello", "hello", "hello", "hello"));

        assertEquals(par9Result.get().get()._1(), "hello");
        assertEquals(par9Result.get().get()._2(), "hello");
        assertEquals(par9Result.get().get()._3(), "hello");
        assertEquals(par9Result.get().get()._4(), "hello");
        assertEquals(par9Result.get().get()._5(), "hello");
        assertEquals(par9Result.get().get()._6(), "hello");
        assertEquals(par9Result.get().get()._7(), "hello");
        assertEquals(par9Result.get().get()._8(), "hello");
        assertEquals(par9Result.get().get()._9(), "hello");

        // tryPar 10
        Function<Tuple10<String, String, String, String, String, String, String, String, String, String>,
                CompletableFuture<Try<Tuple10<String, String, String, String, String, String, String, String, String, String>>>> tryPar10 =
                tryPar(identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync, identityAsync);

        CompletableFuture<Try<Tuple10<String, String, String, String, String, String, String, String, String, String>>> par10Result =
                tryPar10.apply(tuple10("hello", "hello", "hello", "hello", "hello", "hello", "hello", "hello", "hello", "hello"));

        assertEquals(par10Result.get().get()._1(), "hello");
        assertEquals(par10Result.get().get()._2(), "hello");
        assertEquals(par10Result.get().get()._3(), "hello");
        assertEquals(par10Result.get().get()._4(), "hello");
        assertEquals(par10Result.get().get()._5(), "hello");
        assertEquals(par10Result.get().get()._6(), "hello");
        assertEquals(par10Result.get().get()._7(), "hello");
        assertEquals(par10Result.get().get()._8(), "hello");
        assertEquals(par10Result.get().get()._9(), "hello");
        assertEquals(par10Result.get().get()._10(), "hello");
    }

    @Test
    public void testExceptionWrapper(){
        List<Throwable> exceptions =
                Arrays.asList(new RuntimeException("1"), new RuntimeException("2"), new RuntimeException("3"));

        Pipeline.ExceptionWrapper exceptionWrapper = new Pipeline.ExceptionWrapper(exceptions);
        assertEquals(exceptionWrapper.toString(), "java.lang.RuntimeException: 1, java.lang.RuntimeException: 2, java.lang.RuntimeException: 3");
        assertEquals(exceptionWrapper.getExceptions().size(), 3);
    }

    // Define a function that processes a single item
    Function<Integer, CompletableFuture<Try<Integer>>> itemProcessFunction = a -> {
        CompletableFuture<Try<Integer>> promise = new CompletableFuture<>();

        // Simulate a delayed remote call
        Thread thread = new Thread(){
            public void run(){
                try {
                    Thread.sleep(2);
                    promise.complete(Try.success(a * a));
                } catch (InterruptedException ex) {
                    promise.complete(Try.failure(ex));
                }
            }
        };

        thread.start();

        return promise;
    };

    @Test
    public void testNonBlockPar() throws ExecutionException, InterruptedException {
        List<Integer> input = IntStream.range(1, 10000)
                                .boxed()
                                .collect(Collectors.toList());

        Function<Collection<Integer>, CompletableFuture<Collection<Try<Integer>>>> parProcessFunc =
                Pipeline.nonBlockPar(10, itemProcessFunction);

        Collection<Try<Integer>> output = parProcessFunc.apply(input).get();

        assertEquals(input.size(), output.size());
    }

    @Test
    public void testNonBlockParEmptyInput() throws ExecutionException, InterruptedException {
        List<Integer> input = new LinkedList<>();

        Function<Collection<Integer>, CompletableFuture<Collection<Try<Integer>>>> parProcessFunc =
                Pipeline.nonBlockPar(10, itemProcessFunction);

        Collection<Try<Integer>> output = parProcessFunc.apply(input).get();

        assertEquals(input.size(), output.size());
    }
}
