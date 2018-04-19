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

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Greeting service controller.
 */
@RestController
public class GreetingController {

    private final NameService nameService;
    private final List<SseEmitter> cbEmitters = new ArrayList<>();

    public GreetingController(NameService nameService) {
        this.nameService = nameService;
    }

    @RequestMapping("/api/ping")
    public Greeting getPing() throws Exception {
        return new Greeting("OK", null);
    }

    /**
     * Endpoint to get a greeting. This endpoint uses a name server to get a name for the greeting.
     * <p>
     * Request to the name service is guarded with a circuit breaker. Therefore if a name service is not available or is too
     * slow to response fallback name is used.
     *
     * @return Greeting string.
     */
    @GetMapping("/api/greeting")
    public Greeting getGreeting(@RequestParam(name = "from", required = false) String from) throws Exception {
        String result = String.format("Hello, %s!", nameService.getName(from));

        cbEmitters.forEach(emitter -> {
            try {
                emitter.send(nameService.getState(), MediaType.APPLICATION_JSON);
            } catch (Exception e) {
                emitter.complete();
                cbEmitters.remove(emitter);
            }
        });

        return new Greeting(result, from);
    }

    @RequestMapping("/cb-sse")
    public SseEmitter sbStateEmitter() {
        SseEmitter emitter = new SseEmitter();
        cbEmitters.add(emitter);
        emitter.onCompletion(() -> cbEmitters.remove(emitter));

        return emitter;
    }

    static class Greeting {
        private final String content;
        private final String from;

        public Greeting(String content, String from) {
            this.content = content;
            this.from = from;
        }

        public String getContent() {
            return content;
        }

        public String getFrom() {
            return from;
        }
    }
}
