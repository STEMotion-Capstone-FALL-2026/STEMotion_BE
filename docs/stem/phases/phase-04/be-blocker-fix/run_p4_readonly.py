from pathlib import Path
import os,subprocess,tempfile,time,json,urllib.request,urllib.error,re,xml.etree.ElementTree as ET
out=Path(__file__).resolve().parent;be=out.parents[4];fe=be.parents[1]/'phase-04'/'STEMotion_FE'
build=be/'.audit/be-blocker-fix/after'
r=ET.parse(build/'surefire-reports/TEST-com.stemotion.e2e.StemFullContextRegressionTest.xml')
cp=next(p.attrib['value'] for p in r.findall('.//property') if p.attrib['name']=='java.class.path')
java=Path('C:/Users/LeTran/.jdks/ms-17.0.18/bin/java.exe')
source=r"""
import com.stemotion.StemotionApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.io.*;
import java.lang.reflect.*;
public class OwnedFullContextServer {
 public static void main(String[] args)throws Exception{
  System.setProperty("spring.devtools.restart.enabled","false");
  Method defaults=Class.forName("com.stemotion.e2e.E2eServer").getDeclaredMethod("launchArguments",String[].class);defaults.setAccessible(true);
  String[] launch=(String[])defaults.invoke(null,(Object)args);
  try(ConfigurableApplicationContext ctx=new SpringApplication(StemotionApplication.class,Class.forName("com.stemotion.e2e.E2eServer$Config")).run(launch)){
   System.out.println("OWNED_BACKEND_READY "+((ServletWebServerApplicationContext)ctx).getWebServer().getPort());
   BufferedReader input=new BufferedReader(new InputStreamReader(System.in));String command;
   while((command=input.readLine())!=null){
    if(command.equals("STOP"))break;
    if(command.equals("AUDIT")){
     JdbcTemplate db=ctx.getBean(JdbcTemplate.class);Map<String,Object> audit=new LinkedHashMap<>();
     audit.put("migrations",db.queryForObject("select count(*) from flyway_schema_history where success",Integer.class));
     audit.put("assessments",db.queryForObject("select count(*) from stem_assessments",Integer.class));
     audit.put("approvedDecisions",db.queryForObject("select count(*) from status_histories where event_type='REVIEW_DECISION' and decision='APPROVED'",Integer.class));
     audit.put("approvalReceipts",db.queryForObject("select count(*) from stem_receipts where operation='APPROVE_SCRIPT'",Integer.class));
     audit.put("settingsRevision",db.queryForObject("select coalesce(sum(revision),0) from stem_settings",Integer.class));
     audit.put("advisoryWorkspaces",db.queryForObject("select count(*) from stem_settings where mode='ADVISORY'",Integer.class));
     audit.put("modeAudits",db.queryForObject("select count(*) from stem_audit where event='MODE_ASSIGNED'",Integer.class));
     audit.put("modeAuditsWithBaseRevision",db.queryForObject("select count(*) from stem_audit where event='MODE_ASSIGNED' and payload ? 'baseRevision'",Integer.class));
     audit.put("modeAuditsWithModeRevision",db.queryForObject("select count(*) from stem_audit where event='MODE_ASSIGNED' and payload ? 'modeRevision'",Integer.class));
     audit.put("verifiedAssessments",db.queryForObject("select count(*) from stem_assessments where verification_status<>'UNVERIFIED'",Integer.class));
     System.out.println("OWNED_DB_AUDIT "+new ObjectMapper().writeValueAsString(audit));
    }
   }
  }finally{System.out.println("OWNED_BACKEND_CLOSED");}
 }
}
"""
keys={'SYSTEMROOT','WINDIR','COMSPEC','PATHEXT','USERPROFILE','HOMEDRIVE','HOMEPATH','APPDATA','LOCALAPPDATA','PROGRAMFILES','PROGRAMFILES(X86)','PROGRAMDATA','ALLUSERSPROFILE','NUMBER_OF_PROCESSORS','PROCESSOR_ARCHITECTURE','TEMP','TMP','PATH'}
env={k:v for k,v in os.environ.items() if k.upper() in keys}
env.update(DB_URL='jdbc:postgresql://127.0.0.1:9/unused_bfix_live',DB_USERNAME='postgres',DB_PASSWORD='',JWT_SECRET='TEST_ONLY-not-production-bfix-01234567890123456789',GEMINI_API_KEY='',BREVO_API_KEY='',GOOGLE_CLIENT_ID='',CI='true',NO_COLOR='1',BFIX_HTTP_AUDIT_PATH=str(out/'p4-http-audit.json'))
result={'ready':False,'test_exit_code':None,'server_closed':False,'fe_source_mutations':False}
with tempfile.TemporaryDirectory(prefix='stem-bfix-live-') as temporary:
 temporary=Path(temporary);src=temporary/'OwnedFullContextServer.java';src.write_text(source,encoding='utf-8')
 args=['-Dfile.encoding=UTF-8','--class-path',cp,'--source','17',str(src),'--server.port=0','--server.address=127.0.0.1','--spring.profiles.active=stem-p3-test','--spring.dotenv.enabled=false','--spring.flyway.locations=classpath:db/migration,classpath:db/auth-v2-e2e,classpath:db/stem-p3','--app.stem.enabled=true','--app.notification-email.enabled=false','--spring.sql.init.mode=always','--spring.sql.init.data-locations='+(fe/'docs/stem/phases/phase-04/takeover-codex/catalog-test-only.sql').as_uri(),'--logging.level.root=WARN','--logging.level.com.stemotion=WARN','--logging.level.org.hibernate.SQL=WARN','--logging.level.io.zonky.test.db.postgres.embedded.EmbeddedPostgres=INFO']
 argfile=temporary/'java.args';argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='utf-8')
 with (out/'p4-backend.log').open('w',encoding='utf-8') as backendlog:
  server=subprocess.Popen([str(java),'@'+str(argfile)],cwd=be,env=env,stdin=subprocess.PIPE,stdout=backendlog,stderr=subprocess.STDOUT,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
  result['server_pid']=server.pid
  try:
   deadline=time.time()+90
   while time.time()<deadline and server.poll() is None:
    text=(out/'p4-backend.log').read_text(encoding='utf-8');match=re.search(r'^OWNED_BACKEND_READY (\d+)$',text,re.M)
    if match:
     port=int(match.group(1));env['P4_LIVE_API_URL']=f'http://127.0.0.1:{port}/api/v1';result.update(ready=True,port=port);break
    time.sleep(0.5)
   if not result['ready']:result['blocker']='Owned backend did not become ready; no alternate server or mock used'
   else:
    print('Owned BE after-fix ready; running original two P4 live tests read-only.',flush=True)
    command=['node',str(fe/'node_modules/vitest/vitest.mjs'),'run','--config',str(out/'vitest-p4-readonly.config.mjs'),'--configLoader','native']
    with (out/'p4-q10.log').open('w',encoding='utf-8') as testlog:
     test=subprocess.run(command,cwd=be,env=env,stdout=testlog,stderr=subprocess.STDOUT,timeout=150)
    result['test_exit_code']=test.returncode
  finally:
   if server.poll() is None:
    try:server.communicate(input='AUDIT\nSTOP\n',timeout=40)
    except subprocess.TimeoutExpired:
     result['cleanup_blocker']='Graceful close timed out; inspect exact owned PG data dir before any cleanup'
     subprocess.run(['taskkill','/PID',str(server.pid),'/T','/F'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=15);server.wait(timeout=10)
   text=(out/'p4-backend.log').read_text(encoding='utf-8');result['server_exit_code']=server.returncode
   result['server_closed']='OWNED_BACKEND_CLOSED' in text and 'shut down postmaster in' in text
   match=re.search(r'^OWNED_DB_AUDIT (.+)$',text,re.M)
   if match:(out/'P4_DB_AUDIT.json').write_text(json.dumps(json.loads(match.group(1)),indent=2),encoding='utf-8')
   (out/'P4_RUN_RESULT.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result),flush=True)
raise SystemExit(0 if result['ready'] and result['test_exit_code']==0 and result['server_closed'] else 1)
