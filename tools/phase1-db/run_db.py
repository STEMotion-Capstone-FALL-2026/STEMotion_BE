import os,re,subprocess,sys
from pathlib import Path
env=os.environ.copy()
repo=Path(__file__).resolve().parents[2]
env['STEMOTION_BE_ROOT']=str(repo)
Path('.audit/phase1').mkdir(parents=True,exist_ok=True)
for line in (repo/'.env').read_text(encoding='utf8').splitlines():
    m=re.match(r'^([A-Z][A-Z0-9_]*)\s*=\s*(.*)$',line.strip())
    if m: env[m[1]]=m[2].strip().strip('\"\'')
base=Path.home()/'.m2/repository'
libraries=[]
for folder in ['org/postgresql/postgresql','org/flywaydb/flyway-core','com/google/code/gson/gson',
 'com/fasterxml/jackson/core/jackson-core','com/fasterxml/jackson/core/jackson-databind',
 'com/fasterxml/jackson/core/jackson-annotations','com/fasterxml/jackson/dataformat/jackson-dataformat-toml',
 'org/apache/commons/commons-text','org/apache/commons/commons-lang3']:
    artifact=folder.rsplit('/',1)[1]
    candidates=[p for p in (base/folder).glob('*/*.jar') if p.name==f'{artifact}-{p.parent.name}.jar']
    chosen=next((p for p in candidates if '/9.22.3/' in p.as_posix() or '/2.15.4/' in p.as_posix()),max(candidates,key=lambda p:p.stat().st_mtime))
    libraries.append(str(chosen))
result=subprocess.run(['java','--class-path',os.pathsep.join(libraries),str(Path(__file__).with_name('Phase1Database.java')),sys.argv[1]],env=env,capture_output=True,text=True,encoding='utf8')
output=result.stdout+result.stderr
for k,v in env.items():
    if any(w in k for w in ('PASSWORD','SECRET','KEY','DB_URL')) and v: output=output.replace(v,'[REDACTED]')
Path('.audit/phase1/'+sys.argv[1]+'.redacted.log').write_text(output,encoding='utf8')
print(output[-5000:])
raise SystemExit(result.returncode)
