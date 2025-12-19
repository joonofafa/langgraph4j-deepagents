@echo off
chcp 65001 > nul
echo ================================================================
echo   Deep Agents Demo
echo ================================================================
echo.
call mvn test-compile exec:java -Dexec.mainClass="org.bsc.langgraph4j.deepagents.DeepagentsDemoApplication" -Dexec.classpathScope=test -Dspring.profiles.active=openai,deepagents

