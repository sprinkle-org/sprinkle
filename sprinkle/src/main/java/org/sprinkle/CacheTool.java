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

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * Created by mboussarov on 6/25/15.
 */
public class CacheTool {
    // Construct a cache for a particular future
    public static <K, V> Cache<K, CompletableFuture<Try<V>>> constructCache(CacheBuilder<K, CompletableFuture<Try<V>>> cacheBuilder,
                                                                            Function<K, CompletableFuture<Try<V>>> future){
        return cacheBuilder.build(new CacheLoader<K, CompletableFuture<Try<V>>>() {
            public CompletableFuture<Try<V>> load(K key) { // no checked exception
                return future.apply(key);
            }
        });
    }

    // Construct a get function for this cache
    public static <K, V> Function<K, CompletableFuture<Try<V>>> withCache(Cache<K, CompletableFuture<Try<V>>> cache){
        return a -> {
            try {
                return ((LoadingCache<K, CompletableFuture<Try<V>>>)cache).get(a);
            } catch(Exception ex){
                CompletableFuture<Try<V>> errorFuture = new CompletableFuture<Try<V>>();
                errorFuture.complete(Try.failure(ex));
                return errorFuture;
            }
        };
    }

    // Construct a get function for this cache that get a value after extracting the key from the input
    public static <K, V, KK> Function<KK, CompletableFuture<Try<V>>> withCache(Cache<K, CompletableFuture<Try<V>>> cache,
                                                                               Function<KK, Try<K>> getKeyFunc){
        Function<Throwable, CompletableFuture<Try<V>>> errorFunc = a -> {
            CompletableFuture<Try<V>> errorFuture = new CompletableFuture<>();
            errorFuture.complete(Try.failure(a));
            return errorFuture;
        };

        return a -> {
            try {
                Try<K> tryKey = getKeyFunc.apply(a);
                if(tryKey.isSuccess()){
                    return ((LoadingCache<K, CompletableFuture<Try<V>>>)cache).get(tryKey.get());
                } else {
                    CompletableFuture<Try<V>> errorFuture = new CompletableFuture<Try<V>>();
                    errorFuture.complete(Try.failure(tryKey.getException()));
                    return errorFunc.apply(tryKey.getException());
                }
            } catch(Throwable ex){
                return errorFunc.apply(ex);
            }
        };
    }

    // Construct a function wrapper with cache, the benefit/drawback is that the cache is hidden
    public static <K, V> Function<K, CompletableFuture<Try<V>>> withCache(CacheBuilder<K, CompletableFuture<Try<V>>> cacheBuilder,
                                                                            Function<K, CompletableFuture<Try<V>>> future){
        final Cache<K, CompletableFuture<Try<V>>> cache = cacheBuilder.build(new CacheLoader<K, CompletableFuture<Try<V>>>() {
            public CompletableFuture<Try<V>> load(K key) { // no checked exception
                return future.apply(key);
            }
        });

        // Close around the cache
        return withCache(cache);
    }

    // Construct a function wrapper with cache, the benefit/drawback is that the cache is hidden
    // Gets a function that extracts the key from the input
    public static <K, V, KK> Function<KK, CompletableFuture<Try<V>>> withCache(CacheBuilder<K, CompletableFuture<Try<V>>> cacheBuilder,
                                                                          Function<K, CompletableFuture<Try<V>>> future,
                                                                          Function<KK, Try<K>> getKeyFunc){
        final Cache<K, CompletableFuture<Try<V>>> cache = cacheBuilder.build(new CacheLoader<K, CompletableFuture<Try<V>>>() {
            public CompletableFuture<Try<V>> load(K key) { // no checked exception
                return future.apply(key);
            }
        });

        // Close around the cache
        return withCache(cache, getKeyFunc);
    }
}
