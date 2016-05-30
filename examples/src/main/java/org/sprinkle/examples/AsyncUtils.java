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
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandler;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.Consumer;

/**
 * Created by mboussarov on 11/15/15.
 */
public class AsyncUtils {
    // Construct a context handler
    public static ContextHandler contextHandler(String path, Handler handler){
        ContextHandler context = new ContextHandler(path);
        context.setHandler(handler);
        return context;
    }

    // Function that deals with returning the async response
    public static <T> Consumer<Try<T>> getAsyncResponseFunction(AsyncContext asyncContext, String contentType){
        String errorTemplate = "{\"status\": %s, \"message\": \"%s\"}";

        return a -> {
            HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();

            try {
                if (a.isSuccess()) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.setContentType(contentType);

                    PrintWriter out = response.getWriter();
                    out.println(a.get());
                } else {
                    // Failure
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.setContentType("application/json");

                    PrintWriter out = response.getWriter();
                    out.println(String.format(errorTemplate, 500, a.getException().getMessage()));
                }

                response.flushBuffer();
                asyncContext.complete();
            }catch (IOException ex){
                // Something went terribly wrong
                System.out.println("Error in the response: " + ex.getMessage());
                asyncContext.complete();
            }
        };
    }

    public static <T> Consumer<T> getAsyncResponseFunctionPar(AsyncContext asyncContext, String contentType){
        return a -> {
            HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();

            try {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType(contentType);

                PrintWriter out = response.getWriter();
                out.println(a.toString());

                response.flushBuffer();
                asyncContext.complete();
            }catch (IOException ex){
                // Something went terribly wrong
                System.out.println("Error in the response: " + ex.getMessage());
                asyncContext.complete();
            }
        };
    }

    // Default async handler
    public abstract static class AsyncHandler extends AbstractHandler {
        protected AsyncListener asyncListener;

        public AsyncHandler(){
            asyncListener = new DefaultAsyncListener();
        }

        public AsyncHandler(AsyncListener asyncListener){
            this.asyncListener = asyncListener;
        }

        public HttpClient getHttpClient() throws Exception {
            HttpClient httpClient = new HttpClient();

            httpClient.setMaxRequestsQueuedPerDestination(10000);
            httpClient.setMaxConnectionsPerDestination(10000);
            httpClient.setConnectTimeout(1000);
            httpClient.setIdleTimeout(60000);

            httpClient.start();

            return httpClient;
        }

        @Override
        public void handle(String target,
                           Request baseRequest,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException, ServletException {
            final AsyncContext asyncContext = request.startAsync();
            asyncContext.addListener(asyncListener);
            handle(asyncContext);
        }

        public abstract void handle(AsyncContext asyncContext);
    }

    // Default async listener
    public static class DefaultAsyncListener implements AsyncListener {
        public void onComplete(AsyncEvent event) throws IOException {
            HttpServletResponse response = (HttpServletResponse)event.getSuppliedResponse();
            response.flushBuffer();
        }

        public void onTimeout(AsyncEvent event) throws IOException {
            HttpServletResponse response = (HttpServletResponse)event.getSuppliedResponse();
            response.flushBuffer();
            event.getAsyncContext().complete();
        }

        public void onError(AsyncEvent event) throws IOException {
            HttpServletResponse response = (HttpServletResponse) event.getSuppliedResponse();
            response.sendError(500);
        }

        public void onStartAsync(AsyncEvent event) throws IOException {}
    }

    public static class PipelineContext {
        private String conditionParameter;

        private String urlOne;
        private String urlTwo;
        private String urlThree;

        private String responseOne;
        private String responseTwo;
        private String responseThree;

        public String getUrlOne() {
            return urlOne;
        }

        public void setUrlOne(String urlOne) {
            this.urlOne = urlOne;
        }

        public String getUrlTwo() {
            return urlTwo;
        }

        public void setUrlTwo(String urlTwo) {
            this.urlTwo = urlTwo;
        }

        public String getUrlThree() {
            return urlThree;
        }

        public void setUrlThree(String urlThree) {
            this.urlThree = urlThree;
        }

        public String getResponseOne() {
            return responseOne;
        }

        public void setResponseOne(String responseOne) {
            this.responseOne = responseOne;
        }

        public String getResponseTwo() {
            return responseTwo;
        }

        public void setResponseTwo(String responseTwo) {
            this.responseTwo = responseTwo;
        }

        public String getResponseThree() {
            return responseThree;
        }

        public void setResponseThree(String responseThree) {
            this.responseThree = responseThree;
        }

        public String getConditionParameter() {
            return conditionParameter;
        }

        public void setConditionParameter(String conditionParameter) {
            this.conditionParameter = conditionParameter;
        }

        @Override
        public String toString() {
            return "{" +
                    "\"urlOne\"=\"" + urlOne + '"' +
                    ", \"urlTwo\"=\"" + urlTwo + '"' +
                    ", \"urlThree\"=\"" + urlThree + '"' +
                    ", \"responseOne=\"" + responseOne + '"' +
                    ", \"responseTwo\"=\"" + responseTwo + '"' +
                    ", \"responseThree=\"" + responseThree + '"' +
                    '}';
        }
    }

    // A simple class used to demonstrate the normalization pattern
    public static class ValueContainer {
        private String value;

        public ValueContainer(String value){
            this.value = value;
        }
        public String getValue() {
            return value;
        }
    }
}
