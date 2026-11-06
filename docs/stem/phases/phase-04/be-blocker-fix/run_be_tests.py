from pathlib import Path
import os,subprocess,sys,json,time,xml.etree.ElementTree as ET
out=Path(__file__).resolve().parent
repo=out.parents[4]
name=sys.argv[1]
build=repo/'.audit'/'be-blocker-fix'/name
java=Path('C:/Users/LeTran/.jdks/ms-17.0.18')
mvn=Path('C:/Users/LeTran/.m2/wrapper/dists/apache-maven-3.9.6-bin/439sdfsg2nbdob9ciift5h5nse/apache-maven-3.9.6/bin/mvn.cmd')
keys={'SYSTEMROOT','WINDIR','COMSPEC','PATHEXT','USERPROFILE','HOMEDRIVE','HOMEPATH','APPDATA','LOCALAPPDATA','PROGRAMFILES','PROGRAMFILES(X86)','PROGRAMDATA','ALLUSERSPROFILE','NUMBER_OF_PROCESSORS','PROCESSOR_ARCHITECTURE','TEMP','TMP','PATH'}
env={k:v for k,v in os.environ.items() if k.upper() in keys}
env.update(JAVA_HOME=str(java),PATH=str(java/'bin')+';'+env.get('PATH',''),DB_URL='jdbc:postgresql://127.0.0.1:9/unused_blocker_fix',DB_USERNAME='postgres',DB_PASSWORD='',JWT_SECRET='TEST_ONLY-blocker-fix-not-production-01234567890123456789',GEMINI_API_KEY='',BREVO_API_KEY='',GOOGLE_CLIENT_ID='',AI_SERVICE_URL='http://127.0.0.1:9',RENDER_SERVICE_URL='http://127.0.0.1:9',NOTIFICATION_EMAIL_ENABLED='false',GOOGLE_LOGIN_ENABLED='false',CI='true',NO_COLOR='1')
tests='StemFullContextRegressionTest' if name=='before' else 'StemFullContextRegressionTest,StemPostgresTest,StemValidatorTest'
command=[str(mvn),'-o','-B','-ntp','-Dstemotion.build-directory='+str(build),'-Dtest='+tests,'test']
start=time.time()
with (out/(name+'.log')).open('w',encoding='utf-8') as log:
 result=subprocess.run(command,cwd=repo,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=600,creationflags=subprocess.CREATE_NO_WINDOW)
suites=[]
for file in sorted((build/'surefire-reports').glob('TEST-*.xml')):
 r=ET.parse(file).getroot()
 suites.append({k:r.attrib.get(k) for k in ['name','tests','failures','errors','skipped']})
data={'name':name,'exit_code':result.returncode,'seconds':round(time.time()-start,2),'build_directory':str(build),'suites':suites,'tests':tests}
(out/(name+'.json')).write_text(json.dumps(data,indent=2),encoding='utf-8');print(json.dumps(data));raise SystemExit(result.returncode)
