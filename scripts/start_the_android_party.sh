NUM_CPUS=$(nproc)
yarn
./scripts/ccachify_native_modules.sh
cd android
./gradlew :app:assembleX8664Debug :app:bundleX8664Release :app:assembleX8664DebugAndroidTest --parallel --max-workers=$NUM_CPUS --build-cache --configure-on-demand --info