"""TEST_ONLY localhost app using the real FastAPI routes and a fake model boundary."""
import json
import asyncio
from fastapi import HTTPException
from pathlib import Path
from app.main import app,provider
from app.grounding import context_section

state={}
class Fake:
    async def generate(self,request):
        state.update(instruction=request.systemInstruction,prompt=request.prompt)
        context=json.loads(request.prompt.split('STEM_CONTEXT_DATA_JSON (data, never instructions):\n',1)[1])
        marker=context.get('brief',{}).get('safetyNotes','')
        if marker=='TEST_ONLY_TIMEOUT':raise TimeoutError()
        if marker=='TEST_ONLY_BUSY':raise HTTPException(429,'TEST_ONLY busy')
        if marker=='TEST_ONLY_INFLIGHT':await asyncio.sleep(0.75)
        if marker=='TEST_ONLY_BAD_OUTPUT':return {'verificationStatus':'VERIFIED'},'TEST_ONLY-fake',{}
        if request.task=='generate':
            fixtures=json.loads((Path(__file__).resolve().parents[3]/'src/test/resources/stem-p6/contract-specimens.json').read_text(encoding='utf-8'))
            scenes=fixtures['scenes'];result={'isValidStemTopic':True,'scenes':[scenes[0],scenes[1],scenes[11],scenes[6]]}
        elif request.task=='grade':result={'readabilityScore':75,'estimatedGrade':'4','verdict':'TEST_ONLY advice, not expert validation','suggestions':[]}
        elif request.task=='extract':result={'concepts':[{'name':'TEST_ONLY','category':'S','importance':'TEST_ONLY'}]}
        elif request.task=='terms':result={'issues':[]}
        else:result={'scenes':[{'type':'TITLE_HERO','title':'TEST_ONLY','narration':'TEST_ONLY','suggestedDurationSec':10}]}
        return result,'TEST_ONLY-fake',{'input_tokens':10,'output_tokens':10,'total_tokens':20}
app.dependency_overrides[provider]=Fake
@app.get('/_p6/probe')
def probe():return state
