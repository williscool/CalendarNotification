#!/bin/bash
# Setup script for devcontainer aka GitHub Codespace android development environment
brew install ccache

## begin ephemeral android sdk setup. will have to do this every time.
# so I can run this everytime if I want
export ANDROID_HOME=/tmp/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH
export ANDROID_AVD_HOME=/tmp/my_avd_home

# why becuase they give you hella temp space but a small of permanent 
wget https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip 
mv commandlinetools-linux-13114758_latest.zip /tmp/
mkdir -p $ANDROID_HOME
unzip /tmp/commandlinetools-linux-13114758_latest.zip -d $ANDROID_HOME
mkdir -p $ANDROID_HOME/cmdline-tools/
mkdir -p $ANDROID_HOME/cmdline-tools/latest/
mkdir -p $ANDROID_AVD_HOME
mv $ANDROID_HOME/cmdline-tools/* $ANDROID_HOME/cmdline-tools/latest/

yes | sdkmanager --sdk_root=${ANDROID_HOME} "platform-tools" "platforms;android-34" "build-tools;34.0.0"
yes | sdkmanager --install "system-images;android-34;google_apis_playstore;x86_64"

mkdir -p $ANDROID_AVD_HOME

sudo apt -y install qemu-kvm
sudo groupadd -r kvm
sudo adduser $USER kvm
sudo chown $USER /dev/kvm

avdmanager create avd --name 7.6_Fold-in_with_outer_display_API_34 --package "system-images;android-34;google_apis_playstore;x86_64" --device "7.6in Foldable"
# you can run with  emulator -avd 7.6_Fold-in_with_outer_display_API_34 -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none -no-snapshot

echo ""
echo "======================================================================"
echo "Setup complete!"
echo "Don't forget to run:"
echo ""
echo "    scripts/start_the_android_party.sh"
echo ""
echo "======================================================================"