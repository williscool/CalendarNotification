#!/bin/bash

# Script to diagnose ccache issues
echo "==== CCACHE DIAGNOSTIC INFO ===="
echo "CCACHE VERSION:"
ccache -V

echo -e "\nCCACHE CONFIGURATION:"
ccache -p

echo -e "\nCCACHE STATS:"
ccache -s

echo -e "\nCCACHE ENVIRONMENT VARIABLES:"
env | grep -i ccache

echo -e "\nCMake NATIVE TOOLCHAIN INFO:"
if [ -f "android/app/.cxx/cmake/debug/x86_64/toolchain.ninja" ]; then
  cat android/app/.cxx/cmake/debug/x86_64/toolchain.ninja
else
  echo "Toolchain file not found"
fi

echo -e "\nCHECKING IF CCACHE IS IN PATH:"
which ccache

echo -e "\nCHECKING CCACHE SYMLINKS:"
ls -la /usr/lib/ccache/

echo -e "\nCHECKING GRADLE PROPERTIES:"
cat android/gradle.properties | grep -i ccache

echo -e "\nCHECKING GRADLE EXTERNAL NATIVE BUILD OPTIONS:"
cat android/app/build.gradle | grep -i externalnative

echo -e "\nCHECKING CMAKE LISTS:"
cat android/app/CMakeLists.txt

echo -e "\nCHECKING CCACHE LOG (if available):"
if [ -f "/tmp/ccache.log" ]; then
  tail -n 200 /tmp/ccache.log
else
  echo "No ccache log found"
fi 