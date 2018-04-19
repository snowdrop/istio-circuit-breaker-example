# Istio Circuit Breaker mission

## Purpose

Showcase Istio's Circuit Breaker via a (minimally) instrumented Spring Boot application

## Prerequisites

- OpenShift 3.9 cluster
- Istio 0.7.1 (without auth enabled) installed on the aforementioned cluster.
- Enable automatic sidecar injection for Istio
  * See [this](https://istio.io/docs/setup/kubernetes/sidecar-injection.html) for details
  * Additionally, **if you're not using the `istiooc` command (see below)**, you will need to manually change the `policy` field
  of the `istio-inject` ConfigMap of the `istio-system` namespace from `enabled` to `disabled` and restart the
  `istio-sidecar-injector` pod afterwards.
- Login to the cluster with the admin user

Note that the recommended way to start an OpenShift cluster properly setup with Istio is to use the `istiooc` command,
available from [this project](https://github.com/openshift-istio/origin/releases/), using:

```bash
istiooc cluster up --istio=true
```

## Environment preparation

```bash
    oc new-project <whatever valid project name you want>
    oc label namespace $(oc project -q) istio-injection=enabled
```

## Deploy the project

### With Fabric8 Maven Plugin
```bash
    mvn clean fabric8:deploy -Popenshift
```

### With OpenShift S2I Build
```bash
find . | grep openshiftio | grep application | xargs -n 1 oc apply -f

oc new-app --template=spring-boot-circuit-breaker-greeting -p SOURCE_REPOSITORY_URL=https://github.com/snowdrop/spring-boot-circuit-breaker-booster  -p SOURCE_REPOSITORY_REF=with-sse -p SOURCE_REPOSITORY_DIR=name-service
oc new-app --template=spring-boot-circuit-breaker-name -p SOURCE_REPOSITORY_URL=https://github.com/snowdrop/spring-boot-circuit-breaker-booster  -p SOURCE_REPOSITORY_REF=with-sse -p SOURCE_REPOSITORY_DIR=name-service    
```

## Interact with the application

* Open the following URL in your browser:
```bash
oc get route spring-boot-circuit-breaker-greeting -o jsonpath='http://{.spec.host}{"\n"}'
```

### Without Istio

* Click on the "Start" button to issue 10 concurrent requests to the name service.
* Click on the "Stop" button to stop the requests
* You can change the number of concurrent requests between 1 and 20.
* The Circuit Breaker state should be closed.

### With Istio

* Apply the `RouteRule`:
```bash
oc create -f istio/route_rule.yml -n $(oc project -q)
```
* At this point, nothing should have changed in the application behavior.
* Now apply the `DestinationPolicy` that activates Istio's Circuit Breaker on the name service:
```bash
oc create -f istio/destination_policy.yml -n $(oc project -q)
```
* Since the Circuit Breaker is configured to only allow 1 concurrent connection and by default we are sending 10 to the name
service, we should now see the Circuit Breaker tripping open.
