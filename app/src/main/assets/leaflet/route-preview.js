(function(){
'use strict';
const status=document.getElementById('status');
try {
 const map=L.map('map',{preferCanvas:true});
 L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap'}).addTo(map);
 const route=L.featureGroup().addTo(map);
 const text=s=>{const e=document.createElement('span');e.textContent=s;return e.innerHTML;};
 const canStreetView=window.AndroidBridge&&typeof window.AndroidBridge.openStreetView==='function';
 const bounds=[];
 for(let i=1;i<stops.length;i++){
  const t=stops.length>1?i/(stops.length-1):0;
  const col=lerp('#2563eb','#ff8c42',t);
  L.polyline([[stops[i-1].latitude,stops[i-1].longitude],[stops[i].latitude,stops[i].longitude]],{color:col,weight:4,opacity:0.9}).addTo(route);
 }
 stops.forEach((s,i)=>{
  const t=stops.length>1?i/(stops.length-1):0;
  const col=lerp('#2563eb','#ff8c42',t);
  const label='<strong>#'+(i+1)+' '+text(s.house_number+' '+s.street)+'</strong>'+(canStreetView?'<br><button class="sv-btn" data-lat="'+s.latitude+'" data-lon="'+s.longitude+'" style="margin-top:6px;padding:6px 10px;border:1px solid #2563eb;border-radius:5px;background:#2563eb;color:#fff;font-weight:600">Open Street View</button>':'');
  const marker=L.circleMarker([s.latitude,s.longitude],{radius:6,color:'#fff',weight:2,fillOpacity:1,fillColor:col}).addTo(route).bindPopup(label);
  bounds.push([s.latitude,s.longitude]);
 });
 // Every non-walked lead is shown for reference, not part of the walk -- sold/already-serviced get
 // a bigger star (sold gold, already-serviced blue) so a sale stands out at a glance, same as the
 // office web map; every other status (do-not-call, not-owner, etc.) still gets a small colored dot
 // instead of being invisible here, matching exactly what the web route map shows.
 const STATUS_COLORS={pending:'#2563eb',sold:'#eab308',not_interested:'#ef4444',not_home:'#9ca3af',do_not_call:'#111827',callback:'#a855f7',already_serviced:'#3b82f6',not_owner:'#f97316',other:'#6b7280'};
 const STATUS_LABELS={pending:'Pending',sold:'Sold',not_interested:'Not interested',not_home:'Not home',do_not_call:'Do not call',callback:'Callback',already_serviced:'Already had service',not_owner:'Not the owner',other:'Other'};
 const STAR_STATUSES={sold:30,already_serviced:20};
 function starMarker(s,label,color,size){
  L.marker([s.latitude,s.longitude],{icon:L.divIcon({className:'',html:'<span style="font-size:'+size+'px;line-height:'+(size+4)+'px;display:block;text-align:center;color:'+color+';text-shadow:0 0 3px #000,0 0 3px #000">★</span>',iconSize:[size+4,size+4],iconAnchor:[(size+4)/2,(size+4)/2]})}).addTo(route).bindPopup(label);
  bounds.push([s.latitude,s.longitude]);
 }
 (typeof unrouted!=='undefined'?unrouted:[]).forEach(s=>{
  const label='<strong>'+text(STATUS_LABELS[s.status]||s.status)+'</strong><br>'+text(s.house_number+' '+s.street)+(canStreetView?'<br><button class="sv-btn" data-lat="'+s.latitude+'" data-lon="'+s.longitude+'" style="margin-top:6px;padding:6px 10px;border:1px solid #2563eb;border-radius:5px;background:#2563eb;color:#fff;font-weight:600">Open Street View</button>':'');
  if(STAR_STATUSES[s.status]){
   starMarker(s,'<strong>★ '+text(STATUS_LABELS[s.status]||s.status)+'</strong><br>'+text(s.house_number+' '+s.street)+(canStreetView?'<br><button class="sv-btn" data-lat="'+s.latitude+'" data-lon="'+s.longitude+'" style="margin-top:6px;padding:6px 10px;border:1px solid #2563eb;border-radius:5px;background:#2563eb;color:#fff;font-weight:600">Open Street View</button>':''),STATUS_COLORS[s.status],STAR_STATUSES[s.status]);
  }else{
   const marker=L.circleMarker([s.latitude,s.longitude],{radius:3.5,color:'#fff',weight:1,fillOpacity:1,fillColor:STATUS_COLORS[s.status]||'#6b7280'}).addTo(route).bindPopup(label);
   bounds.push([s.latitude,s.longitude]);
  }
 });
 if(canStreetView)map.on('popupopen',e=>{const btn=e.popup.getElement()&&e.popup.getElement().querySelector('.sv-btn');if(btn)btn.addEventListener('click',()=>{window.AndroidBridge.openStreetView(Number(btn.dataset.lat),Number(btn.dataset.lon));});});
 function lerp(c1,c2,t){const a=parseInt(c1.slice(1),16),b=parseInt(c2.slice(1),16);const ar=(a>>16)&255,ag=(a>>8)&255,ab=a&255;const br=(b>>16)&255,bg=(b>>8)&255,bb=b&255;const r=Math.round(ar+(br-ar)*t),g=Math.round(ag+(bg-ag)*t),bl=Math.round(ab+(bb-ab)*t);return 'rgb('+r+','+g+','+bl+')';}
 if(bounds.length>1)map.fitBounds(bounds,{padding:[20,20],maxZoom:17});else if(bounds.length)map.setView(bounds[0],17);
 window.addEventListener('resize',()=>map.invalidateSize());setTimeout(()=>map.invalidateSize(),150);

 // Play: flies the same live map from stop to stop, same idea as the office web version.
 let idx=0,playing=false,timer=null,cursor=null;
 const playBtn=document.getElementById('play'),caption=document.getElementById('caption');
 function goTo(i){
  const s=stops[i];
  if(cursor)map.removeLayer(cursor);
  cursor=L.circleMarker([s.latitude,s.longitude],{radius:10,color:'#ff8c42',weight:3,fillColor:'#fff',fillOpacity:1}).addTo(route);
  map.flyTo([s.latitude,s.longitude],18,{duration:0.6});
  caption.textContent='#'+(i+1)+' / '+stops.length+' — '+s.house_number+' '+s.street;
 }
 function scheduleNext(){
  clearTimeout(timer);
  if(!playing)return;
  if(idx>=stops.length-1){playing=false;playBtn.textContent='▶ Replay';return;}
  timer=setTimeout(()=>{idx++;goTo(idx);scheduleNext();},1400);
 }
 playBtn.addEventListener('click',()=>{
  if(playing){playing=false;clearTimeout(timer);playBtn.textContent='▶ Play';}
  else if(idx<stops.length-1||idx===0){if(idx>=stops.length-1)idx=0;playing=true;playBtn.textContent='❚❚ Pause';goTo(idx);scheduleNext();}
 });
 status.textContent=stops.length+' stop'+(stops.length===1?'':'s')+' · tap a dot for details'+(canStreetView?' and Street View':'');

 // Called from Android (the "My Location" button) -- GPS access lives on that side, this just
 // drops a marker and jumps the map there so the rep never has to pan around looking for himself.
 let myLocationMarker=null;
 window.locateMe=function(lat,lon){
  if(myLocationMarker)map.removeLayer(myLocationMarker);
  myLocationMarker=L.circleMarker([lat,lon],{radius:9,color:'#fff',fillColor:'#16a34a',fillOpacity:1,weight:3}).addTo(map).bindPopup('You are here').openPopup();
  map.flyTo([lat,lon],18,{duration:0.6});
 };
}catch(e){status.textContent='Unable to draw route map: '+e.message;}
})();
