#!/usr/bin/env bash

set -e

SCRIPT_ABSOLUTE_DIR="$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)"
PROJECT_ABSOLUTE_DIR=$(dirname ${SCRIPT_ABSOLUTE_DIR})

pushd ${PROJECT_ABSOLUTE_DIR} > /dev/null

MAVEN_OPTS="-Xmx128m" ./mvnw compile fabric8:deploy -Popenshift,openshift-it -DskipTests --projects '!tests' "$@"

echo "Apps Deployed"
echo "--------------"
oc get pods

MAVEN_OPTS="-Xmx128m" ./mvnw verify -Popenshift,openshift-it --projects 'tests' -Denv.init.enabled=false "$@"

popd > /dev/null
