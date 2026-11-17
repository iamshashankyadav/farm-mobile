import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  canRate, distanceLabel, etaLabel, haversineKm, phaseOf, project, seenLabel, shouldPoll,
  hasPartnerPosition,
} from '../src/tracking/tracking.ts';

const now = new Date('2026-11-18T10:00:00Z');

test('the further of order and logistics wins, a cancelled order stays cancelled', () => {
  assert.equal(phaseOf('READY_FOR_DISPATCH', null), 'PREPARING');
  assert.equal(phaseOf('POOLED', null), 'POOLED');
  assert.equal(phaseOf('POOLED', { orderId: 'o', status: 'IN_TRANSIT' }), 'IN_TRANSIT');
  assert.equal(phaseOf('IN_TRANSIT', { orderId: 'o', status: 'POOLED' }), 'IN_TRANSIT');
  assert.equal(phaseOf('COMPLETED', null), 'DELIVERED');
  assert.equal(phaseOf('CANCELLED', { orderId: 'o', status: 'IN_TRANSIT' }), 'CANCELLED');
});

test('rating is open for a completed, unrated order only', () => {
  assert.equal(canRate('COMPLETED', false), true);
  assert.equal(canRate('COMPLETED', true), false);
  assert.equal(canRate('DELIVERED', false), false); // COMPLETED comes right after DELIVERED
  assert.equal(canRate('CANCELLED', false), false);
});

test('the track is polled until the order is delivered or cancelled', () => {
  assert.equal(shouldPoll('PREPARING'), true);
  assert.equal(shouldPoll('IN_TRANSIT'), true);
  assert.equal(shouldPoll('DELIVERED'), false);
  assert.equal(shouldPoll('CANCELLED'), false);
});

test('a partner position needs both coordinates', () => {
  assert.equal(hasPartnerPosition(null), false);
  assert.equal(hasPartnerPosition({ orderId: 'o', partnerLat: 26.9 }), false);
  assert.equal(hasPartnerPosition({ orderId: 'o', partnerLat: 26.9, partnerLng: 75.8 }), true);
  assert.equal(hasPartnerPosition({ orderId: 'o', partnerLat: 0, partnerLng: 0 }), true);
});

test('distance between two points in Jaipur', () => {
  const km = haversineKm({ lat: 26.9124, lng: 75.7873 }, { lat: 26.85, lng: 75.8 });
  assert.ok(km > 6.5 && km < 7.5, String(km));
  assert.equal(distanceLabel(0.42), '420 m');
  assert.equal(distanceLabel(3.456), '3.5 km');
});

test('eta and last seen labels', () => {
  assert.equal(etaLabel(null, now), '');
  assert.equal(etaLabel('2026-11-18T10:12:00Z', now), 'in 12 min');
  assert.equal(etaLabel('2026-11-18T11:30:00Z', now), 'in 1 h 30 min');
  assert.equal(etaLabel('2026-11-18T09:50:00Z', now), 'due now');
  assert.equal(seenLabel('2026-11-18T09:59:40Z', now), 'just now');
  assert.equal(seenLabel('2026-11-18T09:57:00Z', now), '3 min ago');
  assert.equal(seenLabel(undefined, now), '');
});

test('projection keeps points inside the canvas, north up and east right', () => {
  const markers = project(
    [
      { key: 'partner', at: { lat: 26.85, lng: 75.8 } },
      { key: 'drop', at: { lat: 26.9124, lng: 75.7873 } },
    ],
    300,
    200,
  );
  const [partner, drop] = markers;
  assert.ok(drop.y < partner.y, 'the drop is further north, so higher up');
  assert.ok(partner.x > drop.x, 'the partner is further east, so more to the right');
  for (const m of markers) {
    assert.ok(m.x >= 0 && m.x <= 300 && m.y >= 0 && m.y <= 200);
  }
});

test('projection of one point or no point is safe', () => {
  assert.deepEqual(project([], 300, 200), []);
  const [only] = project([{ key: 'a', at: { lat: 1, lng: 2 } }], 300, 200);
  assert.deepEqual([only.x, only.y], [150, 100]);
});
