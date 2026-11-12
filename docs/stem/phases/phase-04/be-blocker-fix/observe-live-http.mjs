import {writeFileSync} from 'node:fs';
const api=new URL(process.env.P4_LIVE_API_URL);
if(api.hostname!=='127.0.0.1')throw new Error('Owned loopback API required');
const original=globalThis.fetch;
const events=[];
globalThis.fetch=async(input,options)=>{
 const url=new URL(typeof input==='string'?input:input.url);
 if(url.origin!==api.origin)throw new Error('Unowned HTTP destination blocked by BE test runner');
 const response=await original(input,options);
 let body={};try{body=JSON.parse(options?.body??'{}');}catch{}
 events.push({method:options?.method??'GET',path:url.pathname.replace(/[0-9a-f]{8}-[0-9a-f-]{27,}/g,'{TEST_ONLY_ID}'),
  requestKeys:Object.keys(body),status:response.status,
  ...(url.pathname.endsWith('/stem-settings')&&options?.method==='PUT'?{baseRevision:body.baseRevision,mode:body.mode}:{} )});
 return response;
};
afterAll(()=>writeFileSync(process.env.BFIX_HTTP_AUDIT_PATH,JSON.stringify(events,null,2)));
