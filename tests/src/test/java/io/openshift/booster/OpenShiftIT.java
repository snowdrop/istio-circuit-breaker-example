package io.openshift.booster;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.arquillian.cube.istio.api.IstioResource;
import org.arquillian.cube.istio.impl.IstioAssistant;
import org.arquillian.cube.openshift.impl.enricher.RouteURL;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.runners.MethodSorters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * @author Martin Ocenas
 */
@RunWith(Arquillian.class)
@IstioResource("classpath:gateway.yml")
// this is a stop gap solution until deletion of the custom resources works correctly
// see https://github.com/snowdrop/istio-java-api/issues/31
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OpenShiftIT {
    private static final String ISTIO_NAMESPACE = "istio-system";
    private static final String ISTIO_INGRESS_GATEWAY_NAME = "istio-ingressgateway";

    private static final int QUERY_ASKERS_CNT = 50;
    private static final int QUERY_ASKERS_REQUEST_CNT = 100;
    private static final int FALLBACK_RESPONSE_RATIO = 4; // how many passed responses should at most be passed to one fallback response

    @RouteURL(value = ISTIO_INGRESS_GATEWAY_NAME, namespace = ISTIO_NAMESPACE)
    private URL ingressGatewayURL;

    @ArquillianResource
    private IstioAssistant istioAssistant;

    @Test
    public void testBasicGreeting() {
        waitUntilApplicationIsReady();

        String caller = "test";

        Response response = greetingResponse(caller);
        assertThat(response.statusCode()).isEqualTo(200);

        String reponseText = response.jsonPath().get("content");
        assertThat(reponseText).isEqualTo("Hello, World from " + caller + "!");
    }

    @Test
    public void testCircuitBreakerOpen() throws IOException, InterruptedException {
        waitUntilApplicationIsReady();
        List <me.snowdrop.istio.api.IstioResource> resource = deployIstioResource("restrictive_destination_rule.yml");

        Thread.sleep(TimeUnit.SECONDS.toMillis(10)); // wait for rule to take effect

        ResponsesCount responsesCount = measureResponses(0);

        // TODO bring back when resource removal actually works
//        istioAssistant.undeployIstioResources(resource);

        // Assert that there are fallback responses
        /*
         * We cannot presume that there will be any specific number of fallback responses.
         * On high performance clusters the circuit breaker may not trip often and it could cause test to fail
         *      even if there are no real failure.
         */
        assertThat(responsesCount.getFallbackResponses()).isGreaterThan(0);
    }

    @Test
    public void testSimulatedLoad() throws IOException, InterruptedException {
        waitUntilApplicationIsReady();

        // TODO bring back when resource removal actually works
//        List <me.snowdrop.istio.api.model.IstioResource> resource = deployIstioResource("restrictive_destination_rule.yml");
//        Thread.sleep(TimeUnit.SECONDS.toMillis(10)); // wait for rule to take effect

        ResponsesCount responsesCount = measureResponses(150);

        // TODO bring back when resource removal actually works
//        istioAssistant.undeployIstioResources(resource);

        // Assert that there are enough fallback responses in the responses
        assertThat(responsesCount.getPassedResponses())
                .isLessThanOrEqualTo(responsesCount.getFallbackResponses() * FALLBACK_RESPONSE_RATIO);
    }

    /**
     * Create multiple threads and measure responses from the greeting service
     * @param delay Number of milliseconds of artificial delay of one response
     * @return Number of passed and fallback responses
     */
    private ResponsesCount measureResponses(int delay) throws InterruptedException {
        // create threads that will make the calls in parallel
        List<GreetingAsker> askerArray = new ArrayList<>();
        for (int i=0 ; i < QUERY_ASKERS_CNT ; i++){
            GreetingAsker asker = new GreetingAsker(Integer.toString(i),ingressGatewayURL,QUERY_ASKERS_REQUEST_CNT);
            asker.setRequestDelay(delay);
            asker.start();
            askerArray.add(asker);
        }

        ResponsesCount totalResponses = new ResponsesCount();
        // wait for threats to end and take their results
        for (GreetingAsker greetingAsker : askerArray){
            greetingAsker.join();
            totalResponses.addResponses(greetingAsker.getResponsesCount());
        }
        return totalResponses;
    }

    private Response greetingResponse(String caller) {
        return RestAssured
                .given()
                .baseUri(ingressGatewayURL + "breaker/greeting")
                .param("from",caller)
                .get("/api/greeting");
    }

    private void waitUntilApplicationIsReady() {
        await()
                .pollInterval(1, TimeUnit.SECONDS)
                .atMost(1, TimeUnit.MINUTES)
                .untilAsserted(() ->
                        RestAssured
                                .given()
                                .baseUri(ingressGatewayURL.toString())
                                .when()
                                .get("/breaker/greeting")
                                .then()
                                .statusCode(200)
                );
    }

    private List<me.snowdrop.istio.api.IstioResource> deployIstioResource(String istioResource) throws IOException {
        return istioAssistant.deployIstioResources(
                Files.newInputStream(Paths.get("../istio/" + istioResource)));
    }
}
