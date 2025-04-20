#!/bin/bash
# Setup script for GitHub Codespace environment

# Install McFly using Homebrew
echo "Installing McFly..."
brew install mcfly

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

sdkmanager --sdk_root=${ANDROID_HOME} "platform-tools" "platforms;android-34" "build-tools;34.0.0"

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
