import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  deliveryDates, parseChoiceError, shouldPollHarvest, stageOf, summarise,
} from '../src/harvest/harvest.ts';

const base = { id: 'o1', subtotal: 900, deliveryFee: 0, total: 900, status: 'PLACED' };

test('stage follows the harvest order', () => {
  assert.equal(stageOf(null), 'NOT_READY');
  assert.equal(stageOf({ ...base }), 'CHOOSE');
  assert.equal(stageOf({ ...base, harvestOption: null }), 'CHOOSE');
  assert.equal(stageOf({ ...base, status: 'READY_FOR_DISPATCH', harvestOption: 'DELIVERY' }), 'DELIVERY');
  assert.equal(stageOf({ ...base, status: 'DELIVERED', harvestOption: 'DELIVERY' }), 'DELIVERY');
  assert.equal(stageOf({ ...base, status: 'COMPLETED', harvestOption: 'BUYBACK' }), 'COMPLETED');
  assert.equal(stageOf({ ...base, status: 'CANCELLED' }), 'CANCELLED');
});

test('polling stops when the order is closed', () => {
  assert.equal(shouldPollHarvest('NOT_READY'), true);
  assert.equal(shouldPollHarvest('CHOOSE'), true);
  assert.equal(shouldPollHarvest('DELIVERY'), true);
  assert.equal(shouldPollHarvest('COMPLETED'), false);
  assert.equal(shouldPollHarvest('CANCELLED'), false);
});

test('summary of a delivery order before and after the balance', () => {
  const before = summarise({
    ...base, deliveryFee: 20, total: 920, balanceDue: 720, harvestOption: 'DELIVERY',
    payments: [{ kind: 'DEPOSIT', amount: 200, status: 'SUCCEEDED' }],
  });
  assert.deepEqual(before, { value: 900, deliveryFee: 20, depositPaid: 200, balancePaid: 0, balanceDue: 720, credit: 0 });
  const after = summarise({
    ...base, deliveryFee: 20, total: 920, balanceDue: 0, status: 'COMPLETED',
    payments: [
      { kind: 'DEPOSIT', amount: 200, status: 'SUCCEEDED' },
      { kind: 'BALANCE', amount: 720, status: 'SUCCEEDED' },
      { kind: 'BALANCE', amount: 720, status: 'FAILED' },
    ],
  });
  assert.equal(after.balancePaid, 720);
  assert.equal(after.balanceDue, 0);
});

test('summary of a buy-back shows the credit', () => {
  const s = summarise({
    ...base, status: 'COMPLETED', harvestOption: 'BUYBACK',
    payments: [
      { kind: 'DEPOSIT', amount: 200, status: 'SUCCEEDED' },
      { kind: 'REFUND', amount: 810, status: 'SUCCEEDED' },
    ],
  });
  assert.equal(s.credit, 810);
  assert.equal(summarise(base).depositPaid, 0);
});

test('delivery dates start tomorrow', () => {
  assert.deepEqual(deliveryDates(new Date(2026, 10, 18)), ['2026-11-19', '2026-11-20', '2026-11-21']);
  assert.deepEqual(deliveryDates(new Date(2026, 11, 30), 2), ['2026-12-31', '2027-01-01']);
});

test('error codes map to messages, and some ask for a refresh', () => {
  const chosen = parseChoiceError(409, { error: { code: 'HARVEST_OPTION_CHOSEN' } });
  assert.equal(chosen.refresh, true);
  assert.match(chosen.message, /already chose/);
  assert.match(parseChoiceError(422, { error: { code: 'INVALID_DELIVERY_DATE' } }).message, /30 days/);
  assert.match(parseChoiceError(401, null).message, /Log in/);
  assert.match(parseChoiceError(422, { detail: [] }).message, /address/);
  assert.equal(parseChoiceError(500, undefined).refresh, false);
});
