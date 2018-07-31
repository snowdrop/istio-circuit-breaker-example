package io.openshift.booster;

public class ResponsesCount {
    private int passedResponses;
    private int fallbackResponses;

    public ResponsesCount(int passedResponses, int fallbackResponses) {
        this.passedResponses = passedResponses;
        this.fallbackResponses = fallbackResponses;
    }

    public ResponsesCount() {
        passedResponses=0;
        fallbackResponses=0;
    }

    public void addResponses(ResponsesCount responsesCount) {
        this.passedResponses += responsesCount.getPassedResponses();
        this.fallbackResponses += responsesCount.getFallbackResponses();
    }

    public int getPassedResponses() {
        return passedResponses;
    }

    public int getFallbackResponses() {
        return fallbackResponses;
    }
}
