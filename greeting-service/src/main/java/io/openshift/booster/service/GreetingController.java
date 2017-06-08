/*
 * Copyright 2016-2017 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.openshift.booster.service;

import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.WebSocketHandler;

/**
 * Greeting service controller.
 */
@RestController
public class GreetingController {

    private final NameService nameService;
    private final CircuitBreakerHandler handler = new CircuitBreakerHandler();

    public GreetingController(NameService nameService) {
        this.nameService = nameService;
    }

    /**
     * Endpoint to get a greeting. This endpoint uses a name server to get a name for the greeting.
     * <p>
     * Request to the name service is guarded with a circuit breaker. Therefore if a name service is not available or is too
     * slow to response fallback name is used.
     *
     * @return Greeting string.
     */
    @RequestMapping("/api/greeting")
    public Greeting getGreeting() throws Exception {
        String result = String.format("Hello, %s!", this.nameService.getName());
        handler.sendMessage();
        return new Greeting(result);
    }

    @Bean
    public WebSocketHandler getHandler() {
        return handler;
    }

    static class Greeting {
        private final String content;

        public Greeting(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }
}
