export function assertDevelopmentTarget(config) {
  const database = config.d1_databases;
  if (config.name !== "wayirun-dev" || config.vars?.APP_ENV !== "development" ||
      config.account_id !== "6bf560a8b86852196c9898023e3b8d6b" ||
      config.routes || config.route || config.env || database?.length !== 1 ||
      database[0].binding !== "DB" || database[0].database_name !== "wayirun-dev-db" ||
      database[0].database_id !== "04bf8339-386b-4a03-80d7-12b4d1f99ffb") {
    throw new Error("Deployment target must remain the verified WAYiRUN development Worker and database.");
  }
}
