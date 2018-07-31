package io.openshift.booster;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import java.net.URL;

public class GreetingAsker extends Thread {
    private URL istioIngressGateway;
    private int requestCount;
    private int passedResponses;
    private int fallbackResponses;

    private int requestDelay;

    public GreetingAsker(String threadName, URL istioIngressGateway, int requestCount) {
        super(threadName);
        this.istioIngressGateway=istioIngressGateway;
        passedResponses=0;
        fallbackResponses=0;
        requestDelay=0;
        this.requestCount=requestCount;
    }

    @Override
    public void run() {
        Response response;
        for (int i=0; i < requestCount ; i++){
            response = greetingResponse(getName());

            if (response.body().asString().contains("Fallback")){
                fallbackResponses++;
            } else {
                passedResponses++;
            }
        }
    }

    private Response greetingResponse(String caller) {
        return RestAssured
                .given()
                .baseUri(istioIngressGateway + "breaker/greeting")
                .param("from",caller)
                .param("delay",requestDelay)
                .get("/api/greeting");
    }

    public void setRequestDelay(int requestDelay) {
        this.requestDelay = requestDelay;
    }

    public ResponsesCount getResponsesCount(){
        return new ResponsesCount(passedResponses, fallbackResponses);
    }
}
