(function () {
    'use strict';
    // The Jobs map: where the driver is, the orders he already holds (numbered stops along the suggested route) and the waiting jobs he may
    // take (a red pin at the drop-off, a red ring at the pickup). jobsData is the server's "dispatch/available" answer, put on the page by JobsMap.
    const status = document.getElementById('status');
    try {
        const map = L.map('map');
        // Lines and rings can only be added to a map that already has a view; fit() below moves it to where the jobs are.
        map.setView((jobsData && jobsData.position) || [20, 0], jobsData && jobsData.position ? 12 : 2);
        L.tileLayer(APP_CONFIG.tileUrl, {maxZoom: 19, attribution: APP_CONFIG.attribution}).addTo(map);
        const bridge = window.AndroidBridge || {};
        const all = L.featureGroup().addTo(map);
        const jobMarkers = {};
        const data = jobsData || {};
        const jobs = data.jobs || [], orders = data.orders || [];
        const route = data.route && data.route.stops ? data.route : null;

        // This screen has no territory or route preview.
        ['area', 'preview'].forEach(id => {
            const button = document.getElementById(id);
            if (button) button.style.display = 'none';
        });

        // A road route from the server (source 'road') is drawn along the roads; without one the old straight line is drawn.
        const roadLine = road => road && road.source === 'road' && Array.isArray(road.coordinates) && road.coordinates.length > 1 ? road.coordinates : null;
        const orderName = title => {
            const m = /-([A-Za-z0-9]+)$/.exec(title || '');
            return m ? m[1] : (title || '');
        };
        const line = (parent, tag, value, style) => {
            const e = document.createElement(tag);
            e.textContent = value;
            if (style) e.style.cssText = style;
            parent.appendChild(e);
            return e;
        };
        const fmt = n => Number(n) < 10 ? Number(n).toFixed(1) : String(Math.round(Number(n)));
        const stopIcon = (number, color) => L.divIcon({
            className: 'stop-icon',
            html: '<span style="background:' + color + '">' + number + '</span>',
            iconSize: [30, 30], iconAnchor: [15, 15]
        });
        const pinFor = color => L.divIcon({
            className: 'pin-icon',
            html: '<svg viewBox="0 0 24 32" width="28" height="36"><path d="M12 0C5.4 0 0 5.2 0 11.6 0 20 12 32 12 32s12-12 12-20.4C24 5.2 18.6 0 12 0z" fill="' + color + '" stroke="#fff" stroke-width="2"/><circle cx="12" cy="11.5" r="4.5" fill="#fff"/></svg>',
            iconSize: [28, 36], iconAnchor: [14, 36], popupAnchor: [0, -34]
        });

        const pinIcon = pinFor('#dc2626'), offeredPinIcon = pinFor('#d97706');

        // The popup of a waiting job: its order number, both addresses, how far, what it adds, and the button that takes it.
        // The pin (drop-off, where the order ends) and the ring (pickup) of one job open the same popup; `where` says which of the two he touched, so it never looks like one address on two places.
        const jobPopup = (job, where) => {
            const box = document.createElement('div');
            line(box, 'strong', orderName(job.title), 'font-size:15px');
            line(box, 'div', where === 'dropoff' ? 'You tapped the drop-off point' : 'You tapped the pickup point', 'margin-top:2px;font-size:12px;font-weight:700;color:' + (job.offered ? '#b45309' : '#dc2626'));
            line(box, 'div', 'Pick up: ' + job.pickup_address, 'margin-top:4px' + (where === 'dropoff' ? '' : ';font-weight:700'));
            line(box, 'div', 'Drop off: ' + job.dropoff_address, where === 'dropoff' ? 'font-weight:700' : '');
            const mins = n => Number(n) > 0 ? ' · ' + Number(n) + ' min' : '';
            line(box, 'div', fmt(job.miles_to_pickup) + ' mi' + mins(job.minutes_to_pickup) + ' to the pickup · trip ' + fmt(job.trip_miles) + ' mi' + mins(job.trip_minutes), 'margin-top:4px;color:#475569');
            if (job.pay !== null && job.pay !== undefined && job.pay !== '') line(box, 'div', 'Pay: $' + job.pay, 'margin-top:4px;font-weight:700;color:#15803d');
            if (data.holding > 0) line(box, 'div', 'Adds about ' + fmt(job.extra_miles) + ' mi · ' + job.extra_minutes + ' min to your route', 'color:#475569');
            // An offered order is accepted (answered as the offer); a waiting one he can take is claimed. Both read "Accept".
            if ((job.offered ? job.offer_id : data.can_take && job.can_take !== false) && typeof bridge.takeJob === 'function') {
                // The ring is only where the order is collected: the job is accepted from its drop-off (the pin), never from the ring.
                if (where !== 'dropoff') {
                    line(box, 'div', 'To accept, tap the pin (the delivery address).', 'margin-top:8px;font-weight:700;color:#475569');
                    return box;
                }
                const take = line(box, 'button', 'Accept', 'margin-top:8px;width:100%;padding:10px;border:0;border-radius:6px;background:#2563eb;color:#fff;font-weight:600');
                take.addEventListener('click', () => {
                    bridge.takeJob(Number(job.request_id));
                    map.closePopup();
                });
            }
            return box;
        };

        // The orders he holds: numbered along the suggested route when there is one, otherwise plain pickup / drop-off dots.
        const names = {};
        orders.forEach(o => names[o.offer_id] = orderName(o.title));
        if (route && route.stops.length) {
            const here = data.position ? [[data.position[0], data.position[1]]] : [];
            L.polyline(roadLine(route.road) || here.concat(route.stops.map(s => [s.lat, s.lon])), {color: '#2563eb', weight: 4}).addTo(all);
            route.stops.forEach((s, i) => {
                const pickup = s.type === 'pickup';
                L.marker([s.lat, s.lon], {icon: stopIcon(i + 1, pickup ? '#2563eb' : '#16a34a'), zIndexOffset: 500})
                    .addTo(all).bindPopup('<strong>' + (i + 1) + '. ' + (pickup ? 'Pick up' : 'Deliver') + '</strong><br>' + (names[String(s.order)] || '').replace(/</g, '&lt;'));
            });
        } else orders.forEach(o => {
            L.circleMarker([o.pickup_lat, o.pickup_lon], {radius: 9, color: '#fff', fillColor: '#2563eb', fillOpacity: 1, weight: 3}).addTo(all).bindPopup('Pick up ' + orderName(o.title).replace(/</g, '&lt;'));
            L.circleMarker([o.dropoff_lat, o.dropoff_lon], {radius: 9, color: '#fff', fillColor: '#16a34a', fillOpacity: 1, weight: 3}).addTo(all).bindPopup('Deliver ' + orderName(o.title).replace(/</g, '&lt;'));
        });

        jobs.forEach(job => {
            const pin = L.marker([job.dropoff_lat, job.dropoff_lon], {icon: job.offered ? offeredPinIcon : pinIcon}).addTo(all).bindPopup(() => jobPopup(job, 'dropoff'));
            const pickupRing = L.circleMarker([job.pickup_lat, job.pickup_lon], {radius: 8, color: job.offered ? '#d97706' : '#dc2626', fillColor: '#fff', fillOpacity: 1, weight: 4}).addTo(all).bindPopup(() => jobPopup(job, 'pickup'));
            const path = roadLine(job.road) || [[job.pickup_lat, job.pickup_lon], [job.dropoff_lat, job.dropoff_lon]];
            L.polyline(path, {color: job.offered ? '#d97706' : '#dc2626', weight: 3, dashArray: '6,6', opacity: .8}).addTo(all);
            jobMarkers[job.request_id] = {pin: pin, bounds: L.latLngBounds(path)};
        });

        let me = null;
        const showMe = (lat, lon, open) => {
            if (me) map.removeLayer(me);
            me = L.circleMarker([lat, lon], {radius: 10, color: '#fff', fillColor: '#16a34a', fillOpacity: 1, weight: 3}).addTo(map).bindPopup('<strong>You are here</strong>');
            if (open) me.openPopup();
        };
        if (data.position) showMe(data.position[0], data.position[1], false);

        const fit = () => {
            const box = L.featureGroup([all]);
            if (data.position) box.addLayer(L.marker(data.position));
            if (all.getLayers().length) map.fitBounds(box.getBounds(), {padding: [30, 30], maxZoom: 15});
            else if (data.position) map.setView(data.position, 13);
            else map.setView([20, 0], 2);
        };
        fit();

        document.getElementById('fit').onclick = fit;
        document.getElementById('where').onclick = () => {
            if (typeof bridge.whereAmI === 'function') bridge.whereAmI();
        };
        document.getElementById('refresh').onclick = () => {
            if (typeof bridge.refresh === 'function') bridge.refresh();
        };
        // The Android side reads a fresh position and calls this (the "Where am I" button).
        window.locateMe = (lat, lon) => {
            showMe(lat, lon, true);
            map.flyTo([lat, lon], 15, {duration: 0.6});
        };
        // A job picked in the list below the map: zoom to it and open its popup.
        window.focusJob = id => {
            const found = jobMarkers[id];
            if (!found) return;
            map.flyToBounds(found.bounds, {padding: [60, 60], maxZoom: 15, duration: 0.5});
            found.pin.openPopup();
        };

        const offeredCount = jobs.filter(j => j.offered).length, takeableCount = jobs.length - offeredCount;
        // The legend: what the pin and the ring of a job mean (red = a job he can take, amber = offered to him right now).
        const legendPin = color => '<svg viewBox="0 0 24 32" width="12" height="16" style="vertical-align:middle"><path d="M12 0C5.4 0 0 5.2 0 11.6 0 20 12 32 12 32s12-12 12-20.4C24 5.2 18.6 0 12 0z" fill="' + color + '"/><circle cx="12" cy="11.5" r="4.5" fill="#fff"/></svg>';
        const legendRing = color => '<svg viewBox="0 0 16 16" width="14" height="14" style="vertical-align:middle"><circle cx="8" cy="8" r="5.5" fill="#fff" stroke="' + color + '" stroke-width="3"/></svg>';
        if (jobs.length) {
            const color = takeableCount ? '#dc2626' : '#d97706';
            const parts = [legendPin(color) + ' Pin = delivery address', legendRing(color) + ' Ring = pickup'];
            if (orders.length) parts.push('Numbers: your route');
            status.innerHTML = parts.join(' &nbsp;·&nbsp; ');
        } else status.textContent = orders.length ? 'Numbers: your route' : 'No jobs to show.';
        window.addEventListener('resize', () => map.invalidateSize());
        // The page may not have its final size when it first draws: once it does, fit everything again (unless he already moved the map).
        let touched = false, settled = false;
        map.on('dragstart zoomstart', () => { if (settled) touched = true; });
        setTimeout(() => { map.invalidateSize(); if (!touched) fit(); setTimeout(() => { settled = true; }, 800); }, 150);
    } catch (e) {
        status.textContent = 'Unable to draw the jobs map: ' + e.message;
    }
})();
