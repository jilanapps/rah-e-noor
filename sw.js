/* Rah-e-Noor service worker — offline-first app shell.
   Caches the core files so the app opens without internet.
   Live data (prayer API, Quran, Hadith, PDFs) still needs internet. */
const CACHE='rahenoor-v2-14';
const SHELL=[
  './','./index.html','./book-toc.js','./manifest.json',
  './privacy-policy.html','./icon-192.png','./icon-512.png',
  './js/pdf.min.js','./js/pdf.worker.min.js'
];
self.addEventListener('install',e=>{
  e.waitUntil(caches.open(CACHE).then(c=>c.addAll(SHELL)).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',e=>{
  e.waitUntil(caches.keys().then(ks=>Promise.all(
    ks.filter(k=>k!==CACHE).map(k=>caches.delete(k))
  )).then(()=>self.clients.claim()));
});
self.addEventListener('fetch',e=>{
  const u=new URL(e.request.url);
  // Never cache API calls or external sites — always go to network
  if(/aladhan|alquran|bigdatacloud|open-elevation|archive\.org|everyayah|cdnjs|jsdelivr|wa\.me/.test(u.href)){
    return;
  }
  e.respondWith(
    fetch(e.request).then(r=>{
      const copy=r.clone();
      caches.open(CACHE).then(c=>c.put(e.request,copy)).catch(()=>{});
      return r;
    }).catch(()=>caches.match(e.request).then(m=>m||caches.match('./index.html')))
  );
});
