#!/bin/bash

echo "================================================================"
echo "  Source Code Search Agent"
echo "================================================================"
echo ""

# Check if SRC_TARGET is already set
if [ -z "$SRC_TARGET" ]; then
    echo "  검색 대상 디렉토리를 입력하세요."
    echo "  (Enter를 누르면 기본값 'src/main/java' 사용)"
    echo ""
    read -p "  경로: " input_path
    
    if [ -n "$input_path" ]; then
        export SRC_TARGET="$input_path"
    fi
fi

echo ""
echo "  검색 대상: ${SRC_TARGET:-src/main/java}"
echo ""
echo "================================================================"
echo ""

mvn test-compile exec:java \
    -Dexec.mainClass="org.bsc.langgraph4j.deepagents.SourceCodeSearchAgentApplication" \
    -Dexec.classpathScope=test

