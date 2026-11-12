from pathlib import Path
import os,subprocess,sys,json,time
out=Path(__file__).resolve().parent
repo=out.parents[3]
java=Path('C:/Users/LeTran/.jdks/ms-17.0.18')
mvn=Path('C:/Users/LeTran/.m2/wrapper/dists/apache-maven-3.9.6-bin/439sdfsg2nbdob9ciift5h5nse/apache-maven-3.9.6/bin/mvn.cmd')
keys={'SYSTEMROOT','WINDIR','COMSPEC','PATHEXT','USERPROFILE','HOMEDRIVE','HOMEPATH','APPDATA','LOCALAPPDATA','PROGRAMFILES','PROGRAMFILES(X86)','PROGRAMDATA','ALLUSERSPROFILE','NUMBER_OF_PROCESSORS','PROCESSOR_ARCHITECTURE','TEMP','TMP','PATH'}
env={k:v for k,v in os.environ.items() if k.upper() in keys}
env.update(JAVA_HOME=str(java),PATH=str(java/'bin')+';'+env.get('PATH',''),DB_URL='jdbc:postgresql://127.0.0.1:9/p3_unused',DB_USERNAME='postgres',DB_PASSWORD='',JWT_SECRET='p3-test-only-local-no-production-value-01234567890123456789',GEMINI_API_KEY='',BREVO_API_KEY='',GOOGLE_CLIENT_ID='',AI_SERVICE_URL='http://127.0.0.1:9',RENDER_SERVICE_URL='http://127.0.0.1:9',NOTIFICATION_EMAIL_ENABLED='false',GOOGLE_LOGIN_ENABLED='false',CI='true',NO_COLOR='1')
name=sys.argv[1]; tests=sys.argv[2] if len(sys.argv)>2 else ''
command=[str(mvn),'-o','-B','-ntp','-Dstemotion.build-directory=.build-p3']
if tests:command+=['-Dtest='+tests]
command+=['test']
start=time.time()
with (out/(name+'.log')).open('w',encoding='utf-8') as log:
 result=subprocess.run(command,cwd=repo,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=900)
data={'name':name,'exit_code':result.returncode,'elapsed_seconds':round(time.time()-start,2),'repo':str(repo),'tests':tests,'external_providers':'disabled/local placeholders; environment allow-list'}
(out/(name+'.json')).write_text(json.dumps(data,indent=2));print(json.dumps(data));sys.exit(result.returncode)
