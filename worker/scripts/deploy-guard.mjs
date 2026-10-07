export function assertDevelopmentTarget(config) {
  const database = config.d1_databases;
  const expectedRates={RUN_RATE_LIMIT:"786531903",RUN_TOTAL_LIMIT:"786531904",AUTH_RATE_LIMIT:"786531901",AUTH_TOTAL_LIMIT:"786531902"};
  const rates=Object.fromEntries((config.ratelimits??[]).map(x=>[x.name,x.namespace_id]));
  if (config.name !== "wayirun-dev" || config.vars?.APP_ENV !== "development" ||
      config.account_id !== "6bf560a8b86852196c9898023e3b8d6b" ||
      config.workers_dev!==true || config.vars?.PUBLIC_ORIGIN!=="https://wayirun-dev.unopenedparachute.workers.dev" ||
      !["normal","frozen"].includes(config.vars?.WRITE_MODE) || config.routes || config.route || config.env || database?.length !== 1 ||
      config.vars?.GOOGLE_WEB_CLIENT_ID!=="933230558080-ko4r7v0kmhip4i0n7u32diaimv1in73q.apps.googleusercontent.com" ||
      config.vars?.GOOGLE_ANDROID_CLIENT_ID!=="933230558080-8o82hopmd4ibnt2fllqpr8252lg3q44t.apps.googleusercontent.com" ||
      config.vars?.LOCATION_LOOKUP_URL!=="https://nominatim.openstreetmap.org/reverse" ||
      config.vars?.WEATHER_FORECAST_URL!=="https://api.open-meteo.com/v1/forecast" || config.vars?.WEATHER_ARCHIVE_URL!=="https://archive-api.open-meteo.com/v1/archive" ||
      database[0].binding !== "DB" || database[0].database_name !== "wayirun-dev-db" ||
      database[0].database_id !== "04bf8339-386b-4a03-80d7-12b4d1f99ffb" ||
      config.ratelimits?.length!==4 || Object.entries(expectedRates).some(([name,id])=>rates[name]!==id) ||
      Object.values(config.vars??{}).some(value=>String(value).includes("wayirun.slopcopy.com"))) {
    throw new Error("Deployment target must remain the verified WAYiRUN development Worker and database.");
  }
}
