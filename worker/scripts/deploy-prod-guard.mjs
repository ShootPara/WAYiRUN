const account="6bf560a8b86852196c9898023e3b8d6b";
const rateNames=["RUN_RATE_LIMIT","RUN_TOTAL_LIMIT","AUTH_RATE_LIMIT","AUTH_TOTAL_LIMIT"];
const devIds=new Set(["786531903","786531904","786531901","786531902"]);
const devOAuthIds=new Set(["933230558080-ko4r7v0kmhip4i0n7u32diaimv1in73q.apps.googleusercontent.com","933230558080-8o82hopmd4ibnt2fllqpr8252lg3q44t.apps.googleusercontent.com"]);
const placeholder=value=>typeof value!=="string"||!value||/^__.+__$/.test(value);
export function assertProductionTarget(config){
 const db=config.d1_databases,route=config.routes,rates=config.ratelimits??[],vars=config.vars??{};
 const ids=rates.map(x=>x.namespace_id),names=rates.map(x=>x.name);
 if(config.account_id!==account||config.name!=="wayirun-prod"||config.workers_dev!==false||config.env||config.route||
   vars.APP_ENV!=="production"||vars.PUBLIC_ORIGIN!=="https://wayirun.slopcopy.com"||vars.WRITE_MODE!=="normal"||
   db?.length!==1||db[0].binding!=="DB"||db[0].database_name!=="wayirun-prod-db"||placeholder(db[0].database_id)||db[0].database_id==="04bf8339-386b-4a03-80d7-12b4d1f99ffb"||
   route?.length!==1||route[0].pattern!=="wayirun.slopcopy.com"||route[0].custom_domain!==true||
   rates.length!==4||new Set(names).size!==4||rateNames.some(name=>!names.includes(name))||new Set(ids).size!==4||ids.some(id=>placeholder(id)||devIds.has(id))||
   placeholder(vars.GOOGLE_WEB_CLIENT_ID)||placeholder(vars.GOOGLE_ANDROID_CLIENT_ID)||devOAuthIds.has(vars.GOOGLE_WEB_CLIENT_ID)||devOAuthIds.has(vars.GOOGLE_ANDROID_CLIENT_ID)||
   vars.LOCATION_LOOKUP_URL!=="https://nominatim.openstreetmap.org/reverse"||vars.WEATHER_FORECAST_URL!=="https://api.open-meteo.com/v1/forecast"||vars.WEATHER_ARCHIVE_URL!=="https://archive-api.open-meteo.com/v1/archive"||
   JSON.stringify(config).includes("wayirun-dev")||JSON.stringify(config).includes("unopenedparachute.workers.dev"))throw Error("Deployment target must remain the reviewed WAYiRUN production Worker and isolated resources.");
}
