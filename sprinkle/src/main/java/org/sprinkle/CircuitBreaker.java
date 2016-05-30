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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.sprinkle.Tuple.*;

/**
 * Created by mboussarov on 6/27/15.
 */
public class CircuitBreaker {
    // Max size of the processing queue
    public static final int MAX_QUEUE_SIZE = 10000;

    // Circuit breaker hook
    public static <A, B> Function<A, CompletableFuture<Try<B>>> withCircuitBreaker(Function<A, CompletableFuture<Try<B>>> func, ExecutorService executor,
                                                                                   int maxErrors, Duration resetTimeout) {
        final CircuitBreakerStateManager circuitBreakerStateManager =
                CircuitBreakerStateManager.createStateManager(maxErrors, resetTimeout, executor);

        return withCircuitBreaker(func, executor, circuitBreakerStateManager);
    }

    // Circuit breaker hook
    public static <A, B> Function<A, CompletableFuture<Try<B>>> withCircuitBreaker(Function<A, CompletableFuture<Try<B>>> func, ExecutorService executor,
                                                                                   final CircuitBreakerStateManager circuitBreakerStateManager) {
        // Queue that will be used for the detached async processing
        final BlockingQueue<Tuple2<A, CompletableFuture<Try<B>>>> queue = new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);

        // The executor with a single thread that will process all the incoming messages
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        executorService.execute(() -> {
            // Consume and process any messages in async manner
            while(true){
                try {
                    // Block until message comes up
                    // The tuple contains the following: argument, callback future
                    Tuple2<A, CompletableFuture<Try<B>>> tuple = queue.take();

                    try {
                        circuitBreakerStateManager.toProcess();

                        CompletableFuture.runAsync(() -> {
                            // Run the wrapped function asynchronously
                            CompletableFuture<Try<B>> wrappedFuture = func.apply(tuple._1());
                            // Once the wrapped future is completed, complete the future returned to the caller
                            wrappedFuture.thenAccept(a -> {
                                tuple._2().complete(a);
                                // Accumulate success and failures
                                circuitBreakerStateManager.onComplete(a);
                            });
                        }, executor);
                    } catch (CircuitBreakerOpenException ex){
                        // Circuit breaker is open, do not call the wrapped function
                        CompletableFuture.runAsync(() -> {
                            // Complete the future directly with exception
                            tuple._2().complete(Try.failure(circuitBreakerStateManager.CIRCUIT_BREAKER_OPEN_EXCEPTION));
                        }, executor);
                    }
                } catch (InterruptedException ex){
                    // Log whatever and exit the processing
                }
            }
        });

