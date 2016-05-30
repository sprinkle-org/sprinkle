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
import org.junit.Test;
import org.sprinkle.Tuple;

import java.util.concurrent.CompletableFuture;

import static org.sprinkle.Tuple.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by mboussarov on 12/9/15.
 *
 * Unit tests for the tuple classes
 */
public class TupleTest {

    @Test
    public void constructTupleTest() {
        Tuple t = tuple("a", 1);
        assertEquals(t._1(), "a");
        assertEquals(t._2(), 1);
        assertEquals(t.toString(), "Tuple2{_1=a, _2=1}");

        Tuple2 t2 = tuple2("a", 1);
        assertEquals(t2._1(), "a");
        assertEquals(t2._2(), 1);
        assertEquals(t2.toString(), "Tuple2{_1=a, _2=1}");

        Tuple3 t3 = tuple3("a", 1, "b");
        assertEquals(t3._1(), "a");
        assertEquals(t3._2(), 1);
        assertEquals(t3._3(), "b");
        assertEquals(t3.toString(), "Tuple3{_1=a, _2=1, _3=b}");

        Tuple4 t4 = tuple4("a", 1, "b", 2);
        assertEquals(t4._1(), "a");
        assertEquals(t4._2(), 1);
        assertEquals(t4._3(), "b");
        assertEquals(t4._4(), 2);
        assertEquals(t4.toString(), "Tuple4{_1=a, _2=1, _3=b, _4=2}");

        Tuple5 t5 = tuple5("a", 1, "b", 2, "c");
        assertEquals(t5._1(), "a");
        assertEquals(t5._2(), 1);
        assertEquals(t5._3(), "b");
        assertEquals(t5._4(), 2);
        assertEquals(t5._5(), "c");
        assertEquals(t5.toString(), "Tuple5{_1=a, _2=1, _3=b, _4=2, _5=c}");

        Tuple6 t6 = tuple6("a", 1, "b", 2, "c", 3);
        assertEquals(t6._1(), "a");
        assertEquals(t6._2(), 1);
        assertEquals(t6._3(), "b");
        assertEquals(t6._4(), 2);
        assertEquals(t6._5(), "c");
        assertEquals(t6._6(), 3);
        assertEquals(t6.toString(), "Tuple6{_1=a, _2=1, _3=b, _4=2, _5=c, _6=3}");

        Tuple7 t7 = tuple7("a", 1, "b", 2, "c", 3, "d");
        assertEquals(t7._1(), "a");
        assertEquals(t7._2(), 1);
        assertEquals(t7._3(), "b");
        assertEquals(t7._4(), 2);
        assertEquals(t7._5(), "c");
        assertEquals(t7._6(), 3);
        assertEquals(t7._7(), "d");
        assertEquals(t7.toString(), "Tuple7{_1=a, _2=1, _3=b, _4=2, _5=c, _6=3, _7=d}");

        Tuple8 t8 = tuple8("a", 1, "b", 2, "c", 3, "d", 4);
        assertEquals(t8._1(), "a");
        assertEquals(t8._2(), 1);
        assertEquals(t8._3(), "b");
        assertEquals(t8._4(), 2);
        assertEquals(t8._5(), "c");
        assertEquals(t8._6(), 3);
        assertEquals(t8._7(), "d");
        assertEquals(t8._8(), 4);
        assertEquals(t8.toString(), "Tuple8{_1=a, _2=1, _3=b, _4=2, _5=c, _6=3, _7=d, _8=4}");

        Tuple9 t9 = tuple9("a", 1, "b", 2, "c", 3, "d", 4, "e");
        assertEquals(t9._1(), "a");
        assertEquals(t9._2(), 1);
        assertEquals(t9._3(), "b");
        assertEquals(t9._4(), 2);
        assertEquals(t9._5(), "c");
        assertEquals(t9._6(), 3);
        assertEquals(t9._7(), "d");
        assertEquals(t9._8(), 4);
        assertEquals(t9._9(), "e");
        assertEquals(t9.toString(), "Tuple9{_1=a, _2=1, _3=b, _4=2, _5=c, _6=3, _7=d, _8=4, _9=e}");

        Tuple10 t10 = tuple10("a", 1, "b", 2, "c", 3, "d", 4, "e", 5);
        assertEquals(t10._1(), "a");
        assertEquals(t10._2(), 1);
        assertEquals(t10._3(), "b");
        assertEquals(t10._4(), 2);
        assertEquals(t10._5(), "c");
        assertEquals(t10._6(), 3);
        assertEquals(t10._7(), "d");
        assertEquals(t10._8(), 4);
        assertEquals(t10._9(), "e");
        assertEquals(t10._10(), 5);
        assertEquals(t10.toString(), "Tuple10{_1=a, _2=1, _3=b, _4=2, _5=c, _6=3, _7=d, _8=4, _9=e, _10=5}");
    }

