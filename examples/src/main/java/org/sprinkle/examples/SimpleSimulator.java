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

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalTime;
import java.util.HashMap;

/**
 * Created by mboussarov on 11/11/15.
 */
public class SimpleSimulator {

    public static void startAllServers() throws Exception {
        Server server = new Server();
        SimulatorHandler handler = new SimulatorHandler();
        server.setHandler(handler);

        addServer(server, handler, 9901, "/v1", "Server on port 9901");
        addServer(server, handler, 9902, "/v1", "Server on port 9902");
        addServer(server, handler, 9903, "/v1", "Server on port 9903");
        addServer(server, handler, 9904, "/v1", "Server on port 9904");
        addServer(server, handler, 9905, "/v1", "Server on port 9905");
        addServer(server, handler, 9906, "/v1", "Server on port 9906");
        addServer(server, handler, 9907, "/v1", "Server on port 9907");

        server.start();
    }

    public static void addServer(Server server, SimulatorHandler handler, int port,
                                 String path, String message) throws Exception {
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        connector.setIdleTimeout(30000);

        server.addConnector(connector);

        handler.addRoute(port, path, message);
    }

    // Handler definition
    public static class SimulatorHandler extends AbstractHandler {
        HashMap<String, String> routes = new HashMap<>();
        final String notFoundMessage = "{\"status\": 404, \"message\": \"Not found\"}";
        final String responseTemplate = "{\"status\": 200, \"message\": \"%s\", \"time\": \"%s\"}";

        public SimulatorHandler(){}

        public void addRoute(int port, String path, String message){
            routes.put(String.format("%s:%s", port, path), message);
                    //String.format("{\"status\": 200, \"message\": \"%s\", \"time\": \"%s\"}", message, LocalTime.now()));
        }

        public void handle(String target,
                           Request baseRequest,
                           HttpServletRequest request,
                           HttpServletResponse response) throws IOException, ServletException {

            String key = String.format("%s:%s", baseRequest.getServerPort(), baseRequest.getPathInfo());
            String message = routes.get(key);

            response.setContentType("application/json");

            if(message != null){
                String responseMessage = String.format("{\"status\": 200, \"message\": \"%s\", \"time\": \"%s\"}", message, LocalTime.now());
                response.setStatus(HttpServletResponse.SC_OK);
                PrintWriter out = response.getWriter();
                out.println(responseMessage);
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                PrintWriter out = response.getWriter();
                out.println(notFoundMessage);
            }

            baseRequest.setHandled(true);
        }
    }
}
