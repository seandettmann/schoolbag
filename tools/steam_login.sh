#!/bin/sh
# Saves a Steam login for the Mod workflow (.github/workflows/mod.yml), so
# GitHub can upload the mod to the Workshop. Run it on the Mac:
#
#   sh tools/steam_login.sh
#
# or, without a copy of the repo:
#
#   sh -c "$(curl -fsSL https://raw.githubusercontent.com/seandettmann/schoolbag/main/tools/steam_login.sh)"
#
# It needs steamcmd (brew install --cask steamcmd). steamcmd itself asks for
# your Steam password and Steam Guard code (or a tap in the Steam app); this
# script never sees them. Then it saves two secrets in the repo:
# STEAM_USERNAME and STEAM_CONFIG_VDF (steamcmd's saved login, as base64).
# With the GitHub CLI (gh) signed in it sets them itself; otherwise it copies
# the login to the clipboard for you to paste on GitHub.
#
# Run it again whenever the Mod workflow says Steam didn't accept the login.
set -eu
REPO=seandettmann/schoolbag
CONFIG="$HOME/Library/Application Support/Steam/config/config.vdf"
SECRETS_PAGE="https://github.com/$REPO/settings/secrets/actions"

if ! command -v steamcmd > /dev/null 2>&1; then
  echo "steamcmd isn't installed. Install it with:  brew install --cask steamcmd" >&2
  exit 1
fi

printf 'Steam username: '
read -r STEAM_USER
if [ -z "$STEAM_USER" ]; then echo "No username, nothing done." >&2; exit 1; fi

echo
echo "Signing in with steamcmd. It asks for your password and a Steam Guard code."
echo "(The first time, it updates itself first, which takes a minute.)"
echo
steamcmd +login "$STEAM_USER" +quit

if [ ! -s "$CONFIG" ] || ! grep -q "ConnectCache" "$CONFIG"; then
  echo >&2
  echo "steamcmd didn't save a login (nothing in $CONFIG). Was the sign-in accepted?" >&2
  exit 1
fi

echo
if command -v gh > /dev/null 2>&1 && gh auth status > /dev/null 2>&1; then
  printf '%s' "$STEAM_USER" | gh secret set STEAM_USERNAME -R "$REPO"
  base64 < "$CONFIG" | tr -d '\n' | gh secret set STEAM_CONFIG_VDF -R "$REPO"
  echo "Done: saved STEAM_USERNAME and STEAM_CONFIG_VDF in $REPO."
else
  base64 < "$CONFIG" | tr -d '\n' | pbcopy
  echo "Your Steam login is on the clipboard. On $SECRETS_PAGE"
  echo "add two repository secrets (New repository secret):"
  echo "  STEAM_USERNAME    $STEAM_USER"
  echo "  STEAM_CONFIG_VDF  paste (Cmd-V)"
  echo "Then copy something else, so the login doesn't stay on the clipboard."
fi
echo "To try it: Actions tab, Mod, Run workflow."
