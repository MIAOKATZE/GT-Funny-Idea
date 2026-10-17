"""Run only the marked private GTFI RC2 server and preserve console/process evidence."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import re
import socket


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', type=Path, required=True)
    parser.add_argument('--instance', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--keep-open', action='store_true')
    parser.add_argument('--timeout', type=int, default=2400)
    parser.add_argument('--selector', default='all')
    parser.add_argument('--heap', default='4G')
    args = parser.parse_args()
    instance = args.instance.resolve(strict=True)
    marker = json.loads((instance / 'testlab-instance.json').read_text(encoding='utf-8'))
    if marker.get('instance') != 'GTFI' or marker.get('bind') != '127.0.0.1':
        parser.error('Private loopback GTFI marker required')
    with socket.socket() as guard:
        try:
            guard.bind(('127.0.0.1', int(marker['port'])))
        except OSError:
            parser.error('Private fixture port is already occupied; never start a second server')
    candidates = list((instance / 'mods').glob('gtfi*.jar'))
    if len(candidates) != 1:
        parser.error('Exactly one normal GTFI candidate must be installed in the private fixture')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    commands = output / 'commands.txt'
    commands.write_text('', encoding='utf-8')
    command = [str(args.java.resolve(strict=True)), '-Xms1G', '-Xmx' + args.heap, '-Dfml.readTimeout=180',
               '-Dfile.encoding=UTF-8', '@java9args.txt', '-jar', 'lwjgl3ify-forgePatches.jar', 'nogui']
    (output / 'launch-command.json').write_text(json.dumps(command, indent=2), encoding='utf-8')
    manifest = dict(java=str(args.java.resolve()),
                    java_sha256=hashlib.sha256(args.java.read_bytes()).hexdigest(),
                    candidate=str(candidates[0]),
                    candidate_sha256=hashlib.sha256(candidates[0].read_bytes()).hexdigest(),
                    runtime={name: hashlib.sha256((instance / 'mods' / name).read_bytes()).hexdigest()
                             for name in ['modularui2-2.3.92-1.7.10.jar', 'Thaumcraft-1.7.10-4.2.3.5.jar',
                                          'gregtech-5.09.54.205.jar']})
    (output / 'runtime-manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    start = time.monotonic()
    ready = False
    complete = False
    completed_report = None
    timed_out = False
    sent = 0
    last_status = 0
    log_path = output / 'server-console.log'
    with log_path.open('wb') as log:
        process = subprocess.Popen(command, cwd=instance, stdout=log, stderr=subprocess.STDOUT,
                                   stdin=subprocess.PIPE, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        (output / 'process.json').write_text(json.dumps(dict(pid=process.pid, instance=str(instance)), indent=2), encoding='utf-8')

        def send(line):
            process.stdin.write((line + '\n').encode('utf-8'))
            process.stdin.flush()
            print('COMMAND ' + line, flush=True)

        try:
            while process.poll() is None:
                content = log_path.read_text(encoding='utf-8', errors='replace')
                if not ready and 'Done (' in content:
                    ready = True
                    print('READY pid=' + str(process.pid), flush=True)
                    send('gtfi test run ' + args.selector)
                if ready and '[GTFI-TEST] COMPLETE ' in content and not complete:
                    complete = True
                    match = re.search(r'\[GTFI-TEST\] COMPLETE \w+ ([^\r\n]+\.json)', content)
                    if match:
                        completed_report = Path(match.group(1))
                    send('gtfi test report')
                    print('SUITE_COMPLETE', flush=True)
                    if not args.keep_open:
                        send('stop')
                lines = commands.read_text(encoding='utf-8-sig').splitlines()
                for line in lines[sent:]:
                    if line.strip():
                        send(line.strip())
                sent = len(lines)
                if ready and not complete and time.monotonic() - last_status > 30:
                    send('gtfi test status')
                    last_status = time.monotonic()
                if time.monotonic() - start > args.timeout:
                    timed_out = True
                    send('stop')
                    break
                time.sleep(1)
            try:
                code = process.wait(timeout=40)
            except subprocess.TimeoutExpired:
                # This exact process is owned by this runner, never another Java/Prism process.
                process.kill()
                code = process.wait(timeout=20)
        finally:
            if process.poll() is None:
                try:
                    send('stop')
                    process.wait(timeout=40)
                except (OSError, subprocess.TimeoutExpired):
                    process.kill()
                    process.wait(timeout=20)
    report_paths = sorted((instance / 'world' / 'gtfi-test-reports').glob('*.json'), key=lambda p: p.stat().st_mtime)
    reports = []
    for path in report_paths:
        raw = path.read_bytes()
        data = json.loads(raw)
        reports.append(dict(path=str(path), sha256=hashlib.sha256(raw).hexdigest(), status=data.get('status'),
                            runId=data.get('runId'), scenarios=len(data.get('results', []))))
    selected = None
    if completed_report is not None and completed_report.is_file():
        selected = json.loads(completed_report.read_text(encoding='utf-8'))
    business_pass = (selected is not None and selected.get('status') == 'PASS'
                     and selected.get('selector') == args.selector and bool(selected.get('results'))
                     and all(case.get('status') == 'PASS' for case in selected['results']))
    receipt = dict(instance=str(instance), pid=process.pid, exit_code=process.returncode,
                   closed=process.poll() is not None, ready=ready, complete=complete, timeout=timed_out,
                   elapsed_seconds=round(time.monotonic()-start, 3), reports=reports,
                   selected_report=str(completed_report), selected_run=selected.get('runId') if selected else None,
                   business_pass=business_pass)
    (output / 'process-receipt.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(json.dumps(receipt, indent=2), flush=True)
    raise SystemExit(0 if ready and complete and business_pass and not timed_out and process.returncode == 0 else 1)


if __name__ == '__main__':
    main()
