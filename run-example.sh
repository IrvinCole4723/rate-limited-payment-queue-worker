#!/usr/bin/env sh
set -eu
rm -rf .classes
mkdir .classes
javac -d .classes $(find src/main/java src/test/java -name '*.java')
java -cp .classes dev.infrai.fintech.PaymentQueueWorkerTest
