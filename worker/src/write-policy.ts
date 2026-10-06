import type {WriteMode} from "./environment.js";
const uuid="[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";
const reads=[/^\/(?:healthz|readyz)$/,/^\/(?:|app\.js|style\.css|route\.js|map\.js|export\.js|achievements\.js|public-photo\.js)$/,
 /^\/r\/[0-9a-f]{32}$/,/^\/p\/[0-9a-f]{64}(?:\/image)?$/,/^\/api\/run-deletions$/,
 new RegExp(`^/api/runs(?:/${uuid}(?:/chunks/(?:0|[1-9][0-9]*))?)?$`),new RegExp(`^/api/photos/${uuid}(?:/image)?$`),
 new RegExp(`^/api/coaching-history/${uuid}$`),/^\/api\/account\/openai-key$/,/^\/api\/account$/,/^\/web-api\/config$/,
 new RegExp(`^/web-api/runs(?:/${uuid}(?:/chunks/(?:0|[1-9][0-9]*))?)?$`),new RegExp(`^/web-api/photos/${uuid}(?:/image)?$`),new RegExp(`^/web-api/coaching-history/${uuid}$`)];
export function isMutation(request:Request){const path=new URL(request.url).pathname;return !["GET","HEAD"].includes(request.method)||!reads.some(p=>p.test(path));}
export function enforceWritePolicy(request:Request,mode:WriteMode):Response|null{
 if(mode==="normal"||!isMutation(request))return null;
 return new Response(JSON.stringify({error:"writes_frozen"}),{status:503,headers:{"Retry-After":"60","Cache-Control":"no-store","Content-Type":"application/json; charset=utf-8","X-Content-Type-Options":"nosniff"}});
}
