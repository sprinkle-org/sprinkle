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
import java.util.function.BiFunction;

/**
 * Created by mboussarov on 6/26/15.
 */
public abstract class Tuple<A, B> {
    // Factory methods
    public static <A, B> Tuple2 tuple(A a, B b) {
        return new Tuple2(a, b);
    }

    public static <A, B> Tuple2 tuple2(A a, B b) {
        return new Tuple2(a, b);
    }

    public static <A, B, C> Tuple3 tuple3(A a, B b, C c) {
        return new Tuple3(a, b, c);
    }

    public static <A, B, C> Tuple3 tuple3(Tuple2<A, B> t, C c) {
        return new Tuple3(t._1(), t._2(), c);
    }

    public static <A, B, C, D> Tuple4 tuple4(A a, B b, C c, D d) {
        return new Tuple4(a, b, c, d);
    }

    public static <A, B, C, D> Tuple4 tuple4(Tuple3<A, B, C> t, D d) {
        return new Tuple4(t._1(), t._2(), t._3(), d);
    }

    public static <A, B, C, D, E> Tuple5 tuple5(A a, B b, C c, D d, E e) {
        return new Tuple5(a, b, c, d, e);
    }

    public static <A, B, C, D, E> Tuple5 tuple5(Tuple4<A, B, C, D> t, E e) {
        return new Tuple5(t._1(), t._2(), t._3(), t._4(), e);
    }

    public static <A, B, C, D, E, F> Tuple6 tuple6(A a, B b, C c, D d, E e, F f) {
        return new Tuple6(a, b, c, d, e, f);
    }

    public static <A, B, C, D, E, F> Tuple6 tuple6(Tuple5<A, B, C, D, E> t, F f) {
        return new Tuple6(t._1(), t._2(), t._3(), t._4(), t._5(), f);
    }

    public static <A, B, C, D, E, F, G> Tuple7 tuple7(A a, B b, C c, D d, E e, F f, G g) {
        return new Tuple7(a, b, c, d, e, f, g);
    }

    public static <A, B, C, D, E, F, G> Tuple7 tuple7(Tuple6<A, B, C, D, E, F> t, G g) {
        return new Tuple7(t._1(), t._2(), t._3(), t._4(), t._5(), t._6(), g);
    }

    public static <A, B, C, D, E, F, G, H> Tuple8 tuple8(A a, B b, C c, D d, E e, F f, G g, H h) {
        return new Tuple8(a, b, c, d, e, f, g, h);
    }

    public static <A, B, C, D, E, F, G, H> Tuple8 tuple8(Tuple7<A, B, C, D, E, F, G> t, H h) {
        return new Tuple8(t._1(), t._2(), t._3(), t._4(), t._5(), t._6(), t._7(), h);
    }

    public static <A, B, C, D, E, F, G, H, I> Tuple9 tuple9(A a, B b, C c, D d, E e, F f, G g, H h, I i) {
        return new Tuple9(a, b, c, d, e, f, g, h, i);
    }

    public static <A, B, C, D, E, F, G, H, I> Tuple9 tuple9(Tuple8<A, B, C, D, E, F, G, H> t, I i) {
        return new Tuple9(t._1(), t._2(), t._3(), t._4(), t._5(), t._6(), t._7(), t._8(), i);
    }

    public static <A, B, C, D, E, F, G, H, I, J> Tuple10 tuple10(A a, B b, C c, D d, E e, F f, G g, H h, I i, J j) {
        return new Tuple10(a, b, c, d, e, f, g, h, i, j);
    }

    public static <A, B, C, D, E, F, G, H, I, J> Tuple10 tuple10(Tuple9<A, B, C, D, E, F, G, H, I> t, J j) {
        return new Tuple10(t._1(), t._2(), t._3(), t._4(), t._5(), t._6(), t._7(), t._8(), t._9(), j);
    }

    // Bridging tuple to future
    protected static <A, B, C> CompletableFuture<Try<C>> composeFuture(A a, CompletableFuture<Try<B>> cf, BiFunction<A, B, C> f) {
        final CompletableFuture<Try<C>> promise = new CompletableFuture<>();

        cf.thenAccept(b -> {
            if (b.isSuccess()) {
                promise.complete(Try.success(f.apply(a, b.get())));
            } else {
                promise.complete(Try.failure(b.getException()));
            }
        });

        return promise;
    }

    public static <A, B> CompletableFuture<Try<Tuple2<A, B>>> composeTuple2(A t, CompletableFuture<Try<B>> cf) {
        return composeFuture(t, cf, (a, b) -> {
            return tuple2(a, b);
        });
    }

    public static <A, B, C> CompletableFuture<Try<Tuple3<A, B, C>>> composeTuple3(Tuple2<A, B> t, CompletableFuture<Try<C>> cf) {
        return composeFuture(t, cf, (a, b) -> {
            return tuple3(a, b);
        });
    }