        return a -> {
            // Goes to the caller, completes once the wrapped function is completed
            CompletableFuture<Try<B>> future = new CompletableFuture<>();
            try {
                queue.add(tuple(a, future));
            }catch (Exception ex){
                // Most probably the queue limit is reached
                future.complete(Try.failure(ex));
            }
            return future;
        };
    }

    // Class definitions
    public static class CircuitBreakerStateManager {
        // Instance of the circuit breaker open exception, do not keep the stack trace
        public static final CircuitBreakerOpenException CIRCUIT_BREAKER_OPEN_EXCEPTION =
                new CircuitBreakerOpenException("CircuitBreakerOpenException", null, true, false);

        // Scheduler used to reset the circuit breaker
        private final ScheduledExecutorService scheduler;
        // Executor to run the callbacks
        private final ExecutorService executor;

        // Instances of the individual states
        private ClosedState closedState;
        private OpenState openState;
        private HalfOpenState halfOpenState;

        // By default the state is closed - allows traffic
        private AtomicReference<State> activeState;

        // Listeners
        private final List<Runnable> closedListeners;
        private final List<Runnable> openListeners;
        private final List<Runnable> halfOpenListeners;

        // Factory method
        public static CircuitBreakerStateManager createStateManager(int maxFailures,
                                                                    Duration resetTimeout,
                                                                    ExecutorService executor){
            return new CircuitBreakerStateManager(maxFailures, resetTimeout, executor);
        }

        private CircuitBreakerStateManager(int maxFailures, Duration resetTimeout, ExecutorService executor){
            closedState = new ClosedState(maxFailures, resetTimeout,
                    a -> activateOpenState(a));
            openState = new OpenState(maxFailures, resetTimeout,
                    a -> activateHalfOpenState(a));
            halfOpenState = new HalfOpenState(maxFailures, resetTimeout,
                    a -> activateOpenState(a), a -> activateClosedState(a) );

            scheduler = Executors.newScheduledThreadPool(1);
            this.executor = executor;

            closedListeners = new ArrayList<>();
            openListeners = new ArrayList<>();
            halfOpenListeners = new ArrayList<>();

            // By default the state is closed - allows traffic
            activeState = new AtomicReference<>(closedState);
        }

        public void toProcess() throws CircuitBreakerOpenException {
            activeState.get().toProcess();
        }

        // Called when a wrapped future is completed
        public void onComplete(Try<?> response){
            // Notify just the active state something is completed
            if(response.isSuccess()){
                activeState.get().onSuccess();
            } else {
                activeState.get().onFailure();
            }
        };

        public void addClosedListener(Runnable runnable){
            closedListeners.add(runnable);
        }

        public void addOpenListener(Runnable runnable){
            openListeners.add(runnable);
        }

        public void addHalfOpenListener(Runnable runnable){
            halfOpenListeners.add(runnable);
        }

        public void activateClosedState(State fromState){
            activateState(fromState, closedState);
            closedListeners.forEach(a -> executor.submit(a));
        }

        public void activateOpenState(State fromState){
            activateState(fromState, openState);
            openListeners.forEach(a -> executor.submit(a));
        }

        public void activateHalfOpenState(State fromState){
            activateState(fromState, halfOpenState);
            halfOpenListeners.forEach(a -> executor.submit(a));
        }

        private void activateState(State fromState, State toState){
            if(activeState.compareAndSet(fromState, toState)){
                toState.activate();
            };
        }

        abstract class State {
            // Properties
            final int maxFailures;
            final Duration resetTimeout;

            State(int maxFailures, Duration resetTimeout){
                this.maxFailures = maxFailures;
                this.resetTimeout = resetTimeout;
            }

            // State change method
            abstract void activate();

            // Verify if processing is allowed
            abstract void toProcess() throws CircuitBreakerOpenException;

            // Callback methods
            abstract void onSuccess();
            abstract void onFailure();
        }

        private class ClosedState extends State {
            AtomicInteger numberFailures = new AtomicInteger(0);
            Consumer<State> funcToOpen;

            ClosedState(int maxFailures, Duration resetTimeout, Consumer<State> funcToOpen) {
                super(maxFailures, resetTimeout);
                this.funcToOpen = funcToOpen;
            }

            @Override
            void activate() {
                numberFailures.set(0);
            }

            @Override
            void toProcess() throws CircuitBreakerOpenException {
                // Do nothing
            }

            @Override
            void onSuccess() {
                // If success - reset the counter
                numberFailures.set(0);
            }

            @Override
            void onFailure() {
                if(numberFailures.getAndIncrement() >= maxFailures){
                    try {
                        funcToOpen.accept(this);
                    } catch (Exception ex) {
                        //
                    }
                }
            }
        }

        private class OpenState extends State {
            Consumer<State> funcToHalfOpen;

            OpenState(int maxFailures, Duration resetTimeout,
                      Consumer<State> funcToHalfOpen){
                super(maxFailures, resetTimeout);
                this.funcToHalfOpen = funcToHalfOpen;
            }

            @Override
            void activate() {
                // Init the time to reset
                try {
                    scheduler.schedule(() -> funcToHalfOpen.accept(this),
                            resetTimeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (Exception ex){
                    // If unable to schedule - reset immediately
                    funcToHalfOpen.accept(this);
                }
            }

            @Override
            void toProcess() throws CircuitBreakerOpenException {
                throw CIRCUIT_BREAKER_OPEN_EXCEPTION;
            }

            @Override
            void onSuccess() {
                // Do nothing
            }

            @Override
            void onFailure() {
                // Do nothing
            }
        }

        private class HalfOpenState extends State {
            AtomicBoolean processed = new AtomicBoolean(false);
            Consumer<State> funcToOpen;
            Consumer<State> funcToClosed;

            AtomicReference<Future<?>> scheduledFutureAtomicReference = new AtomicReference<>(new CompletableFuture<Nothing>());

            HalfOpenState(int maxFailures, Duration resetTimeout,
                          Consumer<State> funcToOpen, Consumer<State> funcToClosed){
                super(maxFailures, resetTimeout);
                this.funcToOpen = funcToOpen;
                this.funcToClosed = funcToClosed;
            }

            @Override
            void activate() {
                processed.set(false);
                // Init the time to reset in case the state cannot be changed
                try {
                    // Do I have to cancel that and release the thread on state change
                    ScheduledFuture<?> future = scheduler.schedule(() -> funcToClosed.accept(this),
                            resetTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    scheduledFutureAtomicReference.set(future);
                } catch (Exception ex){
                    // If unable to schedule - reset immediately
                    funcToClosed.accept(this);
                }
            }

            @Override
            void toProcess() throws CircuitBreakerOpenException {
                if(processed.get()){
                    throw CIRCUIT_BREAKER_OPEN_EXCEPTION;
                } else {
                    processed.compareAndSet(false, true);
                }
            }

            @Override
            void onSuccess() {
                // Move back to closed
                funcToClosed.accept(this);
                // Cancel any scheduled task
                scheduledFutureAtomicReference.get().cancel(false);
            }

            @Override
            void onFailure() {
                // Move back to open
                funcToOpen.accept(this);
                // Cancel any scheduled task
                scheduledFutureAtomicReference.get().cancel(false);
            }
        }
    }

    public static class CircuitBreakerOpenException extends Exception {
        public CircuitBreakerOpenException(){}

        public CircuitBreakerOpenException(String message) {
            super(message);
        }

        public CircuitBreakerOpenException(String message, Throwable cause) {
            super(message, cause);
        }

        public CircuitBreakerOpenException(Throwable cause) {
            super(cause);
        }

        public CircuitBreakerOpenException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
