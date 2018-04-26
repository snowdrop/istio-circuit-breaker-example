# Istio Circuit Breaker mission

## Purpose

Showcase Istio's Circuit Breaker via a (minimally) instrumented Spring Boot application

## Prerequisites

- OpenShift 3.9 cluster
- Istio 0.7.1 installed on the aforementioned cluster.
- Enable automatic sidecar injection for Istio
  * See [this](https://istio.io/docs/setup/kubernetes/sidecar-injection.html) for details
  * Additionally, **if you're not using the `istiooc` command (see below)**, you will need to manually change the `policy` field
  of the `istio-inject` ConfigMap of the `istio-system` namespace from `enabled` to `disabled` and restart the
  `istio-sidecar-injector` pod afterwards.
  * The `istio-sidecar-injector` `MutatingWebhookConfiguration` should not limit the injection to properly labeled namespaces. 
- Login to the cluster with the admin user

Note that the recommended way to start an OpenShift cluster properly setup with Istio is to use the `istiooc` command,
available from [this project](https://github.com/openshift-istio/origin/releases/), using:

```bash
istiooc cluster up --istio=true
```

## Environment preparation

```bash
    oc new-project <whatever valid project name you want>
```

## Deploy the project

### With Fabric8 Maven Plugin
```bash
    mvn clean fabric8:deploy -Popenshift
```

### With OpenShift S2I Build
```bash
find . | grep openshiftio | grep application | xargs -n 1 oc apply -f

oc new-app --template=spring-boot-istio-circuit-breaker-greeting -p SOURCE_REPOSITORY_URL=https://github.com/snowdrop/spring-boot-istio-circuit-breaker-booster  -p SOURCE_REPOSITORY_REF=master -p SOURCE_REPOSITORY_DIR=name-service
oc new-app --template=spring-boot-istio-circuit-breaker-name -p SOURCE_REPOSITORY_URL=https://github.com/snowdrop/spring-boot-istio-circuit-breaker-booster  -p SOURCE_REPOSITORY_REF=master -p SOURCE_REPOSITORY_DIR=name-service    
```

## Interact with the application

* Open the following URL in your browser:
```bash
oc get route spring-boot-istio-circuit-breaker-greeting -o jsonpath='http://{.spec.host}{"\n"}'
```

### Without Istio

* Click on the "Start" button to issue 10 concurrent requests to the name service.
* Click on the "Stop" button to stop the requests
* You can change the number of concurrent requests between 1 and 20.
* All calls go through as expected.

### With Istio

#### Initial setup
* Apply the `RouteRule` which is required for the `DestinationPolicy` to take effect:
```bash
oc create -f istio/route_rule.yml -n $(oc project -q)
```
* At this point, nothing should have changed in the application behavior.

#### Initial Circuit Breaker configuration
* Now apply the initial `DestinationPolicy` that activates Istio's Circuit Breaker on the name service, configuring it to allow 
a maximum of 100 concurrent connections. Since we only make up to 20 concurrent connections, the circuit breaker should not trip.
```bash
oc create -f istio/initial_destination_policy.yml -n $(oc project -q)
```

#### Restrictive Circuit Breaker configuration
* Now apply a more restrictive `DestinationPolicy`, after having remove the initial one:
```bash
oc delete -f istio/initial_destination_policy.yml
oc create -f istio/restrictive_destination_policy.yml -n $(oc project -q)
```

* Since the Circuit Breaker is now configured to only allow 1 concurrent connection and by default we are sending 10 to the name
service, we should now see the Circuit Breaker tripping open. However, experimentally, we observe that this does not happen in 
a clear-cut fashion: the circuit is not always open. In fact, depending on how fast the server on which the application is
running, the circuit might not break open at all. The reason for this is that is the name service doesn't do much and thus 
responds quite fast. This, in turn, leads to not having much concurrency at all.

#### Optional: fault injection 

* We could try to increase contention on the name service using Istio's fault injection behavior by applying a 1-second 
delay to 50% of the calls to the name service. Delete the original `RouteRule` and apply a new one to do that:
```bash
oc delete -f istio/route_rule.yml
oc create -f istio/route_rule_with_delay.yml -n $(oc project -q)
```

* Interacting with the application, we note, though, that this doesn't seem to change how often the circuit breaks open. This is
due to the fact that the injected delay actually occurs between the services. So, in essence, this only time shifts the requests,
only increasing concurrency marginally (due to the fact that only 50% of the requests are delayed). This still doesn't let us
observe the circuit breaking open properly. For more comfort, re-activate the original `RouteRule` since we don't need
the artificial 1 second delay:
```bash
oc delete -f istio/route_rule_with_delay.yml
oc create -f istio/route_rule.yml -n $(oc project -q)
```

#### Simulate load on the name service

* We need to increase contention on the name service in order to have enough concurrent connections to trip open the circuit
breaker. We can accomplish this by simulating load on the name service by asking it to introduce a random processing time. This
is accomplished by stopping the requests (if that wasn't already the case), checking the "Simulate load" checkbox and restarting
the requests. You should now see the circuit breaking open.