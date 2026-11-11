#!/usr/bin/env bash
set -Eeuo pipefail

# GitHub's Ubuntu 24.04 hosted image sometimes points apt-mirrors.txt at
# azure.archive.ubuntu.com. When that DNS name fails, use Ubuntu's official
# archive mirror. The apt archive signatures are still verified normally.
if [[ -f /etc/apt/apt-mirrors.txt ]] &&
   grep -q 'azure.archive.ubuntu.com' /etc/apt/apt-mirrors.txt; then
    echo "Using official archive.ubuntu.com instead of hosted Azure apt mirror"
    sudo sed -i 's|azure.archive.ubuntu.com|archive.ubuntu.com|g' /etc/apt/apt-mirrors.txt
fi

sudo apt-get update -qq -o Acquire::Retries=1
sudo apt-get install -y --no-install-recommends -o Acquire::Retries=1 "$@"
