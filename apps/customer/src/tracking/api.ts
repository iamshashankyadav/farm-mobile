import { BASE_URL, getAuthToken } from '@farm/api/src/client';
import { orderClient, type OrderDetail, type OrderRating } from '@farm/api/src/order';
import type { Track } from './tracking.ts';

export async function fetchOrder(orderId: string): Promise<OrderDetail> {
  const { data, response } = await orderClient.GET('/orders/{order_id}', {
    params: { path: { order_id: orderId } },
  });
  if (!data) throw new Error(response.status === 404 ? 'Order not found' : 'Could not load the order');
  return data;
}

/** Live position from the logistics service. null while the order is not in a pool yet. */
export async function fetchTrack(orderId: string): Promise<Track | null> {
  const token = getAuthToken();
  const response = await fetch(`${BASE_URL}/logistics/orders/${encodeURIComponent(orderId)}/track`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (response.status === 404) return null;
  if (!response.ok) throw new Error('Could not load the delivery position');
  return (await response.json()) as Track;
}

export type RateResult = { ok: true; rating: OrderRating } | { ok: false; message: string };

const RATE_ERRORS: Record<string, string> = {
  ALREADY_RATED: 'You already rated this order.',
  ORDER_NOT_COMPLETED: 'You can rate the order once it is completed.',
  ORDER_NOT_FOUND: 'Order not found.',
};

export async function rateOrder(orderId: string, score: number, comment: string): Promise<RateResult> {
  try {
    const { data, error } = await orderClient.POST('/orders/{order_id}/rating', {
      params: { path: { order_id: orderId } },
      body: { score, comment: comment.trim() || null },
    });
    if (data) return { ok: true, rating: data };
    const code = (error as { error?: { code?: string } } | undefined)?.error?.code ?? '';
    return { ok: false, message: RATE_ERRORS[code] ?? 'Could not save your rating. Try again.' };
  } catch {
    return { ok: false, message: 'No connection. Check your network and try again.' };
  }
}
