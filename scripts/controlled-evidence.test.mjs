import test from 'node:test';
import assert from 'node:assert/strict';
import { controlledEvidence } from './lib/controlled-evidence.mjs';
const task = () => ({taskId:'query',timing:{cost:1},subType:'Query',usage:{model_name:'gpt-6-luna',response_model_name:'gpt-6-luna'},uiContext:{shotSize:{width:988,height:1972},shrunkShotToLogicalRatio:1},output:{success:true,observation:'Auto brightness on'}});
const html = t => `<script type="midscene_web_dump">${JSON.stringify({tasks:[t]})}</script>`;
test('controlled comparisons require actual model, full-size image and independent success',()=>{
 assert.equal(controlledEvidence(html(task())).modelCalls,1);
 for (const mutate of [t=>t.usage.response_model_name='different',t=>delete t.usage.response_model_name,t=>t.output.success=false,t=>t.output.observation='',t=>t.uiContext.shotSize.width=659]) {
  const t=task();mutate(t);assert.throws(()=>controlledEvidence(html(t)));
 }
 assert.throws(()=>controlledEvidence('old unrelated report'));
});
