#!/bin/bash

# LangGraph4j Deep Agents - Program Launcher

show_menu() {
    clear
    echo "================================================================"
    echo "  LangGraph4j Deep Agents - Program Launcher"
    echo "================================================================"
    echo ""
    echo "  [1] Deep Agents Demo"
    echo "      - Deep Agents 데모 프로그램 실행"
    echo ""
    echo "  [2] Source Code Search Agent"
    echo "      - 소스 코드 검색 에이전트 실행"
    echo "      - 지정된 디렉토리의 소스 코드를 검색하고 질문에 답변"
    echo ""
    echo "  [3] Build Only (컴파일)"
    echo "      - 프로젝트를 컴파일만 수행"
    echo ""
    echo "  [4] Clean Build (전체 빌드)"
    echo "      - 클린 빌드 수행 (clean + compile + test-compile)"
    echo ""
    echo "  [0] Exit"
    echo "      - 프로그램 종료"
    echo ""
    echo "================================================================"
    echo ""
}

run_deepagents() {
    clear
    echo "================================================================"
    echo "  Deep Agents Demo 실행 중..."
    echo "================================================================"
    echo ""
    mvn test-compile exec:java \
        -Dexec.mainClass="org.bsc.langgraph4j.deepagents.DeepagentsDemoApplication" \
        -Dexec.classpathScope=test \
        -Dspring.profiles.active=openai,deepagents
    echo ""
    echo "================================================================"
    echo "  프로그램이 종료되었습니다."
    echo "================================================================"
    read -p "계속하려면 Enter를 누르세요..."
}

run_sourcesearch() {
    clear
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
    echo ""
    echo "================================================================"
    echo "  프로그램이 종료되었습니다."
    echo "================================================================"
    read -p "계속하려면 Enter를 누르세요..."
}

build_only() {
    clear
    echo "================================================================"
    echo "  프로젝트 컴파일 중..."
    echo "================================================================"
    echo ""
    mvn compile test-compile
    echo ""
    echo "================================================================"
    echo "  컴파일이 완료되었습니다."
    echo "================================================================"
    read -p "계속하려면 Enter를 누르세요..."
}

clean_build() {
    clear
    echo "================================================================"
    echo "  클린 빌드 수행 중..."
    echo "================================================================"
    echo ""
    mvn clean compile test-compile
    echo ""
    echo "================================================================"
    echo "  클린 빌드가 완료되었습니다."
    echo "================================================================"
    read -p "계속하려면 Enter를 누르세요..."
}

# Main loop
while true; do
    show_menu
    read -p "선택하세요 (0-4): " choice
    
    case $choice in
        1)
            run_deepagents
            ;;
        2)
            run_sourcesearch
            ;;
        3)
            build_only
            ;;
        4)
            clean_build
            ;;
        0)
            echo ""
            echo "프로그램을 종료합니다."
            exit 0
            ;;
        *)
            echo ""
            echo "잘못된 선택입니다. 다시 시도해 주세요."
            sleep 2
            ;;
    esac
done

