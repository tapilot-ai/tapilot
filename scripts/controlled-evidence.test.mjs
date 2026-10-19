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
test('explicit device geometry remains strict for both dimensions and scale',()=>{
 const t=task();t.uiContext.shotSize={width:1080,height:2400};
 const size={width:1080,height:2400};
 assert.deepEqual(controlledEvidence(html(t),size).screenshot,size);
 assert.throws(()=>controlledEvidence(html(t)));
 assert.throws(()=>controlledEvidence(html(t),{width:1080,height:1972}));
 assert.throws(()=>controlledEvidence(html(t),{width:0,height:2400}));
 t.uiContext.shrunkShotToLogicalRatio=0.5;
 assert.throws(()=>controlledEvidence(html(t),size));
});

test('two-stage verification uses the last query and rejects duplicate verdicts or a failed verdict', () => {
 const read = task(); read.taskId='read'; read.timing.start=1; delete read.output.success;
 const verdict = task(); verdict.timing.start=2;
 const report = () => `<script type="midscene_web_dump">${JSON.stringify({tasks:[verdict,read]})}</script>`;
 assert.equal(controlledEvidence(report()).modelCalls,2);
 verdict.output.success=false;
 assert.throws(()=>controlledEvidence(report()));
 verdict.output.success=true; read.output.success=true;
 assert.throws(()=>controlledEvidence(report()));
 delete read.output.success; read.output='Visible selected item';
 assert.equal(controlledEvidence(report()).modelCalls,2);
 read.output={observation:''};
 assert.throws(()=>controlledEvidence(report()));
});

test('prompt echo is rejected; one malformed-output repair is allowed but retrying a valid false is not', () => {
 const read=task();read.taskId='read';read.timing.start=1;read.output='Visible labels';
 const echo=task();echo.taskId='echo';echo.timing.start=2;echo.param={dataDemand:{observation:'Read the current screenshot'}};echo.output.observation=echo.param.dataDemand.observation;
 const verdict=task();verdict.timing.start=3;
 const report=tasks=>`<script type="midscene_web_dump">${JSON.stringify({tasks})}</script>`;
 assert.throws(()=>controlledEvidence(report([read,echo])));
 assert.equal(controlledEvidence(report([read,echo,verdict])).modelCalls,3);
 echo.output={success:false,observation:'Wrong selected item'};
 assert.throws(()=>controlledEvidence(report([read,echo,verdict])));
});