    public static <A, B, C, D> CompletableFuture<Try<Tuple4<A, B, C, D>>> composeTuple4(Tuple3<A, B, C> t, CompletableFuture<Try<D>> cf) {
        return composeFuture(t, cf, (a, b) -> {
            return tuple4(a, b);
        });
    }

    public static <A, B, C, D, E> CompletableFuture<Try<Tuple5<A, B, C, D, E>>> composeTuple5(Tuple4<A, B, C, D> t, CompletableFuture<Try<E>> cf) {
        return composeFuture(t, cf, (a, b) -> {
            return tuple5(a, b);
        });
    }

    public static <A, B, C, D, E, F> CompletableFuture<Try<Tuple6<A, B, C, D, E, F>>> composeTuple6(Tuple5<A, B, C, D, E> t, CompletableFuture<Try<F>> cf) {
        return composeFuture(t, cf, (a, b) -> {
            return tuple6(a, b);
        });
    }

    public static <A, B, C, D, E, F, G> CompletableFuture<Try<Tuple7<A, B, C, D, E, F, G>>> composeTuple7(Tuple6<A, B, C, D, E, F> t, CompletableFuture<Try<G>> cf) {
        return composeFuture(t, cf, (a, b) -> {
            return tuple7(a, b);
        });
    }

    public static <A, B, C, D, E, F, G, H> CompletableFuture<Try<Tuple8<A, B, C, D, E, F, G, H>>> composeTuple8(Tuple7<A, B, C, D, E, F, G> t, CompletableFuture<Try<H>> cf) {
        return composeFuture(t, cf, (a, b) -> {
            return tuple8(a, b);
        });
    }

    public static <A, B, C, D, E, F, G, H, I> CompletableFuture<Try<Tuple9<A, B, C, D, E, F, G, H, I>>> composeTuple9(Tuple8<A, B, C, D, E, F, G, H> t, CompletableFuture<Try<I>> cf) {
        return composeFuture(t, cf, (a, b) -> {
            return tuple9(a, b);
        });
    }

    public static <A, B, C, D, E, F, G, H, I, J> CompletableFuture<Try<Tuple10<A, B, C, D, E, F, G, H, I, J>>> composeTuple10(Tuple9<A, B, C, D, E, F, G, H, I> t, CompletableFuture<Try<J>> cf) {
        return composeFuture(t, cf, (a, b) -> {
            return tuple10(a, b);
        });
    }

    public abstract A _1();

    public abstract B _2();

    public abstract String toString();

    // Tuple definitions
    public static class Tuple2<A, B> extends Tuple<A, B> {
        A a;
        B b;

        private Tuple2(A a, B b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public A _1() {
            return a;
        }

        @Override
        public B _2() {
            return b;
        }

        @Override
        public String toString() {
            return "Tuple2{" +
                    "_1=" + a +
                    ", _2=" + b +
                    '}';
        }
    }

    public static class Tuple3<A, B, C> extends Tuple<A, B> {
        A a;
        B b;
        C c;

        private Tuple3(A a, B b, C c) {
            this.a = a;
            this.b = b;
            this.c = c;
        }

        @Override
        public A _1() {
            return a;
        }

        @Override
        public B _2() {
            return b;
        }

        public C _3() {
            return c;
        }

        @Override
        public String toString() {
            return "Tuple3{" +
                    "_1=" + a +
                    ", _2=" + b +
                    ", _3=" + c +
                    '}';
        }
    }

    public static class Tuple4<A, B, C, D> extends Tuple<A, B> {
        A a;
        B b;
        C c;
        D d;

        private Tuple4(A a, B b, C c, D d) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
        }

        @Override
        public A _1() {
            return a;
        }

        @Override
        public B _2() {
            return b;
        }

        public C _3() {
            return c;
        }

        public D _4() {
            return d;
        }

        @Override
        public String toString() {
            return "Tuple4{" +
                    "_1=" + a +
                    ", _2=" + b +
                    ", _3=" + c +
                    ", _4=" + d +
                    '}';
        }
    }

    public static class Tuple5<A, B, C, D, E> extends Tuple<A, B> {
        A a;
        B b;
        C c;
        D d;
        E e;

        private Tuple5(A a, B b, C c, D d, E e) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
        }

        @Override
        public A _1() {
            return a;
        }

        @Override
        public B _2() {
            return b;
        }

        public C _3() {
            return c;
        }

        public D _4() {
            return d;
        }

        public E _5() {
            return e;
        }

