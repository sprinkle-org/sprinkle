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
package org.sprinkle.examples;

import org.sprinkle.Try;
import org.eclipse.jetty.client.api.Request;

import java.util.function.Function;

import static org.sprinkle.Pipeline.lift;

/**
 * Created by mboussarov on 7/7/15.
 */
public class Functions {
    public static Function<Request, Request> addHeader1(String header, String value){
        return a -> {
            return a.header(header, value);
        };
    }

    public static Request addHeader2(Request request){
        String header = "Authorization";
        String value = "Basic dXNlcm5hbWU6cGFzc3dvcmQ=";

        return request.header(header, value);
    }

    /**
     * Function that add a query param to a url
     *
     * @param url
     * @param name
     * @param value
     * @return
     */
    public static String addQueryParam(String url, String name, String value) {
        return url.contains("?") ? url + "&" + name + "=" + value : url + "?" + name + "=" + value;
    }

    /**
     * Function that build another function that takes a single argument - the value of the query parameter
     * Builds a curried function out of addQueryParameter
     *
     * @param url
     * @param name
     * @return
     */
    public static Function<String, Try<String>> getAddQueryParam(String url, String name){
        return lift(a -> {
            return addQueryParam(url, name, a);
        });
    }
}