    @Test
    public void tupleCompositionTest(){
        Tuple3<String, String, String> t3 = tuple3(tuple2("1", "2"), "3");
        assertEquals(t3._1(), "1");
        assertEquals(t3._2(), "2");
        assertEquals(t3._3(), "3");

        Tuple4<String, String, String, String> t4 = tuple4(t3, "4");
        assertEquals(t4._1(), "1");
        assertEquals(t4._2(), "2");
        assertEquals(t4._3(), "3");
        assertEquals(t4._4(), "4");

        Tuple5<String, String, String, String, String> t5 = tuple5(t4, "5");
        assertEquals(t5._1(), "1");
        assertEquals(t5._2(), "2");
        assertEquals(t5._3(), "3");
        assertEquals(t5._4(), "4");
        assertEquals(t5._5(), "5");

        Tuple6<String, String, String, String, String, String> t6 = tuple6(t5, "6");
        assertEquals(t6._1(), "1");
        assertEquals(t6._2(), "2");
        assertEquals(t6._3(), "3");
        assertEquals(t6._4(), "4");
        assertEquals(t6._5(), "5");
        assertEquals(t6._6(), "6");

        Tuple7<String, String, String, String, String, String, String> t7 = tuple7(t6, "7");
        assertEquals(t7._1(), "1");
        assertEquals(t7._2(), "2");
        assertEquals(t7._3(), "3");
        assertEquals(t7._4(), "4");
        assertEquals(t7._5(), "5");
        assertEquals(t7._6(), "6");
        assertEquals(t7._7(), "7");

        Tuple8<String, String, String, String, String, String, String, String> t8 = tuple8(t7, "8");
        assertEquals(t8._1(), "1");
        assertEquals(t8._2(), "2");
        assertEquals(t8._3(), "3");
        assertEquals(t8._4(), "4");
        assertEquals(t8._5(), "5");
        assertEquals(t8._6(), "6");
        assertEquals(t8._7(), "7");
        assertEquals(t8._8(), "8");

        Tuple9<String, String, String, String, String, String, String, String, String> t9 = tuple9(t8, "9");
        assertEquals(t9._1(), "1");
        assertEquals(t9._2(), "2");
        assertEquals(t9._3(), "3");
        assertEquals(t9._4(), "4");
        assertEquals(t9._5(), "5");
        assertEquals(t9._6(), "6");
        assertEquals(t9._7(), "7");
        assertEquals(t9._8(), "8");
        assertEquals(t9._9(), "9");

        Tuple10<String, String, String, String, String, String, String, String, String, String> t10 = tuple10(t9, "10");
        assertEquals(t10._1(), "1");
        assertEquals(t10._2(), "2");
        assertEquals(t10._3(), "3");
        assertEquals(t10._4(), "4");
        assertEquals(t10._5(), "5");
        assertEquals(t10._6(), "6");
        assertEquals(t10._7(), "7");
        assertEquals(t10._8(), "8");
        assertEquals(t10._9(), "9");
        assertEquals(t10._10(), "10");
    }

