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

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Service invoking name-service via REST
 */
@Service
public class NameService {

    private static final String nameHost = System.getProperty("name.host", "http://spring-boot-istio-circuit-breaker-name:8080");
    private final RestTemplate restTemplate;
    private final AtomicBoolean isCBOpen = new AtomicBoolean(false);

    public NameService() {
        this.restTemplate = new RestTemplate();
    }

    public String getName(String from, String delay) {
        try {
            if(delay == null) {
                return restTemplate.getForObject(nameHost + "/api/name?from={from}", String.class, from);
            }
            return restTemplate.getForObject(nameHost + "/api/name?from={from}&delay={delay}", String.class, from, delay);
        } catch (RestClientException e) {
            if (e instanceof HttpServerErrorException) {
                HttpServerErrorException serverError = (HttpServerErrorException) e;
                // check if we get a 503 error, which is what Istio will send when its CB is open
                if (HttpStatus.SERVICE_UNAVAILABLE.equals(serverError.getStatusCode())) {
                    isCBOpen.set(true);
                    return getFallbackName();
                }
            }

            throw e;
        }
    }

    private String getFallbackName() {
        return "Fallback";
    }

    CircuitBreakerState getState() throws Exception {
        return isCBOpen.get() ? CircuitBreakerState.OPEN : CircuitBreakerState.CLOSED;
    }
}
