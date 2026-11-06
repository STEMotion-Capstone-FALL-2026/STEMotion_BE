import {defineConfig} from "file:///C:/Users/LeTran/FPT%20University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE/node_modules/vitest/dist/config.js";
import react from "file:///C:/Users/LeTran/FPT%20University/2026_FALL/STEMotion/.audit/stem-phase-2026-11-05/phase-04/STEMotion_FE/node_modules/%40vitejs/plugin-react/dist/index.js";
export default defineConfig({
 root:"C:\\Users\\LeTran\\FPT University\\2026_FALL\\STEMotion\\.audit\\stem-phase-2026-11-05\\phase-04\\STEMotion_FE",envDir:false,cacheDir:"C:\\Users\\LeTran\\FPT University\\2026_FALL\\STEMotion\\.audit\\stem-phase-2026-11-05\\phase-03\\STEMotion_BE\\.audit\\be-blocker-fix\\vite-cache",
 plugins:[react()],resolve:{alias:{'@':"C:\\Users\\LeTran\\FPT University\\2026_FALL\\STEMotion\\.audit\\stem-phase-2026-11-05\\phase-04\\STEMotion_FE\\src"}},
 server:{host:'127.0.0.1',open:false,fs:{allow:["C:\\Users\\LeTran\\FPT University\\2026_FALL\\STEMotion\\.audit\\stem-phase-2026-11-05\\phase-04\\STEMotion_FE", "C:\\Users\\LeTran\\FPT University\\2026_FALL\\STEMotion\\.audit\\stem-phase-2026-11-05\\phase-03\\STEMotion_BE\\docs\\stem\\phases\\phase-04\\be-blocker-fix", "C:\\Users\\LeTran\\FPT University\\2026_FALL\\STEMotion\\STEMotion_FE\\node_modules"]}},
 test:{environment:'jsdom',globals:true,cache:false,fsModuleCache:false,
  include:['src/components/stem/StemFlow.p4-live.test.tsx'],maxWorkers:1,fileParallelism:false,
  setupFiles:["C:\\Users\\LeTran\\FPT University\\2026_FALL\\STEMotion\\.audit\\stem-phase-2026-11-05\\phase-03\\STEMotion_BE\\docs\\stem\\phases\\phase-04\\be-blocker-fix\\observe-live-http.mjs"],reporters:['default','json'],outputFile:{json:"C:\\Users\\LeTran\\FPT University\\2026_FALL\\STEMotion\\.audit\\stem-phase-2026-11-05\\phase-03\\STEMotion_BE\\docs\\stem\\phases\\phase-04\\be-blocker-fix\\p4-q10-results.json"}}
});
