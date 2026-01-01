#!/bin/bash
# Safety check: Ensure dev page menu item has android:visible="false"
# This prevents accidentally shipping with the dev page exposed

set -e

MENU_FILE="android/app/src/main/res/menu/main.xml"

# Check if file exists
if [ ! -f "$MENU_FILE" ]; then
    echo "❌ FATAL: Menu file not found: $MENU_FILE"
    exit 1
fi

echo "Checking $MENU_FILE..."

# Extract the entire <item> block containing action_test_page
# -P = Perl regex, -z = treat as NUL-separated (multiline), -o = only matching
# tr removes null bytes to avoid bash warning
ITEM_BLOCK=$(grep -Pzo '(?s)<item[^>]*android:id="@\+id/action_test_page"[^>]*/>' "$MENU_FILE" 2>/dev/null | tr -d '\0' || echo "")

if [ -z "$ITEM_BLOCK" ]; then
    echo "❌ FATAL: Could not find <item> with android:id=\"@+id/action_test_page\""
    exit 1
fi

# Check if visible="false" is present in the block
if echo "$ITEM_BLOCK" | grep -q 'android:visible="false"'; then
    echo "✅ Dev page visibility check passed (visible=\"false\")"
    exit 0
elif echo "$ITEM_BLOCK" | grep -q 'android:visible="true"'; then
    echo "❌ FATAL: Dev page has visible=\"true\" (must be \"false\")"
    exit 1
else
    echo "⚠️ Warning: Dev page item has no visible attribute"
    echo "If visible attribute is missing, Android defaults to true - this is unsafe!"
    exit 1
fi
