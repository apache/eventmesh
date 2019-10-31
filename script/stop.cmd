@echo off
rem Copyright (C) @2017 Webank Group Holding Limited
rem
rem Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
rem in compliance with the License. You may obtain a copy of the License at
rem
rem http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software distributed under the License
rem is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
rem or implied. See the License for the specific language governing permissions and limitations under
rem the License.
rem

if not exist "%JAVA_HOME%\bin\jps.exe" echo Please set the JAVA_HOME variable in your environment, We need java(x64)! & EXIT /B 1

setlocal

set "PATH=%JAVA_HOME%\bin;%PATH%"

if /I "%1" == "broker" (
    echo killing broker
    for /f "tokens=1" %%i in ('jps -m ^| find "DeFiBusBrokerStartup"') do ( taskkill /F /PID %%i )
    echo Done!
) else if /I "%1" == "namesrv" (
    echo killing name server

    for /f "tokens=1" %%i in ('jps -m ^| find "DeFiBusNamesrvStartup"') do ( taskkill /F /PID %%i )

    echo Done!
) else (
    echo Unknown role to kill, please specify broker or namesrv
)