#!/bin/bash

read -r -p "$(cat <<'EOF'
WARNING: This script will:
- Delete ALL .bin folders in the current directory
- You will need to reinstall node_modules to get them back

Why do this? the windows build chokes on the script files in the .bin folders

but builds just fine if you remove them. so we do that. thus this convenience script.

Are you sure you want to proceed? (y/N) 
EOF
)" -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]
then
    find . -type d -name ".bin" -prune -exec rm -rf '{}' +
    echo "Deleted all .bin folders"
else
    echo "Operation cancelled"
fi