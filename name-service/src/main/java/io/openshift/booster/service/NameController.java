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

import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.Filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Name service controller.
 */
@RestController
public class NameController {

    private static final Logger LOG = LoggerFactory.getLogger(NameController.class);

    private static final String DEFAULT_NAME = "World";

    private final List<SseEmitter> nameEmitters = new ArrayList<>();


    /**
     * Endpoint to get a name.
     *
     * @return Host name.
     */
    @RequestMapping("/api/name")
    public ResponseEntity<String> getName(@RequestParam(name = "from", required = false) String from, @RequestParam(name = "delay", required = false) String delay) throws IOException {
        final String fromSuffix = from != null ? " from " + from : "";

        final String name = DEFAULT_NAME + fromSuffix;
        LOG.info(String.format("Returning name '%s'", name));

        // add random processing time to have a better chance for concurrent calls if we asked for it
        if (delay != null && !delay.isEmpty()) {
            int processingDelay;
            try {
                processingDelay = Integer.parseInt(delay);
            } catch (NumberFormatException e) {
                processingDelay = 150;
            }
            
            try {
                final long round = Math.round((Math.random() * 200) + processingDelay);
                Thread.sleep(round);
                LOG.info(String.format("Delayed call %s ms", round));
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

        sendMessage("GET /api/name at " + LocalTime.now() + fromSuffix);
        return new ResponseEntity<>(name, HttpStatus.OK);
    }
    
    @RequestMapping("/name-sse")
    public SseEmitter nameStateEmitter() {
        SseEmitter emitter = new SseEmitter();
        nameEmitters.add(emitter);
        emitter.onCompletion(() -> nameEmitters.remove(emitter));

        return emitter;
    }

    @Bean
    public Filter getCorsFilter() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOrigin(CorsConfiguration.ALL);
        configuration.addAllowedMethod(CorsConfiguration.ALL);
        configuration.addAllowedHeader(CorsConfiguration.ALL);
        return new CorsFilter(request -> configuration);
    }

    private void sendMessage(String message) {
        nameEmitters.forEach(emitter -> {
            try {
                emitter.send(message, MediaType.TEXT_PLAIN);
            } catch (IOException e) {
                emitter.complete();
            }
        });
    }
}
