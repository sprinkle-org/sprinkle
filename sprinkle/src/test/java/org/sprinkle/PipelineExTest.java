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
import org.sprinkle.Try;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.sprinkle.PipelineEx.*;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.sprinkle.Tuple.*;
import static org.junit.Assert.assertFalse;

/**
 * Created by mboussarov on 12/20/15.
 */
public class PipelineExTest {
    @Test
    public void testFoldLeft(){
        // Integers
        List<Integer> integerList = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        Integer zero = 0;

        BiFunction<Integer, Integer, Integer> sum = (a, b) -> {
            return a + b;
        };

        Integer result = foldLeft(integerList, zero, sum);
        assertEquals(result.intValue(), 55);

        // Strings
        List<String> stringList = Arrays.asList("world", " " , "hello", " ", "world", " " , "hello");
        String zeroString = "";

        BiFunction<String, String, String> concat = (a, b) -> {
            return a + b;
        };

        String stringResult = foldLeft(stringList, zeroString, concat);
        assertEquals(stringResult, "hello world hello world");
    }

    @Test
    public void testExtractProcessEnrich() throws Exception {
        class Context{
            private int value = 0;
            private int result = 0;

            void setValue(int value){
                this.value = value;
            }

            int getValue(){
                return value;
            }

            void setResult(int result){
                this.result = result;
            }

            int getResult(){
                return result;
            }
        }

        Function<Context, Try<Integer>> extract = a -> {
            return Try.success(a.getValue());
        };

        Function<Integer, CompletableFuture<Try<Integer>>> process = a -> {
            CompletableFuture<Try<Integer>> promise = new CompletableFuture<>();
            promise.complete(Try.success(a + 2));
            return promise;
        };

        Function<Tuple2<Context, Integer>, Try<Context>> enrich = a -> {
            Context resultContext = new Context();
            resultContext.setValue(a._1().getValue());
            resultContext.setResult(a._2());

            return Try.success(resultContext);
        };

        // Primary function
        Function<Context, CompletableFuture<Try<Context>>> composition =
                extractProcessEnrich(extract, process, enrich);

        Context context = new Context();
        context.setValue(2);

        CompletableFuture<Try<Context>> resultFuture = composition.apply(context);
        assertEquals(resultFuture.get().get().getResult(), 4);

        // Alternative function
        composition = xpe(extract, process, enrich);

        context = new Context();
        context.setValue(4);

        resultFuture = composition.apply(context);
        assertEquals(resultFuture.get().get().getResult(), 6);

    }

    @Test
    public void testSwitchCase() throws Exception {
        Function<String, Predicate<String>> condition = a -> {
            return b -> {
                return a.equalsIgnoreCase(b);
            };
        };

        Function<String, Function<String, CompletableFuture<Try<String>>>> process = a -> {
            return b -> {
                CompletableFuture<Try<String>> promise = new CompletableFuture<>();
                promise.complete(Try.success(a + " " + b));
                return promise;
            };
        };

        Function<String, CompletableFuture<Try<String>>> switchCaseComposition =
                switchCase(Arrays.<Tuple2<Predicate<String>, Function<String, CompletableFuture<Try<String>>>>>asList(
                    tuple2(condition.apply("first"), process.apply("hello")),
                    tuple2(condition.apply("second"), process.apply("hello")),
                    tuple2(condition.apply("third"), process.apply("hello"))
                ), new RuntimeException("No matching condition"));

        CompletableFuture<Try<String>> resultFuture = switchCaseComposition.apply("second");
        assertEquals(resultFuture.get().get(), "hello second");

        resultFuture = switchCaseComposition.apply("forth");
        assertTrue(resultFuture.get().isFailure());
    }

    @Test
    public void testProcessSequence() throws Exception {
        Function<String, Predicate<String>> condition = a -> {
            return b -> {
                return b.contains(a);
            };
        };

        Function<String, Function<String, CompletableFuture<Try<String>>>> process = a -> {
            return b -> {
                CompletableFuture<Try<String>> promise = new CompletableFuture<>();
                promise.complete(Try.success(a + " " + b));
                return promise;
            };
        };

        Function<String, CompletableFuture<Try<String>>> processSequenceComposition =
                processSequence(Arrays.<Tuple2<Predicate<String>, Function<String, CompletableFuture<Try<String>>>>>asList(
                        tuple2(condition.apply("first"), process.apply("hi first")),
                        tuple2(condition.apply("second"), process.apply("hello second")),
                        tuple2(condition.apply("first"), process.apply("hello again first"))
                ));

        CompletableFuture<Try<String>> resultFuture = processSequenceComposition.apply("first");
        assertTrue(resultFuture.get().get().contains("hi first"));
        assertTrue(resultFuture.get().get().contains("hello again first"));
        assertFalse(resultFuture.get().get().contains("hello second"));

        resultFuture = processSequenceComposition.apply("forth");
        assertEquals(resultFuture.get().get(), "forth");
    }

    @Test
    public void testNormalize() throws Exception {
        Function<Integer, CompletableFuture<Try<Integer>>> process = a -> {
            CompletableFuture<Try<Integer>> promise = new CompletableFuture<>();
            promise.complete(Try.success(a + 2));
            return promise;
        };

        Function<Integer, Try<String>> normalizeProcess = a -> {
            return Try.success("{" + a + "}");
        };

        Function<Integer, CompletableFuture<Try<String>>> normalizedFuture = normalize(process, normalizeProcess);

        CompletableFuture<Try<String>> normalizedResult = normalizedFuture.apply(2);
        assertEquals(normalizedResult.get().get(), "{4}");
    }
}
