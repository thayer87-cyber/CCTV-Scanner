#!/bin/bash
# Build script for CCTV Scanner with Java 21 LTS
export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.10-ms
export ANDROID_HOME=/opt/android-sdk

"$@"
