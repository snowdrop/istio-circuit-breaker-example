#!/usr/bin/env bash

set -e

SCRIPT_ABSOLUTE_DIR="$(cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd)"
PROJECT_ABSOLUTE_DIR=$(dirname ${SCRIPT_ABSOLUTE_DIR})

pushd ${PROJECT_ABSOLUTE_DIR} > /dev/null

MAVEN_OPTS="-Xmx128m"  ./mvnw clean verify -Popenshift,openshift-it "$@"

popd > /dev/null
