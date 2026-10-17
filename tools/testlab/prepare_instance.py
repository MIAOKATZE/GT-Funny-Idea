"""Create a private, independently extracted RC2 server. Never modifies another instance."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--instance', type=Path, required=True)
    parser.add_argument('--port', type=int, default=25676)
    args = parser.parse_args()
    target = args.instance.resolve()
    if target.exists():
        parser.error('Target must not exist; existing instances are never overwritten')
    digest = hashlib.sha256(args.archive.read_bytes()).hexdigest()
    expected = 'e005a0d050ec06073b3b0d11b149b5ab9355cacf4b8c4b837c50b8a89946dbf9'
    if digest != expected:
        parser.error('Archive is not the verified GTNH 2.9.0-RC-2 Java 17-26 server pack')
    target.mkdir(parents=True)
    with zipfile.ZipFile(args.archive) as archive:
        for entry in archive.infolist():
            resolved = (target / entry.filename).resolve()
            if not resolved.is_relative_to(target):
                raise ValueError('Archive path escapes private instance')
        archive.extractall(target)
    # This private test fixture inherits the existing explicitly accepted local RC2 EULA.
    (target / 'eula.txt').write_text('eula=true\n', encoding='ascii')
    (target / 'server.properties').write_text(
        f'server-ip=127.0.0.1\nserver-port={args.port}\nonline-mode=false\nwhite-list=true\n'
        'allow-flight=true\nlevel-name=world\nlevel-type=FLAT\n'
        'generator-settings=2;7,2x3,2;1;\nview-distance=3\nmax-players=4\n'
        'spawn-monsters=false\nspawn-animals=false\nspawn-npcs=false\n'
        'generate-structures=false\nspawn-protection=0\nenable-rcon=false\nenable-query=false\n'
        'op-permission-level=4\nmotd=GTFI isolated test fixture\n', encoding='ascii')
    (target / 'testlab-instance.json').write_text(json.dumps(dict(
        instance='GTFI', pack_version='2.9.0-RC-2', bind='127.0.0.1', port=args.port,
        source_zip=str(args.archive.resolve()), source_sha256=digest,
        copy_mode='independent official archive extraction; no hardlinks',
        world_policy='fresh private world; no existing player saves copied'), indent=2), encoding='utf-8')
    print(target)


if __name__ == '__main__':
    main()
