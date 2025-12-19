#!/bin/bash

echo "================================================================"
echo "  Deep Agents Demo"
echo "================================================================"
echo ""

mvn test-compile exec:java \
    -Dexec.mainClass="org.bsc.langgraph4j.deepagents.DeepagentsDemoApplication" \
    -Dexec.classpathScope=test \
    -Dspring.profiles.active=deepagents

