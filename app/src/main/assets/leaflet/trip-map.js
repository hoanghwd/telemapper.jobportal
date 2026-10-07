(function () {
    'use strict';
    const status = document.getElementById('status');
    try {
        const map = L.map('map', {preferCanvas: true});
        // The tile server and credit line come from app/app-config.properties (injected by the app as APP_CONFIG), not from here.
        L.tileLayer(APP_CONFIG.tileUrl, {
            maxZoom: 19,
            attribution: APP_CONFIG.attribution
        }).addTo(map);
        const route = L.featureGroup().addTo(map), area = L.featureGroup().addTo(map);
        const text = s => {
            const e = document.createElement('span');
            e.textContent = s;
            return e.innerHTML;
        };
        scheduled.forEach(s => {
            const label = 'Scheduled: ' + text(s.name || '');
            if (s.location_type === 'territory' && Array.isArray(s.boundary) && s.boundary.length >= 3) L.polygon(s.boundary, {
                color: '#16a34a',
                weight: 3,
                dashArray: '6,4',
                fillOpacity: .08
            }).addTo(area).bindPopup(label); else if (s.latitude != null && s.longitude != null) L.circleMarker([s.latitude, s.longitude], {
                radius: 11,
                color: '#7c3aed',
                fillColor: '#fff',
                fillOpacity: 1,
                weight: 4
            }).addTo(area).bindPopup(label);
        });
        if (points.length > 1) L.polyline(points.map(p => [p.latitude, p.longitude]), {
            color: '#2563eb',
            weight: 3
        }).addTo(route);
        let first;
        const canStartDoor = window.AndroidBridge && typeof window.AndroidBridge.startDoor === 'function';
        points.forEach((p, i) => {
            const uncertain = p.accuracy_m > 100 || Number(p.is_mock) === 1;
            const label = '<strong>Recorded position ' + (i + 1) + ' of ' + points.length + '</strong><br>' + text(p.captured_iso || p.captured_utc || '') + '<br>' + Number(p.latitude).toFixed(6) + ', ' + Number(p.longitude).toFixed(6) + '<br>Accuracy: ±' + Math.round(p.accuracy_m) + ' m'
                + (canStartDoor && p.point_id ? '<br><button class="start-door-btn" data-point="' + p.point_id + '" data-lat="' + p.latitude + '" data-lon="' + p.longitude + '" style="margin-top:6px;padding:6px 10px;border:1px solid #2563eb;border-radius:5px;background:#2563eb;color:#fff;font-weight:600">I\'m at the door</button>' : '');
            const marker = L.circleMarker([p.latitude, p.longitude], {
                radius: 6,
                color: '#fff',
                fillColor: uncertain ? '#d97706' : '#2563eb',
                fillOpacity: 1,
                weight: 2
            }).addTo(route).bindPopup(label);
            if (i === 0) first = L.marker([p.latitude, p.longitude], {
                icon: L.divIcon({
                    className: 'start-icon',
                    html: '<span>▶</span>',
                    iconSize: [38, 38],
                    iconAnchor: [19, 19]
                }), zIndexOffset: 1000
            }).addTo(route).bindTooltip('First recorded position').bindPopup(label);
        });
        if (canStartDoor) map.on('popupopen', e => {
            const btn = e.popup.getElement() && e.popup.getElement().querySelector('.start-door-btn');
            if (btn) btn.addEventListener('click', () => {
                window.AndroidBridge.startDoor(Number(btn.dataset.point), Number(btn.dataset.lat), Number(btn.dataset.lon));
                map.closePopup();
            });
        });
        // Door outcomes for the day, when present — Sold gets a big gold star, everything else a small
        // colored dot, same language as the office web app's own disposition map.
        const dispositionColors = {
            already_serviced: '#3b82f6',
            not_interested: '#ef4444',
            not_owner: '#f97316',
            callback: '#a855f7',
            do_not_call: '#111827',
            not_home: '#9ca3af'
        };
        const dispositionLabels = {
            already_serviced: 'Already Had Service',
            not_interested: 'Not Interested',
            not_owner: 'Not the Owner',
            callback: 'Come Back Another Time',
            do_not_call: 'Do Not Call',
            not_home: 'Not Home'
        };
        const outcomes = L.featureGroup().addTo(map);
        let activeMarker;
        (typeof dispositions !== 'undefined' ? dispositions : []).forEach(dd => {
            if (dd.latitude == null || dd.longitude == null) return;
            const addr = text(dd.address || 'an unnamed door');
            if (dd.in_progress) {
                activeMarker = L.circleMarker([dd.latitude, dd.longitude], {
                    radius: 9,
                    color: '#fff',
                    fillColor: '#2563eb',
                    fillOpacity: 1,
                    weight: 3
                }).addTo(outcomes).bindPopup('<strong>In progress</strong><br>' + addr);
            } else if (dd.status === 'sold') {
                L.marker([dd.latitude, dd.longitude], {
                    icon: L.divIcon({
                        className: '',
                        html: '<span aria-hidden="true" style="font-size:30px;line-height:38px;display:block;text-align:center;color:#eab308;text-shadow:0 0 3px #000,0 0 3px #000">★</span>',
                        iconSize: [38, 38],
                        iconAnchor: [19, 19]
                    }), zIndexOffset: 900
                }).addTo(outcomes).bindPopup('<strong>Sold</strong><br>' + addr);
            } else {
                L.circleMarker([dd.latitude, dd.longitude], {
                    radius: 7,
                    color: '#fff',
                    fillColor: dispositionColors[dd.status] || '#6b7280',
                    fillOpacity: 1,
                    weight: 2
                }).addTo(outcomes).bindPopup('<strong>' + text(dispositionLabels[dd.status] || dd.status) + '</strong><br>' + addr);
            }
        });

        function fit(group) {
            if (group.getLayers().length) map.fitBounds(group.getBounds(), {padding: [25, 25], maxZoom: 16});
        }

        document.getElementById('fit').disabled = !points.length;
        document.getElementById('area').disabled = !area.getLayers().length;
        // "Where am I": the Android side reads a fresh position and calls window.locateMe (below) with it.
        document.getElementById('where').onclick = () => {
            if (window.AndroidBridge && typeof window.AndroidBridge.whereAmI === 'function') window.AndroidBridge.whereAmI();
        };
        // "Refresh": the Android side reloads the screen's data, which redraws this map.
        document.getElementById('refresh').onclick = () => {
            if (window.AndroidBridge && typeof window.AndroidBridge.refresh === 'function') window.AndroidBridge.refresh();
        };
        document.getElementById('fit').onclick = () => fit(route);
        document.getElementById('area').onclick = () => fit(area);
        // The D2D screen also offers "Preview the route" in this toolbar (its bridge has previewRoute); My Trip does not.
        if (window.AndroidBridge && typeof window.AndroidBridge.previewRoute === 'function') {
            const preview = document.getElementById('preview');
            preview.style.display = 'flex';
            preview.onclick = () => window.AndroidBridge.previewRoute();
        }
        // A door in progress is where the rep is standing right now — jump straight there, same as the
        // "You are here" location screen, instead of zooming out to fit the whole day's trail.
        if (activeMarker) {
            map.setView(activeMarker.getLatLng(), 17);
            activeMarker.openPopup();
        } else if (area.getLayers().length) fit(area); else if (points.length) fit(route); else map.setView([20, 0], 2);
        const soldCount = (typeof dispositions !== 'undefined' ? dispositions : []).filter(dd => dd.status === 'sold').length;
        status.textContent = (points.length ? 'Blue: GPS trail · Green: assigned territory' : 'No recorded positions for this date.') + (soldCount ? ' · ★ ' + soldCount + ' sold today' : '');
        // Called from Android (the "Where am I" button) -- GPS access lives on that side; this drops a green marker at the
        // rep's current position and flies there so he never has to hunt for himself on the map.
        let myLocationMarker = null;
        window.locateMe = function (lat, lon) {
            if (myLocationMarker) map.removeLayer(myLocationMarker);
            myLocationMarker = L.circleMarker([lat, lon], {
                radius: 10,
                color: '#fff',
                fillColor: '#16a34a',
                fillOpacity: 1,
                weight: 3
            }).addTo(map).bindPopup('<strong>You are here</strong>').openPopup();
            map.flyTo([lat, lon], 18, {duration: 0.6});
        };
        window.addEventListener('resize', () => map.invalidateSize());
        setTimeout(() => map.invalidateSize(), 150);
    } catch (e) {
        status.textContent = 'Unable to draw trip map: ' + e.message;
    }
})();