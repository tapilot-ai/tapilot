import {readFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

// Input contains full phone task wall times, never individual tool timings.
export function compareNativeLatency({baseline, candidate}) {
  const check = runs => {
    if (!Array.isArray(runs) || runs.length < 3) throw Error('At least three runs per variant are required');
    for (const r of runs) {
      if (r.scope !== 'native-task-wall' || !Number.isFinite(r.wallMs) || r.wallMs <= 0) throw Error('Invalid native task timing');
      for (const key of ['task','device','network','startState','requestedModel','responseModel','reasoningEffort','warmth']) {
        if (typeof r[key] !== 'string' || !r[key]) throw Error(`Missing comparison metadata: ${key}`);
      }
      if (r.requestedModel !== r.responseModel) throw Error('Actual model differs from requested model');
    }
  };
  check(baseline);check(candidate);
  const reference=baseline[0];
  for (const r of [...baseline,...candidate]) for(const key of ['task','device','network','startState','requestedModel','responseModel','reasoningEffort','warmth']) {
    if(r[key]!==reference[key])throw Error(`Uncontrolled variable: ${key}`);
  }
  const stats=runs=>{
    const times=runs.filter(r=>r.status==='succeeded' && r.verified===true).map(r=>r.wallMs).sort((a,b)=>a-b);
    const middle=Math.floor(times.length/2);
    return {runs:runs.length,verifiedSuccesses:times.length,failures:runs.length-times.length,
      medianMs:times.length?(times.length%2?times[middle]:(times[middle-1]+times[middle])/2):null};
  };
  const before=stats(baseline),after=stats(candidate);
  // Do not reward an optimization for dropping its slower/failed runs.
  const eligible=before.failures===0 && after.failures===0;
  const reduction=before.medianMs && after.medianMs ? 1-after.medianMs/before.medianMs : null;
  return {scope:'native-task-wall',baseline:before,candidate:after,reduction,
    eligible,halfLatencyReached:eligible && reduction>=0.5};
}
if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  console.log(JSON.stringify(compareNativeLatency(JSON.parse(readFileSync(process.argv[2],'utf8'))),null,2));
}
