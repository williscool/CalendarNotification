#!/bin/bash
# Setup script for GitHub Codespace environment

# Setup Git configuration
# username and email are setup by github
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

# Install McFly using Homebrew
cp .devcontainer/mcfly_history.db ~/.local/share/mcfly/history.db
touch $HOME/.bash_history
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

## begin ephemeral android sdk setup. will have to do this every time.
./scripts/setup_dev_container_android_toolchain.sh
# end ephemeral android sdk setup

# begin ephemeral gradle setup
export GRADLE_USER_HOME=/tmp/gradle-cache
mkdir -p $GRADLE_USER_HOME
# end ephemeral gradle setup

# Source bashrc to apply changes in current session
echo "Applying changes to current session..."
source ~/.bashrc

echo "setup complete!"