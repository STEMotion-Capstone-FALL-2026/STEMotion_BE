#!/usr/bin/env bash
# Run only on the intended NEW Ubuntu 24.04 VPS, never on a developer host.
set -Eeuo pipefail
export DEBIAN_FRONTEND=noninteractive
export NEEDRESTART_MODE=a
[[ $EUID == 0 ]] || { echo 'Run as root/sudo' >&2; exit 1; }
# shellcheck source=/dev/null
source /etc/os-release
[[ $ID == ubuntu && $VERSION_ID == 24.04 ]] || { echo 'Ubuntu 24.04 required' >&2; exit 1; }
key_file=${DEPLOY_SSH_KEY_FILE:-/root/.ssh/authorized_keys}
[[ -s $key_file ]] || { echo 'Supply a public authorized_keys file via DEPLOY_SSH_KEY_FILE' >&2; exit 1; }
ssh-keygen -l -f "$key_file" >/dev/null
if [[ -n ${SSH_CONNECTION:-} && ${SSH_CONNECTION##* } != 22 ]]; then
  echo 'Expected SSH port 22; refusing to enable firewall on another SSH port' >&2; exit 1
fi

apt-get update
apt-get -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold -y upgrade
apt-get install -y ca-certificates curl gnupg sudo ufw fail2ban openssh-client python3
install -d -m 0755 /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc.new
chmod 0644 /etc/apt/keyrings/docker.asc.new
mv /etc/apt/keyrings/docker.asc.new /etc/apt/keyrings/docker.asc
printf 'Types: deb\nURIs: https://download.docker.com/linux/ubuntu\nSuites: noble\nComponents: stable\nArchitectures: %s\nSigned-By: /etc/apt/keyrings/docker.asc\n' "$(dpkg --print-architecture)" > /etc/apt/sources.list.d/docker.sources
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

getent group docker >/dev/null || groupadd docker
if ! id deploy >/dev/null 2>&1; then useradd --create-home --shell /bin/bash deploy; fi
usermod -aG sudo,docker deploy
install -d -o deploy -g deploy -m 0700 /home/deploy/.ssh
touch /home/deploy/.ssh/authorized_keys
while IFS= read -r key || [[ -n $key ]]; do
  [[ -z $key || $key == \#* ]] && continue
  grep -Fqx -- "$key" /home/deploy/.ssh/authorized_keys || printf '%s\n' "$key" >> /home/deploy/.ssh/authorized_keys
done < "$key_file"
chown deploy:deploy /home/deploy/.ssh/authorized_keys
chmod 0600 /home/deploy/.ssh/authorized_keys
printf 'deploy ALL=(ALL) NOPASSWD: ALL\n' > /etc/sudoers.d/90-stemotion-deploy
chmod 0440 /etc/sudoers.d/90-stemotion-deploy
visudo -cf /etc/sudoers.d/90-stemotion-deploy

if [[ ! -e /swapfile ]]; then
  # Stage atomically; reruns after interrupted allocation can safely recreate this temp file.
  fallocate -l 4G /swapfile.stemotion-new
  chmod 0600 /swapfile.stemotion-new
  mkswap /swapfile.stemotion-new
  mv /swapfile.stemotion-new /swapfile
else
  [[ $(stat -c %s /swapfile) == 4294967296 ]] || { echo 'Existing /swapfile not 4 GiB; refusing to overwrite' >&2; exit 1; }
  [[ $(blkid -p -s TYPE -o value /swapfile) == swap ]] || { echo 'Existing /swapfile is not swap; refusing to overwrite' >&2; exit 1; }
fi
chmod 0600 /swapfile
swapon --show=NAME --noheadings | grep -Fxq /swapfile || swapon /swapfile
grep -Eq '^/swapfile[[:space:]]' /etc/fstab || printf '/swapfile none swap sw 0 0\n' >> /etc/fstab
printf 'vm.swappiness=10\n' > /etc/sysctl.d/90-stemotion-swap.conf
sysctl -p /etc/sysctl.d/90-stemotion-swap.conf

# New server only. Preserve any existing custom rules on rerun; review status below.
ufw default deny incoming
ufw default allow outgoing
ufw limit 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
mkdir -p /etc/fail2ban/jail.d
printf '[sshd]\nenabled = true\nbackend = systemd\nport = 22\n' > /etc/fail2ban/jail.d/stemotion.local
systemctl enable --now fail2ban
systemctl restart fail2ban
install -d -o deploy -g deploy -m 0750 /opt/stemotion
# This is the dedicated new-server workspace, including an already extracted bundle.
chown -R deploy:deploy /opt/stemotion
echo '=== Verify before closing the current SSH session ==='
docker --version
docker compose version
ufw status verbose
free -h
swapon --show
echo 'Open a SECOND session: ssh deploy@SERVER_IP. Verify sudo -n true and docker ps.'
echo 'Docker group and NOPASSWD sudo confer root-level administration. Keep this SSH key private.'
echo 'Only 22/80/443 should be allowed on a fresh host; review pre-existing custom UFW rules.'
[[ ! -f /var/run/reboot-required ]] || echo 'Reboot required after package/kernel upgrades; verify SSH after reboot before deploying.'
