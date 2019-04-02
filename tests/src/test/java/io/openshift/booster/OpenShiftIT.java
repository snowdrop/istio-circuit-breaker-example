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
import java.util.Collections;
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

        String caller = "Dwarf miner"; // use some non-common word for testing

        Response response = greetingResponse(caller);
        assertThat(response.statusCode()).isEqualTo(200);

        String responseText = response.jsonPath().get("content");
        assertThat(responseText).isEqualTo("Hello, World from " + caller + "!");
    }

    @Test
    public void testOneWorker() throws IOException, InterruptedException {
        waitUntilApplicationIsReady();

        ResponsesCount responsesCount = deployAndMeasure(
                    Collections.singletonList("restrictive_destination_rule.yml"),
                0, 1);
        /*
         * With one worker, there should be not fallback responses, but it is possible for some to occur.
         */
        assertThat(responsesCount.getFallbackResponses())
                .isLessThan(responsesCount.getTotalResponses() / 20);
    }

    @Test
    public void testCircuitBreakerOpen() throws IOException, InterruptedException {
        waitUntilApplicationIsReady();

        ResponsesCount responsesCount = deployAndMeasure(
                Collections.singletonList("restrictive_destination_rule.yml"),
                0, QUERY_ASKERS_CNT);
        /*
         * We cannot presume that there will be any specific number of fallback responses.
         * On high performance clusters the circuit breaker may not trip often and it could cause test to fail
         *      even if there is no real failure.
         * But there should be at least some fallback responses, also there should be a lot of passed responses.
         */

        // at least 10% of responses are fallbacks
        assertThat(((responsesCount.getFallbackResponses() * 100)
                / responsesCount.getTotalResponses())).isGreaterThan(10);
    }

    @Test
    public void testSimulatedLoad() throws IOException, InterruptedException {
        waitUntilApplicationIsReady();

        ResponsesCount responsesCount = deployAndMeasure(
                Collections.singletonList("restrictive_destination_rule.yml"),
                10, QUERY_ASKERS_CNT);

        // Assert that there are enough fallback responses in the responses
        assertThat(responsesCount.getPassedResponses())
                .isLessThanOrEqualTo(responsesCount.getFallbackResponses() * FALLBACK_RESPONSE_RATIO);
    }

    /**
     * Wait for application to become ready, deploy istio rules and measure it's effect on the application
     *
     * @param istioResources List of desired istio resources to deploy
     * @param responseDelay artificiant delay inserted to each response
     * @param workersCount number of workers which will query the application
     * @return count of passed and failed responses
     */
    private ResponsesCount deployAndMeasure(List<String> istioResources,
                                            int responseDelay,
                                            int workersCount) throws InterruptedException, IOException {
        waitUntilApplicationIsReady();

        // deploy desired istio rules
        List <me.snowdrop.istio.api.IstioResource> resource = new ArrayList<>();
        for (String istioResource: istioResources){
            resource.addAll(deployIstioResource(istioResource));
        }
        Thread.sleep(TimeUnit.SECONDS.toMillis(10)); // wait for rules to take effect

        // measure effect on application
        ResponsesCount responsesCount = measureResponses(responseDelay, workersCount);

        istioAssistant.undeployIstioResources(resource);
        return responsesCount;
    }

    /**
     * Create multiple threads and measure responses from the greeting service
     * @param delay Number of milliseconds of artificial delay of one response
     * @param workersCount Number of workers to be created
     * @return Number of passed and fallback responses
     */
    private ResponsesCount measureResponses(int delay, int workersCount) throws InterruptedException {
        // create threads that will make the calls in parallel
        List<GreetingWorker> workerArray = new ArrayList<>();
        for (int i=0 ; i < workersCount ; i++){
            GreetingWorker worker = new GreetingWorker(Integer.toString(i),ingressGatewayURL,QUERY_ASKERS_REQUEST_CNT);
            worker.setRequestDelay(delay);
            worker.start();
            workerArray.add(worker);
        }

        ResponsesCount totalResponses = new ResponsesCount();
        // wait for threats to end and take their results
        for (GreetingWorker greetingWorker : workerArray){
            greetingWorker.join();
            totalResponses.addResponses(greetingWorker.getResponsesCount());
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
