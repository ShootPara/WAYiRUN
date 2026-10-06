const origin="https://wayirun.slopcopy.com";
for(const path of ["/healthz","/readyz"]){const response=await fetch(origin+path,{redirect:"error"});if(!response.ok)throw Error(`Production smoke failed: ${path} ${response.status}`);await response.body?.cancel();}
console.log("Production health and readiness smoke passed.");
