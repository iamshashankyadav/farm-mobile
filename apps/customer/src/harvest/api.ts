import {
  orderClient,
  type HarvestOrderChoice,
  type OrderDetail,
  type PaymentIntent,
} from '@farm/api/src/order';
import { fetchOrder } from '../tracking/api.ts';
import { parseChoiceError, type ChoiceError } from './harvest.ts';

const MAX_PAGES = 5;

/**
 * The harvest order of a reservation, or null while there is none (the quality check has not passed).
 * TODO(M1): the order list has no reservationId filter, so the customer's orders are paged through.
 */
export async function findHarvestOrder(reservationId: string): Promise<OrderDetail | null> {
  let cursor: string | undefined;
  for (let page = 0; page < MAX_PAGES; page++) {
    const { data, response } = await orderClient.GET('/orders', {
      params: { query: { role: 'customer', limit: 100, cursor } },
    });
    if (!data) throw new Error(`Could not load your orders (${response.status})`);
    const found = data.items.find((o) => o.reservationId === reservationId);
    if (found) return fetchOrder(found.id);
    if (!data.nextCursor) return null;
    cursor = data.nextCursor;
  }
  return null;
}

export type ChooseResult = { ok: true; order: OrderDetail } | { ok: false; error: ChoiceError };

export async function chooseOption(body: HarvestOrderChoice): Promise<ChooseResult> {
  try {
    const { data, error, response } = await orderClient.POST('/orders/harvest', { body });
    if (data) return { ok: true, order: data };
    return { ok: false, error: parseChoiceError(response.status, error) };
  } catch {
    return { ok: false, error: { message: 'No connection. Check your network and try again.', refresh: false } };
  }
}

/**
 * Pay the deposit of an accepted reservation (the mock gateway takes it at once).
 * TODO(M1): for the Land tab (farm-mobile #9) to call after the farmer accepts a reservation.
 */
export async function payDeposit(reservationId: string, amount: number): Promise<PaymentIntent | null> {
  const { data } = await orderClient.POST('/orders/payments/intent', {
    body: { amount, kind: 'DEPOSIT', referenceId: reservationId },
  });
  return data ?? null;
}