        @Override
        public String toString() {
            return "Tuple5{" +
                    "_1=" + a +
                    ", _2=" + b +
                    ", _3=" + c +
                    ", _4=" + d +
                    ", _5=" + e +
                    '}';
        }
    }

    public static class Tuple6<A, B, C, D, E, F> extends Tuple<A, B> {
        A a;
        B b;
        C c;
        D d;
        E e;
        F f;

        private Tuple6(A a, B b, C c, D d, E e, F f) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.f = f;
        }

        @Override
        public A _1() {
            return a;
        }

        @Override
        public B _2() {
            return b;
        }

        public C _3() {
            return c;
        }

        public D _4() {
            return d;
        }

        public E _5() {
            return e;
        }

        public F _6() {
            return f;
        }

        @Override
        public String toString() {
            return "Tuple6{" +
                    "_1=" + a +
                    ", _2=" + b +
                    ", _3=" + c +
                    ", _4=" + d +
                    ", _5=" + e +
                    ", _6=" + f +
                    '}';
        }
    }

    public static class Tuple7<A, B, C, D, E, F, G> extends Tuple<A, B> {
        A a;
        B b;
        C c;
        D d;
        E e;
        F f;
        G g;

        private Tuple7(A a, B b, C c, D d, E e, F f, G g) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.f = f;
            this.g = g;
        }

        @Override
        public A _1() {
            return a;
        }

        @Override
        public B _2() {
            return b;
        }

        public C _3() {
            return c;
        }

        public D _4() {
            return d;
        }

        public E _5() {
            return e;
        }

        public F _6() {
            return f;
        }

        public G _7() {
            return g;
        }

        @Override
        public String toString() {
            return "Tuple7{" +
                    "_1=" + a +
                    ", _2=" + b +
                    ", _3=" + c +
                    ", _4=" + d +
                    ", _5=" + e +
                    ", _6=" + f +
                    ", _7=" + g +
                    '}';
        }
    }

    public static class Tuple8<A, B, C, D, E, F, G, H> extends Tuple<A, B> {
        A a;
        B b;
        C c;
        D d;
        E e;
        F f;
        G g;
        H h;

        private Tuple8(A a, B b, C c, D d, E e, F f, G g, H h) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.f = f;
            this.g = g;
            this.h = h;
        }

        @Override
        public A _1() {
            return a;
        }

        @Override
        public B _2() {
            return b;
        }

        public C _3() {
            return c;
        }

        public D _4() {
            return d;
        }

        public E _5() {
            return e;
        }

        public F _6() {
            return f;
        }

        public G _7() {
            return g;
        }

        public H _8() {
            return h;
        }

        @Override
        public String toString() {
            return "Tuple8{" +
                    "_1=" + a +
                    ", _2=" + b +
                    ", _3=" + c +
                    ", _4=" + d +
                    ", _5=" + e +
                    ", _6=" + f +
                    ", _7=" + g +
                    ", _8=" + h +
                    '}';
        }
    }

    public static class Tuple9<A, B, C, D, E, F, G, H, I> extends Tuple<A, B> {
        A a;
        B b;
        C c;
        D d;
        E e;
        F f;
        G g;
        H h;
        I i;

        private Tuple9(A a, B b, C c, D d, E e, F f, G g, H h, I i) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.f = f;
            this.g = g;
            this.h = h;
            this.i = i;
        }

        @Override
        public A _1() {
            return a;
        }

        @Override
        public B _2() {
            return b;
        }

        public C _3() {
            return c;
        }

        public D _4() {
            return d;
        }

        public E _5() {
            return e;
        }

        public F _6() {
            return f;
        }

        public G _7() {
            return g;
        }

        public H _8() {
            return h;
        }

        public I _9() {
            return i;
        }

        @Override
        public String toString() {
            return "Tuple9{" +
                    "_1=" + a +
                    ", _2=" + b +
                    ", _3=" + c +
                    ", _4=" + d +
                    ", _5=" + e +
                    ", _6=" + f +
                    ", _7=" + g +
                    ", _8=" + h +
                    ", _9=" + i +
                    '}';
        }
    }

    public static class Tuple10<A, B, C, D, E, F, G, H, I, J> extends Tuple<A, B> {
        A a;
        B b;
        C c;
        D d;
        E e;
        F f;
        G g;
        H h;
        I i;
        J j;

        private Tuple10(A a, B b, C c, D d, E e, F f, G g, H h, I i, J j) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
            this.f = f;
            this.g = g;
            this.h = h;
            this.i = i;
            this.j = j;
        }

        @Override
        public A _1() {
            return a;
        }

        @Override
        public B _2() {
            return b;
        }

        public C _3() {
            return c;
        }

        public D _4() {
            return d;
        }

        public E _5() {
            return e;
        }

        public F _6() {
            return f;
        }

        public G _7() {
            return g;
        }

        public H _8() {
            return h;
        }

        public I _9() {
            return i;
        }

        public J _10() {
            return j;
        }

        @Override
        public String toString() {
            return "Tuple10{" +
                    "_1=" + a +
                    ", _2=" + b +
                    ", _3=" + c +
                    ", _4=" + d +
                    ", _5=" + e +
                    ", _6=" + f +
                    ", _7=" + g +
                    ", _8=" + h +
                    ", _9=" + i +
                    ", _10=" + j +
                    '}';
        }
    }
}
