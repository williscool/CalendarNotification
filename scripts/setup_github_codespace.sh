#!/bin/bash
# Setup script for GitHub Codespace environment

# Install McFly using Homebrew
echo "Installing McFly..."
brew install mcfly

cp .devcontainer/mcfly_history.db ~/.local/share/mcfly/history.db

# Configure McFly for bash
echo "Configuring McFly for bash..."
# Add check for interactive shell before initializing McFly
echo 'case $- in' >> ~/.bashrc
echo '  *i*) ;;  # Interactive shell, do nothing' >> ~/.bashrc
echo '  *)' >> ~/.bashrc
echo '    echo "$(date): NON INTERACTIVE SHELL - MCFLY IS DISABLED! (PID $$, PPID $PPID, command: $0 $@)" >> "$HOME/.bashrc_noninteractive.log"' >> ~/.bashrc
echo '    ;;' >> ~/.bashrc
echo 'esac' >> ~/.bashrc
echo '' >> ~/.bashrc
echo 'eval "$(mcfly init bash)"' >> ~/.bashrc

echo 'export ANDROID_HOME=/tmp/Android/Sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools' >> ~/.bashrc
echo 'export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH' >> ~/.bashrc
echo 'export ANDROID_AVD_HOME=/tmp/my_avd_home' >> ~/.bashrc

## begin ephemeral android sdk setup. will have to do this every time.
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

sudo apt install qemu-kvm
sudo groupadd -r kvm
sudo adduser $USER kvm
sudo chown $USER /dev/kvm


avdmanager create avd --name 7.6_Fold-in_with_outer_display_API_34 --package "system-images;android-34;google_apis_playstore;x86_64" --device "7.6in Foldable"
# you can run with  emulator -avd 7.6_Fold-in_with_outer_display_API_34 -no-window -gpu swiftshader_indirect -noaudio -no-boot-anim -camera-back none -no-snapshot



# end ephemeral android sdk setup

# begin ephemeral gradle setup
export GRADLE_USER_HOME=/tmp/gradle-cache
mkdir -p $GRADLE_USER_HOME
# end ephemeral gradle setup


# Setup Git configuration
echo "Setting up Git configuration..."
git config --global color.ui true
git config --global color.branch true
git config --global color.diff true
git config --global color.status true
git config --global color.log true
git config --global alias.co checkout
git config --global alias.ci commit
git config --global alias.st status
git config --global alias.br branch
git config --global alias.log "log --color=always"

# Source bashrc to apply changes in current session
echo "Applying changes to current session..."
source ~/.bashrc

echo "setup complete!"