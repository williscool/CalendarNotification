#!/bin/bash
# Setup script for GitHub Codespace environment

# Install McFly using Homebrew
echo "Installing McFly..."
brew install mcfly

# Configure McFly for bash
echo "Configuring McFly for bash..."
if ! grep -q "mcfly init bash" ~/.bashrc; then
  echo 'eval "$(mcfly init bash)"' >> ~/.bashrc
fi

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