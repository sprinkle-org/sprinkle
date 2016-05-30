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

import org.sprinkle.Try;
import junit.framework.TestCase;
import org.junit.Test;

import java.util.function.Function;

/**
 * Created by vkulkarni on 12/9/15.
 *
 * Unit tests for the Try monad
 */
public class TryTest extends TestCase {
    @Test
    public void testTrySuccess() {
        Try<String> stringTry = Try.success("hello");

        // Basic functions
        assertTrue(stringTry.isSuccess());
        assertFalse(stringTry.isFailure());
        assertEquals(stringTry.get(), "hello");
        assertEquals(stringTry.getOrElse("goodbye"), "hello");
        try {
            stringTry.throwException();
        }catch(Exception ex){
            fail("No exception should be thrown");
        }

        // map
        Function<String, String> mapFunc = a -> {
            return a + " world";
        };

        Try<String> mapTry = stringTry.map(mapFunc);

        assertEquals(mapTry.get(), "hello world");

        // flatMap
        Function<String, Try<String>> flatMapFunc = a -> {
            return Try.success(a + " world");
        };

        Try<String> flatMapTry = stringTry.flatMap(flatMapFunc);

        assertEquals(flatMapTry.get(), "hello world");
    }

    @Test
    public void testTryFail() {
        Try<String> stringTry = Try.failure(new TryFailException("My exception"));

        // Basic functions
        assertFalse(stringTry.isSuccess());
        assertTrue(stringTry.isFailure());
        try {
            stringTry.get();
            fail("get on failure Try monad must throw an exception");
        } catch (RuntimeException ex){
            // okay
        }
        assertEquals(stringTry.getOrElse("goodbye"), "goodbye");

        try {
            stringTry.throwException();
            fail("throwException on failure Try monad must throw an exception");
        }catch(RuntimeException ex){
            // Okay
        }

        // map
        Function<String, String> mapFunc = a -> {
            return a + " world";
        };

        Try<String> mapTry = stringTry.map(mapFunc);

        assertTrue(mapTry.getException() instanceof TryFailException);
        assertEquals(mapTry.getException().getMessage(), "My exception");

        // flatMap
        Function<String, Try<String>> flatMapFunc = a -> {
            return Try.success(a + " world");
        };

        Try<String> flatMapTry = stringTry.flatMap(flatMapFunc);

        assertTrue(mapTry.getException() instanceof TryFailException);
        assertEquals(mapTry.getException().getMessage(), "My exception");
    }

    private class TryFailException extends Throwable {
        TryFailException(String str) {
            super(str);
        }
    }
}
