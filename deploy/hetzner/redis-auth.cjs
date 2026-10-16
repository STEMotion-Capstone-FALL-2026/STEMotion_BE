'use strict';
// Deployment compatibility adapter only. No source edit in the frozen worker.
// Its {host,port} connections inherit ioredis defaults, including BullMQ duplicates.
const {createRequire} = require('node:module');
const workerRequire = createRequire('/workspace/STEMotion_RENDER/package.json');
const password = process.env.REDIS_PASSWORD;
if (!password) throw new Error('Deployment requires REDIS_PASSWORD');
const clients = new Set([
  workerRequire('ioredis'),
  createRequire(workerRequire.resolve('bullmq'))('ioredis'),
]);
for (const Redis of clients) {
  if (!Redis.defaultOptions || !Object.isExtensible(Redis.defaultOptions)) {
    throw new Error('Unsupported ioredis version: retest deployment Redis auth bootstrap');
  }
  Redis.defaultOptions.password = password;
}
