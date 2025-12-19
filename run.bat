@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

title LangGraph4j Deep Agents

:menu
cls
echo ================================================================
echo   LangGraph4j Deep Agents - Program Launcher
echo ================================================================
echo.
echo   [1] Deep Agents Demo
echo       - Deep Agents 데모 프로그램 실행
echo.
echo   [2] Source Code Search Agent
echo       - 소스 코드 검색 에이전트 실행
echo       - 지정된 디렉토리의 소스 코드를 검색하고 질문에 답변
echo.
echo   [3] Build Only (컴파일)
echo       - 프로젝트를 컴파일만 수행
echo.
echo   [4] Clean Build (전체 빌드)
echo       - 클린 빌드 수행 (clean + compile + test-compile)
echo.
echo   [0] Exit
echo       - 프로그램 종료
echo.
echo ================================================================
echo.

set /p choice="선택하세요 (0-4): "

if "%choice%"=="1" goto deepagents
if "%choice%"=="2" goto sourcesearch
if "%choice%"=="3" goto build
if "%choice%"=="4" goto cleanbuild
if "%choice%"=="0" goto exit

echo.
echo 잘못된 선택입니다. 다시 시도해 주세요.
timeout /t 2 > nul
goto menu

:deepagents
cls
echo ================================================================
echo   Deep Agents Demo 실행 중...
echo ================================================================
echo.
call mvn test-compile exec:java -Dexec.mainClass="org.bsc.langgraph4j.deepagents.DeepagentsDemoApplication" -Dexec.classpathScope=test -Dspring.profiles.active=openai,deepagents
echo.
echo ================================================================
echo   프로그램이 종료되었습니다.
echo ================================================================
pause
goto menu

:sourcesearch
cls
echo ================================================================
echo   Source Code Search Agent 실행 중...
echo ================================================================
echo.
echo   SRC_TARGET 환경변수로 검색 대상 디렉토리를 지정할 수 있습니다.
echo   현재 설정: %SRC_TARGET%
echo   (미설정 시 기본값: src/main/java)
echo.
echo ================================================================
echo.
call mvn test-compile exec:java -Dexec.mainClass="org.bsc.langgraph4j.deepagents.SourceCodeSearchAgentApplication" -Dexec.classpathScope=test
echo.
echo ================================================================
echo   프로그램이 종료되었습니다.
echo ================================================================
pause
goto menu

:build
cls
echo ================================================================
echo   프로젝트 컴파일 중...
echo ================================================================
echo.
call mvn compile test-compile
echo.
echo ================================================================
echo   컴파일이 완료되었습니다.
echo ================================================================
pause
goto menu

:cleanbuild
cls
echo ================================================================
echo   클린 빌드 수행 중...
echo ================================================================
echo.
call mvn clean compile test-compile
echo.
echo ================================================================
echo   클린 빌드가 완료되었습니다.
echo ================================================================
pause
goto menu

:exit
echo.
echo 프로그램을 종료합니다.
endlocal
exit /b 0

