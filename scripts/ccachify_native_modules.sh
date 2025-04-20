#!/bin/bash

# 1. List of modules (pipe-separated for grep -E)
MODULES="@op-engineering/op-sqlite|react-native-screens|expo-modules-core"

# 2. Find matching CMakeLists.txt files and store in an array variable
mapfile -t TARGET_FILES < <(
  find node_modules/ -name CMakeLists.txt |
  grep -E "$MODULES" |
  grep '/android/CMakeLists.txt$'
)

# 3. Prepend prepender.txt to each file if not already prepended
for file in "${TARGET_FILES[@]}"; do
  # Check for unique marker at the top of the file
  if head -n 1 "$file" | grep -q 'find_program(CCACHE ccache)'; then
    echo "Already prepended: $file"
  else
    cp "$file" "$file.bak"
	# this is fine unless we ever need to add anything to our CMakeLists then we can move it out
    { cat android/CMakeLists.txt; printf "\n\n"; cat "$file"; }> temp && mv temp "$file"
    echo "Prepended: $file"
  fi
done
