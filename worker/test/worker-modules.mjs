import { readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
export function workerModules() {
 const directory=new URL("../build/deploy/",import.meta.url);
 return [{type:"ESModule",path:fileURLToPath(new URL("index.js",directory))},
  ...readdirSync(directory).filter(name=>/\.(html|css|browserjs)$/.test(name)).map(name=>({type:"Text",path:fileURLToPath(new URL(name,directory))}))];
}
