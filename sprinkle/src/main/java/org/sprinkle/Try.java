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

import java.util.function.Function;

/**
 * Created by mboussarov on 6/23/15.
 *
 * Try monad used to wrap the result of a function execution capturing the actual outcome, or an exception if one occurred
 */
public abstract class Try<V> {

    private Try() {
    }

    public abstract Boolean isSuccess();

    public abstract Boolean isFailure();

    public abstract void throwException();

    public static <V> Try<V> failure(String message) {
        return new Failure<>(message);
    }

    public static <V> Try<V> failure(String message, Throwable ex) {
        return new Failure<>(message, ex);
    }

    public static <V> Try<V> failure(Throwable ex) {
        return new Failure<>(ex);
    }

    public static <V, U> Try<V> failure(Try<U> tryEx) { return new Failure<V>(tryEx.getException()); }

    public static <V> Try<V> success(V value) {
        return new Success<>(value);
    }

    public abstract V get();

    public abstract Throwable getException();

    public abstract V getOrElse(V v);

    public abstract <U> Try<U> map(Function<V, U> f);

    public abstract <U> Try<U> flatMap(Function<V, Try<U>> f);

    private static class Failure<V> extends Try<V> {

        private Throwable exception;

        public Failure(String message) {
            super();
            this.exception = new RuntimeException(message);
        }

        public Failure(String message, Throwable ex) {
            super();
            this.exception = ex;
        }

        public Failure(Throwable ex) {
            super();
            this.exception = ex;
        }

        @Override
        public Boolean isSuccess() {
            return false;
        }

        @Override
        public Boolean isFailure() {
            return true;
        }

        @Override
        public void throwException() {
            if(exception instanceof RuntimeException){
                throw (RuntimeException)exception;
            } else {
                throw new RuntimeException(this.exception);
            }
        }

        @Override
        public V get() {
            if(exception instanceof RuntimeException){
                throw (RuntimeException)exception;
            } else {
                throw new RuntimeException(this.exception);
            }
        }

        @Override
        public V getOrElse(V v) {
            return v;
        }

        @Override
        public <U> Try<U> map(Function<V, U> f) {
            return new Failure<>(exception);
        }

        @Override
        public <U> Try<U> flatMap(Function<V, Try<U>> f) {
            return new Failure<>(exception);
        }

        @Override
        public Throwable getException() {
            return exception;
        }
    }

    private static class Success<V> extends Try<V> {

        private V value;

        public Success(V value) {
            super();
            this.value = value;
        }

        @Override
        public Boolean isSuccess() {
            return true;
        }

        @Override
        public Boolean isFailure() {
            return false;
        }

        @Override
        public void throwException() {
            //log.error("Method throwException() called on a Success instance");
        }

        @Override
        public V get() {
            return value;
        }

        @Override
        public V getOrElse(V v) {
            return value;
        }

        @Override
        public <U> Try<U> map(Function<V, U> f) {
            return new Success<>(f.apply(value));
        }

        @Override
        public <U> Try<U> flatMap(Function<V, Try<U>> f) {
            return f.apply(value);
        }

        @Override
        public Exception getException() {
            return null;
        }
    }
}

