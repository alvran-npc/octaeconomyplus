#!/usr/bin/env sh
# OctaEconomy Maven bootstrap wrapper. Requires JDK 21, curl or wget, and unzip.
set -eu

MAVEN_VERSION="3.9.9"
MAVEN_HOME_DIR="${MAVEN_USER_HOME:-${HOME:-.}/.m2}/wrapper/dists/apache-maven-${MAVEN_VERSION}"
MAVEN_HOME="${MAVEN_HOME_DIR}/apache-maven-${MAVEN_VERSION}"
MAVEN_BIN="${MAVEN_HOME}/bin/mvn"
DIST_URL="https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.zip"
ZIP_FILE="${MAVEN_HOME_DIR}/apache-maven-${MAVEN_VERSION}-bin.zip"

if [ ! -x "${MAVEN_BIN}" ]; then
    mkdir -p "${MAVEN_HOME_DIR}"
    echo "Downloading Apache Maven ${MAVEN_VERSION}..." >&2
    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --silent --show-error "${DIST_URL}" --output "${ZIP_FILE}"
    elif command -v wget >/dev/null 2>&1; then
        wget -q "${DIST_URL}" -O "${ZIP_FILE}"
    else
        echo "ERROR: Install curl or wget to bootstrap Maven." >&2
        exit 1
    fi
    if ! command -v unzip >/dev/null 2>&1; then
        echo "ERROR: Install unzip to extract Maven." >&2
        exit 1
    fi
    unzip -q -o "${ZIP_FILE}" -d "${MAVEN_HOME_DIR}"
    rm -f "${ZIP_FILE}"
fi

exec "${MAVEN_BIN}" -f "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/pom.xml" "$@"
