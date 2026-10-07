export type AppEnvironment = "development" | "production";
export type WriteMode = "normal" | "frozen";
export type RuntimeEnvironment = Readonly<{ name: AppEnvironment; publicOrigin: string; googleWebClientId: string;
  googleAndroidClientId: string; locationLookupUrl: string; weatherForecastUrl: string; weatherArchiveUrl: string; writeMode: WriteMode }>;
const DEV_ORIGIN="https://wayirun-dev.unopenedparachute.workers.dev",PROD_ORIGIN="https://wayirun.slopcopy.com";
const DEV_GOOGLE_CLIENT_IDS=new Set(["933230558080-ko4r7v0kmhip4i0n7u32diaimv1in73q.apps.googleusercontent.com","933230558080-8o82hopmd4ibnt2fllqpr8252lg3q44t.apps.googleusercontent.com"]);
const url=(value:unknown,label:string)=>{if(typeof value!=="string")throw Error(`${label}_missing`);const parsed=new URL(value);
 if(parsed.protocol!=="https:"||parsed.username||parsed.password||parsed.hash)throw Error(`${label}_invalid`);return parsed.toString().replace(/\/$/,"");};
const required=(value:unknown,label:string)=>{if(typeof value!=="string"||!value.trim()||/^__.+__$/.test(value))throw Error(`${label}_invalid`);return value;};
export function resolveEnvironment(env:Record<string,unknown>):RuntimeEnvironment{
 if(env.APP_ENV!=="development"&&env.APP_ENV!=="production")throw Error("app_env_invalid");
 const name=env.APP_ENV,publicOrigin=url(env.PUBLIC_ORIGIN,"public_origin"),expected=name==="development"?DEV_ORIGIN:PROD_ORIGIN;
 if(publicOrigin!==expected)throw Error("environment_origin_mismatch");
 const googleWebClientId=required(env.GOOGLE_WEB_CLIENT_ID,"google_web_client_id"),googleAndroidClientId=required(env.GOOGLE_ANDROID_CLIENT_ID,"google_android_client_id");
 const locationLookupUrl=url(env.LOCATION_LOOKUP_URL,"location_lookup_url"),weatherForecastUrl=url(env.WEATHER_FORECAST_URL,"weather_forecast_url"),weatherArchiveUrl=url(env.WEATHER_ARCHIVE_URL,"weather_archive_url");
 if(env.WRITE_MODE!=="normal"&&env.WRITE_MODE!=="frozen")throw Error("write_mode_invalid");
 const identifiers=[googleWebClientId,googleAndroidClientId,locationLookupUrl,weatherForecastUrl,weatherArchiveUrl];
 if(name==="production"&&(identifiers.some(v=>v.includes("wayirun-dev"))||DEV_GOOGLE_CLIENT_IDS.has(googleWebClientId)||DEV_GOOGLE_CLIENT_IDS.has(googleAndroidClientId)))throw Error("development_identifier_in_production");
 if(name==="development"&&identifiers.some(v=>v.includes("wayirun.slopcopy.com")))throw Error("production_identifier_in_development");
 return Object.freeze({name,publicOrigin,googleWebClientId,googleAndroidClientId,locationLookupUrl,weatherForecastUrl,weatherArchiveUrl,writeMode:env.WRITE_MODE});
}
export const environmentOrigins=Object.freeze({development:DEV_ORIGIN,production:PROD_ORIGIN});
export const providerDefaults=Object.freeze({location:"https://nominatim.openstreetmap.org/reverse",forecast:"https://api.open-meteo.com/v1/forecast",archive:"https://archive-api.open-meteo.com/v1/archive"});
