#!/usr/bin/env bash
# Run in a DISPOSABLE Ubuntu 24.04 container. No privileged capabilities/network.
set -euo pipefail
mkdir -p /mock-bin /root/.ssh /etc/sudoers.d
printf 'ssh-ed25519 AAAA_TEST_PUBLIC_KEY fixture\n' > /root/.ssh/authorized_keys
for name in apt-get systemctl ufw docker sysctl visudo ssh-keygen; do
  printf '#!/bin/sh\nprintf "%%s\\n" "%s $*" >> /tmp/calls\n' "$name" > "/mock-bin/$name"
done
cat > /mock-bin/curl <<'EOF'
#!/bin/sh
while [ "$1" != '-o' ]; do shift; done
printf 'fixture docker public signing key\n' > "$2"
EOF
cat > /mock-bin/fallocate <<'EOF'
#!/bin/sh
truncate -s 4294967296 /swapfile.stemotion-new
EOF
cat > /mock-bin/mkswap <<'EOF'
#!/bin/sh
echo mkswap >> /tmp/calls
EOF
cat > /mock-bin/blkid <<'EOF'
#!/bin/sh
echo swap
EOF
cat > /mock-bin/swapon <<'EOF'
#!/bin/sh
if [ "$1" = '--show=NAME' ]; then
  [ ! -f /tmp/swap-active ] || echo /swapfile
elif [ "$1" = '/swapfile' ]; then
  touch /tmp/swap-active
fi
exit 0
EOF
chmod +x /mock-bin/*
export PATH=/mock-bin:$PATH
bash /deployment/setup.sh >/tmp/setup-first.log
bash /deployment/setup.sh >/tmp/setup-second.log
test "$(grep -c '^/swapfile ' /etc/fstab)" = 1
test "$(grep -c AAAA_TEST /home/deploy/.ssh/authorized_keys)" = 1
test "$(grep -c '^mkswap$' /tmp/calls)" = 1
test "$(stat -c %a /swapfile)" = 600
test "$(stat -c %a /home/deploy/.ssh)" = 700
test "$(stat -c %a /home/deploy/.ssh/authorized_keys)" = 600
test "$(stat -c %a /etc/sudoers.d/90-stemotion-deploy)" = 440
id -nG deploy | grep -qw docker
id -nG deploy | grep -qw sudo
SSH_CONNECTION='127.0.0.1 12345 127.0.0.1 2222' bash /deployment/setup.sh >/tmp/non22.log 2>&1 && exit 1
grep -q 'Expected SSH port 22' /tmp/non22.log
echo 'PASS setup twice: single key/fstab entry, no swap reformat, permissions/groups, non-22 refusal'
echo 'MOCKED apt/curl/systemctl/UFW/swapon; not a real VM/systemd/firewall validation'
