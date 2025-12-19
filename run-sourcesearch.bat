@echo off
chcp 65001 > nul
echo ================================================================
echo   Source Code Search Agent
echo ================================================================
echo.

REM Check if SRC_TARGET is already set
if "%SRC_TARGET%"=="" (
    echo   검색 대상 디렉토리를 입력하세요.
    echo   ^(Enter를 누르면 기본값 'src/main/java' 사용^)
    echo.
    set /p input_path="  경로: "
    
    if not "!input_path!"=="" (
        set SRC_TARGET=!input_path!
    )
)

setlocal enabledelayedexpansion
echo.
if "%SRC_TARGET%"=="" (
    echo   검색 대상: src/main/java
) else (
    echo   검색 대상: %SRC_TARGET%
)
echo.
echo ================================================================
echo.
endlocal

call mvn test-compile exec:java -Dexec.mainClass="org.bsc.langgraph4j.deepagents.SourceCodeSearchAgentApplication" -Dexec.classpathScope=test

