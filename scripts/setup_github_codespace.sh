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



wget https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip commandlinetools-linux-13114758_latest.zip -d ~/Android/Sdk
rm commandlinetools-linux-13114758_latest.zip
mv ~/Android/Sdk/cmdline-tools/* ~/Android/Sdk/cmdline-tools/latest/

echo 'export ANDROID_HOME=$HOME/Android/Sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools' >> ~/.bashrc
echo 'export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH' >> ~/.bashrc
echo 'export ANDROID_AVD_HOME=/tmp/my_avd_home' >> ~/.bashrc

yes | sdkmanager --sdk_root=${ANDROID_HOME} "platform-tools" "platforms;android-34" "build-tools;34.0.0"
yes | sdkmanager --install "system-images;android-34;google_apis_playstore;x86_64"

sudo apt install qemu-kvm
sudo groupadd -r kvm
sudo adduser $USER kvm
sudo chown $USER /dev/kvm

avdmanager create avd --name my_avd_name --package "system-images;android-34;google_apis_playstore;x86_64" --device "7.6in Foldable"

mkdir -p /tmp/my_avd_home

export GRADLE_USER_HOME=/tmp/gradle-cache
mkdir -p $GRADLE_USER_HOME



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

echo "McFly and Git configuration setup complete!"

sudo apt-get install android-sdk-platform-tools