    @Test
    public void tupleFutureCompositionTest() throws Exception {
        CompletableFuture<Try<String>> completableFutureSuccess = new CompletableFuture<>();
        completableFutureSuccess.complete(Try.success("OK"));

        CompletableFuture<Try<String>> completableFutureFailure = new CompletableFuture<>();
        completableFutureFailure.complete(Try.failure("Error"));

        // Try tuple2 composition
        CompletableFuture<Try<Tuple2<String, String>>> futureComposition2 = composeTuple2("2", completableFutureSuccess);
        Try<Tuple2<String, String>> tuple2Try = futureComposition2.get();
        assertTrue(tuple2Try.isSuccess());
        assertEquals(tuple2Try.get()._1(), "2");
        assertEquals(tuple2Try.get()._2(), "OK");

        futureComposition2 = composeTuple2("2", completableFutureFailure);
        tuple2Try = futureComposition2.get();
        assertTrue(tuple2Try.isFailure());
        assertTrue(tuple2Try.getException() instanceof RuntimeException);

        // Try tuple3 composition
        CompletableFuture<Try<Tuple3<String, String, String>>> futureComposition3 =
                composeTuple3(tuple2("2", "3"), completableFutureSuccess);
        Try<Tuple3<String, String, String>> tuple3Try = futureComposition3.get();
        assertTrue(tuple3Try.isSuccess());
        assertEquals(tuple3Try.get()._1(), "2");
        assertEquals(tuple3Try.get()._2(), "3");
        assertEquals(tuple3Try.get()._3(), "OK");

        futureComposition3 = composeTuple3(tuple2("2", "3"), completableFutureFailure);
        tuple3Try = futureComposition3.get();
        assertTrue(tuple3Try.isFailure());
        assertTrue(tuple3Try.getException() instanceof RuntimeException);

        // Try tuple4 composition
        CompletableFuture<Try<Tuple4<String, String, String, String>>> futureComposition4 =
                composeTuple4(tuple3("2", "3", "4"), completableFutureSuccess);
        Try<Tuple4<String, String, String, String>> tuple4Try = futureComposition4.get();
        assertTrue(tuple4Try.isSuccess());
        assertEquals(tuple4Try.get()._1(), "2");
        assertEquals(tuple4Try.get()._2(), "3");
        assertEquals(tuple4Try.get()._3(), "4");
        assertEquals(tuple4Try.get()._4(), "OK");

        futureComposition4 = composeTuple4(tuple3("2", "3", "4"), completableFutureFailure);
        tuple4Try = futureComposition4.get();
        assertTrue(tuple4Try.isFailure());
        assertTrue(tuple4Try.getException() instanceof RuntimeException);

        // Try tuple5 composition
        CompletableFuture<Try<Tuple5<String, String, String, String, String>>> futureComposition5 =
                composeTuple5(tuple4("2", "3", "4", "5"), completableFutureSuccess);
        Try<Tuple5<String, String, String, String, String>> tuple5Try = futureComposition5.get();
        assertTrue(tuple5Try.isSuccess());
        assertEquals(tuple5Try.get()._1(), "2");
        assertEquals(tuple5Try.get()._2(), "3");
        assertEquals(tuple5Try.get()._3(), "4");
        assertEquals(tuple5Try.get()._4(), "5");
        assertEquals(tuple5Try.get()._5(), "OK");

        futureComposition5 = composeTuple5(tuple4("2", "3", "4", "5"), completableFutureFailure);
        tuple5Try = futureComposition5.get();
        assertTrue(tuple5Try.isFailure());
        assertTrue(tuple5Try.getException() instanceof RuntimeException);

        // Try tuple6 composition
        CompletableFuture<Try<Tuple6<String, String, String, String, String, String>>> futureComposition6 =
                composeTuple6(tuple5("2", "3", "4", "5", "6"), completableFutureSuccess);
        Try<Tuple6<String, String, String, String, String, String>> tuple6Try = futureComposition6.get();
        assertTrue(tuple6Try.isSuccess());
        assertEquals(tuple6Try.get()._1(), "2");
        assertEquals(tuple6Try.get()._2(), "3");
        assertEquals(tuple6Try.get()._3(), "4");
        assertEquals(tuple6Try.get()._4(), "5");
        assertEquals(tuple6Try.get()._5(), "6");
        assertEquals(tuple6Try.get()._6(), "OK");

        futureComposition6 = composeTuple6(tuple5("2", "3", "4", "5", "6"), completableFutureFailure);
        tuple6Try = futureComposition6.get();
        assertTrue(tuple6Try.isFailure());
        assertTrue(tuple6Try.getException() instanceof RuntimeException);

        // Try tuple7 composition
        CompletableFuture<Try<Tuple7<String, String, String, String, String, String, String>>> futureComposition7 =
                composeTuple7(tuple6("2", "3", "4", "5", "6", "7"), completableFutureSuccess);
        Try<Tuple7<String, String, String, String, String, String, String>> tuple7Try = futureComposition7.get();
        assertTrue(tuple7Try.isSuccess());
        assertEquals(tuple7Try.get()._1(), "2");
        assertEquals(tuple7Try.get()._2(), "3");
        assertEquals(tuple7Try.get()._3(), "4");
        assertEquals(tuple7Try.get()._4(), "5");
        assertEquals(tuple7Try.get()._5(), "6");
        assertEquals(tuple7Try.get()._6(), "7");
        assertEquals(tuple7Try.get()._7(), "OK");

        futureComposition7 = composeTuple7(tuple6("2", "3", "4", "5", "6", "7"), completableFutureFailure);
        tuple7Try = futureComposition7.get();
        assertTrue(tuple7Try.isFailure());
        assertTrue(tuple7Try.getException() instanceof RuntimeException);

        // Try tuple8 composition
        CompletableFuture<Try<Tuple8<String, String, String, String, String, String, String, String>>> futureComposition8 =
                composeTuple8(tuple7("2", "3", "4", "5", "6", "7", "8"), completableFutureSuccess);
        Try<Tuple8<String, String, String, String, String, String, String, String>> tuple8Try = futureComposition8.get();
        assertTrue(tuple8Try.isSuccess());
        assertEquals(tuple8Try.get()._1(), "2");
        assertEquals(tuple8Try.get()._2(), "3");
        assertEquals(tuple8Try.get()._3(), "4");
        assertEquals(tuple8Try.get()._4(), "5");
        assertEquals(tuple8Try.get()._5(), "6");
        assertEquals(tuple8Try.get()._6(), "7");
        assertEquals(tuple8Try.get()._7(), "8");
        assertEquals(tuple8Try.get()._8(), "OK");

        futureComposition8 = composeTuple8(tuple7("2", "3", "4", "5", "6", "7", "8"), completableFutureFailure);
        tuple8Try = futureComposition8.get();
        assertTrue(tuple8Try.isFailure());
        assertTrue(tuple8Try.getException() instanceof RuntimeException);

        // Try tuple9 composition
        CompletableFuture<Try<Tuple9<String, String, String, String, String, String, String, String, String>>> futureComposition9 =
                composeTuple9(tuple8("2", "3", "4", "5", "6", "7", "8", "9"), completableFutureSuccess);
        Try<Tuple9<String, String, String, String, String, String, String, String, String>> tuple9Try = futureComposition9.get();
        assertTrue(tuple9Try.isSuccess());
        assertEquals(tuple9Try.get()._1(), "2");
        assertEquals(tuple9Try.get()._2(), "3");
        assertEquals(tuple9Try.get()._3(), "4");
        assertEquals(tuple9Try.get()._4(), "5");
        assertEquals(tuple9Try.get()._5(), "6");
        assertEquals(tuple9Try.get()._6(), "7");
        assertEquals(tuple9Try.get()._7(), "8");
        assertEquals(tuple9Try.get()._8(), "9");
        assertEquals(tuple9Try.get()._9(), "OK");

        futureComposition9 = composeTuple9(tuple8("2", "3", "4", "5", "6", "7", "8", "9"), completableFutureFailure);
        tuple9Try = futureComposition9.get();
        assertTrue(tuple9Try.isFailure());
        assertTrue(tuple9Try.getException() instanceof RuntimeException);

        // Try tuple10 composition
        CompletableFuture<Try<Tuple10<String, String, String, String, String, String, String, String, String, String>>> futureComposition10 =
                composeTuple10(tuple9("2", "3", "4", "5", "6", "7", "8", "9", "10"), completableFutureSuccess);
        Try<Tuple10<String, String, String, String, String, String, String, String, String, String>> tuple10Try = futureComposition10.get();
        assertTrue(tuple10Try.isSuccess());
        assertEquals(tuple10Try.get()._1(), "2");
        assertEquals(tuple10Try.get()._2(), "3");
        assertEquals(tuple10Try.get()._3(), "4");
        assertEquals(tuple10Try.get()._4(), "5");
        assertEquals(tuple10Try.get()._5(), "6");
        assertEquals(tuple10Try.get()._6(), "7");
        assertEquals(tuple10Try.get()._7(), "8");
        assertEquals(tuple10Try.get()._8(), "9");
        assertEquals(tuple10Try.get()._9(), "10");
        assertEquals(tuple10Try.get()._10(), "OK");

        futureComposition10 = composeTuple10(tuple9("2", "3", "4", "5", "6", "7", "8", "9", "10"), completableFutureFailure);
        tuple10Try = futureComposition10.get();
        assertTrue(tuple10Try.isFailure());
        assertTrue(tuple10Try.getException() instanceof RuntimeException);
    }
}
