import { useQuery } from '@tanstack/react-query';
import { fetchOrder, fetchTrack } from './api.ts';
import { phaseOf, shouldPoll } from './tracking.ts';

const ORDER_POLL_MS = 15_000;
const TRACK_POLL_MS = 10_000;

/** The order and the live position of its delivery, refreshed while the order is on its way. */
export function useTracking(orderId: string) {
  const order = useQuery({
    queryKey: ['order', orderId],
    queryFn: () => fetchOrder(orderId),
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'COMPLETED' || status === 'CANCELLED' ? false : ORDER_POLL_MS;
    },
  });
  const track = useQuery({
    queryKey: ['track', orderId],
    queryFn: () => fetchTrack(orderId),
    refetchInterval: (query) =>
      shouldPoll(phaseOf(order.data?.status ?? '', query.state.data ?? null)) ? TRACK_POLL_MS : false,
  });
  const phase = order.data ? phaseOf(order.data.status, track.data ?? null) : null;
  return { order, track, phase };
}
