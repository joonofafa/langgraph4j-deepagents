#!/bin/bash

echo "================================================================"
echo "  Source Code Search Agent"
echo "================================================================"
echo ""
echo "  SRC_TARGET 환경변수로 검색 대상 디렉토리를 지정할 수 있습니다."
echo "  현재 설정: ${SRC_TARGET:-미설정}"
echo "  (미설정 시 기본값: src/main/java)"
echo ""
echo "================================================================"
echo ""

mvn test-compile exec:java \
    -Dexec.mainClass="org.bsc.langgraph4j.deepagents.SourceCodeSearchAgentApplication" \
    -Dexec.classpathScope=test

