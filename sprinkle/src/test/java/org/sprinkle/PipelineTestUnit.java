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

import org.sprinkle.Pipeline;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.Ignore;
import org.sprinkle.Try;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class PipelineTestUnit extends TestCase {
    @Ignore
    @Test
    public void testCreateSyncPipeline() {
        Function<Float, Try<Float>> funSample = flt -> {
            return Try.success(flt);
        };

        Pipeline pipeline = Pipeline.createPipeline(funSample);

        System.out.print(pipeline.get().apply(200f));
    }

    @Ignore
    @Test
    public void testCreateAsyncPipeline() {
        Function<Float, CompletableFuture<Try<Float>>> funSample = flt -> {

//            CompletableFuture<Try<Float>> result = new CompletableFuture<>();
//            result.complete(Try.success(flt));
//            return result;

            return CompletableFuture.completedFuture(Try.success(flt));
        };

        Pipeline pipeline = Pipeline.createAsyncPipeline(funSample);

        Function<Float, CompletableFuture<Try<Float>>> funcFromPipeline = pipeline.<Float, CompletableFuture<Try<Float>>>get();

        //pipeline.<Float, CompletableFuture<Try<Float>>>get().

        CompletableFuture<Try<Float>> result = funcFromPipeline.apply(100.342f);

        result.thenAccept(a -> {
            if (a.isSuccess()) {
                System.out.println(a.get());
            } else {
                fail();
            }
        });


    }

    @Ignore
    @Test
    public void testParallel() {
        Function<Float, CompletableFuture<Try<Float>>> funSample1 = a -> {

            return CompletableFuture.completedFuture(Try.success(a));
        };

        Function<Float, CompletableFuture<Try<Float>>> funSample2 = b -> {
            return CompletableFuture.completedFuture(Try.success(b));
        };

        //CompletableFuture<Try<Tuple.Tuple2<Float, Float>>> future1 = Pipeline.<>tryPar(funSample1, funSample2);


        //Function<Float, CompletableFuture<Try<Float>>> fun1 = pipeline.get();

        //CompletableFuture<Try<Float>> result = fun1.apply(3.98f);


    }

}
